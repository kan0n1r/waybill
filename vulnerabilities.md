# Known vulnerabilities

Waybill is an intentionally insecure training target. This file lists the issues found by an automated security review, with the code location and the recommended fix. Line numbers refer to the code at commit `2b2b68b` and were checked against the source. The exploitation behaviour itself has not been tested.

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
