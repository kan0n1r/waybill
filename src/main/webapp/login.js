const $ = (id) => document.getElementById(id);
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => '&#' + c.charCodeAt(0) + ';');
const pretty = (s) => esc(String(s).replace(/_/g, ' '));
const post = (u, b) => fetch(u, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(b) })
  .then(async (r) => ({ ok: r.ok, data: await r.json().catch(() => ({})) }));

fetch('/api/auth/bootstrap').catch(() => {});

$('login').addEventListener('submit', async (e) => {
  e.preventDefault();
  $('err').textContent = '';
  const { ok, data } = await post('/api/auth/login', { email: $('email').value.trim(), password: $('password').value, remember: $('remember').checked });
  if (ok) location.href = '/app.html'; else $('err').textContent = data.error || 'Could not sign in';
});

const show = (w) => { $('login').hidden = w !== 'login'; $('forgot').hidden = w !== 'forgot'; };
$('to-forgot').onclick = () => show('forgot');
$('to-login').onclick = () => show('login');

let sent = false;
$('forgot').addEventListener('submit', async (e) => {
  e.preventDefault();
  $('f-err').textContent = '';
  if (!sent) {
    const { data } = await post('/api/auth/forgot', { email: $('f-email').value.trim() });
    sent = true; $('reset-fields').hidden = false; $('forgot-help').textContent = data.message; $('f-submit').textContent = 'Update password';
    return;
  }
  const { ok, data } = await post('/api/auth/reset', { email: $('f-email').value.trim(), token: $('f-token').value.trim(), newPassword: $('f-pass').value });
  if (ok) { show('login'); $('email').value = $('f-email').value; $('password').focus(); } else $('f-err').textContent = data.error || 'Could not reset password';
});

$('track-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  $('track-detail').innerHTML = '';
  const q = $('track-q').value.trim();
  const list = $('track-results');
  if (!q) { list.innerHTML = ''; return; }
  const r = await fetch('/api/track?q=' + encodeURIComponent(q));
  const rows = r.ok ? await r.json() : [];
  list.innerHTML = rows.length
    ? rows.map((p) => `<li><button type="button" data-code="${esc(p.tracking_code)}"><span>${esc(p.tracking_code)} &middot; ${esc(p.city)}</span><span>${pretty(p.status)}</span></button></li>`).join('')
    : '<li class="muted">No parcels found.</li>';
  list.querySelectorAll('button').forEach((b) => (b.onclick = async () => {
    const d = await fetch('/api/track/' + encodeURIComponent(b.dataset.code)).then((x) => x.json());
    $('track-detail').innerHTML = d.events
      ? `<ol class="timeline">${[...d.events].reverse().map((ev) => `<li><b>${pretty(ev.status)}</b><span>${esc(ev.location)}</span><small>${esc(String(ev.created_at).slice(0, 16).replace('T', ' '))}</small></li>`).join('')}</ol>`
      : `<p>${esc(d.error || '')}</p>`;
  }));
});
