const $ = (s) => document.querySelector(s);
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => '&#' + c.charCodeAt(0) + ';');
const view = $('#view');
let me = null;

const ICONS = {
  dashboard: '<path d="M3 11l9-8 9 8v9a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z"/>',
  shipments: '<path d="M12 3l8 4.500v9L12 21l-8-4.500v-9z"/><path d="M4 7.500l8 4.500 8-4.500M12 12v9"/>',
  wallet: '<rect x="3" y="6" width="18" height="14" rx="2"/><path d="M3 10h18M16 15h2"/>',
  depot: '<path d="M3 21V9l9-5 9 5v12M8 21v-6h8v6"/>',
  users: '<circle cx="9" cy="8" r="3.500"/><path d="M2.500 20c0-3.600 2.900-6 6.500-6s6.500 2.400 6.500 6"/><circle cx="17.500" cy="9" r="2.500"/><path d="M17 14c2.700 0 4.500 1.800 4.500 4.500"/>',
  settings: '<circle cx="12" cy="12" r="3"/><path d="M12 2v3M12 19v3M2 12h3M19 12h3M5 5l2 2M17 17l2 2M19 5l-2 2M7 17l-2 2"/>',
  out: '<path d="M9 4H5a1 1 0 0 0-1 1v14a1 1 0 0 0 1 1h4M16 8l4 4-4 4M20 12H9"/>',
};
const icon = (n) => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${ICONS[n]}</svg>`;

async function api(url, opts) {
  const r = await fetch(url, opts);
  const data = await r.json().catch(() => ({}));
  if (r.status === 401) { location.href = '/'; throw new Error('Not signed in'); }
  if (!r.ok) throw new Error(data.error || r.statusText);
  return data;
}
const send = (url, body, method = 'POST') => api(url, { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });

let toastTimer;
function toast(msg, isErr) {
  const t = $('#toast'); t.textContent = msg; t.className = 'show' + (isErr ? ' err' : '');
  clearTimeout(toastTimer); toastTimer = setTimeout(() => (t.className = ''), 3400);
}

const initials = (n) => String(n).split(/\s+/).map((w) => w[0]).slice(0, 2).join('').toUpperCase();
const hue = (s) => [...String(s)].reduce((a, c) => (a * 31 + c.charCodeAt(0)) % 360, 7);
const slug = (email) => String(email || '').split('@')[0].toLowerCase().replace(/[^a-z0-9_-]/g, '');
const localImg = (src) => (/^\/img\/[\w./-]+$/.test(String(src || '')) && !String(src).includes('..') ? src : '');
const avatar = (name, email, cls = '', src = '') => {
  const img = localImg(src) || (slug(email) ? `/img/avatars/${slug(email)}.svg` : '');
  return `<span class="avatar ${cls}" style="background:hsl(${hue(name)} 42% 38%)" aria-hidden="true">${esc(initials(name))}${img ? `<img src="${esc(img)}" alt="">` : ''}</span>`;
};
document.addEventListener('error', (e) => { if (e.target.tagName === 'IMG' && e.target.parentElement.classList.contains('avatar')) e.target.remove(); }, true);
const pretty = (s) => esc(String(s).replace(/_/g, ' '));
const chip = (s) => `<span class="chip ${esc(s)}">${pretty(s)}</span>`;
const money = (n) => 'EUR ' + Number(n).toFixed(2);
const day = (s) => esc(String(s).slice(0, 10));
const isStaff = () => me.role === 'courier' || me.role === 'admin';

const SAMPLE_MANIFEST = `<?xml version="1.0" encoding="UTF-8"?>
<manifest depot="Hamburg">
  <shipment code="WB-100001">
    <status>in_transit</status>
    <location>Hannover</location>
    <note>Scanned at sorting hub</note>
  </shipment>
</manifest>`;

const pages = {
  async dashboard() {
    const [wallet, list] = await Promise.all([api('/api/wallet'), api('/api/shipments')]);
    const active = list.filter((s) => s.status !== 'delivered');
    return `
      <div class="page-head"><div><h1>Hello, ${esc(me.fullName.split(' ')[0])}</h1><p>${isStaff() ? 'All parcels in the network' : 'Your parcels and wallet'}</p></div>
        <a class="btn" href="#/shipments">Send a parcel</a></div>
      <div class="grid two">
        <section class="panel"><div class="stat">${esc(active.length)}<small>${isStaff() ? 'parcels on the move' : 'parcels on the way'}</small></div></section>
        <section class="panel"><div class="stat">${money(wallet.balance)}<small>wallet balance</small></div></section>
      </div>
      <section class="panel" style="margin-top:18px"><h2>${isStaff() ? 'Latest shipments' : 'Your shipments'}</h2>
        ${list.length ? `<ul class="list">${list.slice(0, 6).map((s) => `<li><div class="grow"><a href="/shipments/view?id=${esc(s.id)}">${esc(s.tracking_code)} &middot; ${esc(s.recipient_name)}</a><small>${esc(s.recipient_city)} &middot; ${day(s.created_at)}</small></div>${chip(s.status)}</li>`).join('')}</ul>`
          : '<p class="empty">No shipments yet. Send your first parcel.</p>'}</section>`;
  },

  async shipments() {
    const list = await api('/api/shipments');
    return `
      <div class="page-head"><div><h1>Shipments</h1><p>${isStaff() ? 'Every shipment in the network' : 'Parcels you have sent'}</p></div></div>
      <div class="grid main-side">
        <section class="panel"><h2>${isStaff() ? 'All shipments' : 'My shipments'}</h2>
          ${list.length ? `<ul class="list">${list.map((s) => `<li><div class="grow"><a href="/shipments/view?id=${esc(s.id)}">${esc(s.tracking_code)} &middot; ${esc(s.recipient_name)}</a><small>${esc(s.recipient_city)} &middot; ${esc(s.service)} &middot; ${money(s.price)}</small></div>${chip(s.status)}</li>`).join('')}</ul>`
            : '<p class="empty">Nothing here yet.</p>'}</section>
        <section class="panel"><h2>Send a parcel</h2>
          <form id="new-shipment">
            <label for="rn">Recipient name</label><input id="rn" required maxlength="120">
            <label for="rc">City</label><input id="rc" required maxlength="80">
            <label for="ra">Address</label><input id="ra" required maxlength="200">
            <div class="row"><div><label for="rw">Weight (kg)</label><input id="rw" type="number" step="0.1" required></div>
              <div><label for="rs">Service</label><select id="rs"><option value="economy">Economy</option><option value="standard" selected>Standard</option><option value="express">Express</option></select></div></div>
            <div class="quote" id="quote" hidden></div>
            <button class="btn" style="margin-top:14px;width:100%">Pay from wallet and ship</button>
          </form></section>
      </div>`;
  },

  async depot() {
    if (!isStaff()) return '<p class="empty">The depot desk is available to couriers and admins.</p>';
    return `
      <div class="page-head"><div><h1>Depot desk</h1><p>Update parcels and exchange manifests</p></div></div>
      <div class="grid two">
        <section class="panel"><h2>Scan update</h2>
          <form id="status-form"><label for="sid">Shipment id</label><input id="sid" type="number" required>
          <label for="sst">Status</label><select id="sst">${['picked_up', 'in_transit', 'out_for_delivery', 'delivered', 'exception'].map((s) => `<option value="${s}">${s.replace(/_/g, ' ')}</option>`).join('')}</select>
          <label for="sloc">Location</label><input id="sloc" maxlength="80">
          <button class="btn" style="margin-top:14px">Save update</button></form></section>
        <section class="panel"><h2>Export manifest</h2>
          <form id="export-form"><label for="ecity">City (optional)</label><input id="ecity">
          <label for="ename">Archive name</label><input id="ename" value="manifest">
          <button class="btn" style="margin-top:14px">Create archive</button></form><pre class="out" id="export-out" hidden></pre></section>
      </div>
      <section class="panel" style="margin-top:18px"><h2>Import manifest (XML)</h2>
        <form id="import-form"><label class="sr-only" for="xml">Manifest XML</label><textarea class="code" id="xml" spellcheck="false">${esc(SAMPLE_MANIFEST)}</textarea>
        <button class="btn" style="margin-top:12px">Import manifest</button></form><pre class="out" id="import-out" hidden></pre></section>`;
  },

  async wallet() {
    const w = await api('/api/wallet');
    return `
      <div class="page-head"><div><h1>Wallet</h1><p>Prepaid balance used for shipping</p></div></div>
      <div class="grid two"><section class="panel"><div class="stat">${money(w.balance)}<small>available</small></div></section>
        <section class="panel"><h2>Top up</h2><form id="topup"><label for="amt">Amount (EUR, 1-500)</label><input id="amt" type="number" min="1" max="500" step="1" required>
          <button class="btn" style="margin-top:14px">Add funds</button></form></section></div>`;
  },

  async users() {
    if (me.role !== 'admin') return '<p class="empty">Only admins can manage users.</p>';
    const users = await api('/api/admin/users');
    return `
      <div class="page-head"><div><h1>Users</h1><p>Manage roles</p></div></div>
      <section class="panel"><div class="table-wrap"><table><thead><tr><th>Name</th><th>City</th><th>Wallet</th><th>Role</th></tr></thead><tbody>${users.map((u) =>
        `<tr><td><div class="row" style="flex-wrap:nowrap">${avatar(u.full_name, u.email, '', u.avatar_url)}<div><b>${esc(u.full_name)}</b><br><small class="muted">${esc(u.email)}</small></div></div></td><td>${esc(u.city)}</td><td>${money(u.wallet_balance)}</td>
        <td><select data-role="${esc(u.id)}" aria-label="Role for ${esc(u.full_name)}" style="width:auto">${['customer', 'courier', 'admin'].map((r) => `<option ${r === u.role ? 'selected' : ''}>${r}</option>`).join('')}</select></td></tr>`).join('')}</tbody></table></div></section>`;
  },

  async settings() {
    return `
      <div class="page-head"><div><h1>Settings</h1><p>Your profile</p></div></div>
      <div class="grid two">
        <section class="panel"><h2>Profile</h2>
          <div class="row" style="flex-wrap:nowrap;margin-bottom:12px">${avatar(me.fullName, me.email, 'lg', me.avatarUrl)}<div><b>${esc(me.fullName)}</b><br><span class="muted">${esc(me.email)}</span></div></div>
          <p class="muted" style="margin:4px 0">Home city: ${esc(me.city)}</p><p class="muted" style="margin:4px 0">Role: ${esc(me.role)}</p>
          <form id="photo"><label for="photo-url">Import profile photo from URL</label><input id="photo-url" type="url" placeholder="https://">
          <button class="btn ghost" style="margin-top:12px">Import photo</button></form><pre class="out" id="photo-out" hidden></pre></section>
        <section class="panel"><h2>Appearance</h2><label for="theme">Theme</label>
          <select id="theme"><option value="light">Light</option><option value="dark">Dark</option></select></section>
      </div>`;
  },
};

const handlers = {
  shipments() {
    const quote = async () => {
      const w = $('#rw').value; const box = $('#quote');
      if (!w) { box.hidden = true; return; }
      try { const q = await send('/api/quote', { weightKg: Number(w), service: $('#rs').value }); box.hidden = false; box.textContent = 'Estimated price: ' + money(q.price); }
      catch { box.hidden = true; }
    };
    $('#rw').oninput = quote; $('#rs').onchange = quote;
    $('#new-shipment').onsubmit = async (e) => {
      e.preventDefault();
      try {
        const r = await send('/api/shipments', { recipientName: $('#rn').value, recipientCity: $('#rc').value, recipientAddress: $('#ra').value, weightKg: Number($('#rw').value), service: $('#rs').value });
        toast('Shipped as ' + r.trackingCode); route();
      } catch (err) { toast(err.message, true); }
    };
  },
  depot() {
    const f = $('#status-form'); if (!f) return;
    f.onsubmit = async (e) => { e.preventDefault(); try { await send(`/api/shipments/${encodeURIComponent($('#sid').value)}/status`, { status: $('#sst').value, location: $('#sloc').value }); toast('Parcel updated'); } catch (err) { toast(err.message, true); } };
    $('#export-form').onsubmit = async (e) => {
      e.preventDefault();
      const qs = new URLSearchParams({ name: $('#ename').value }); if ($('#ecity').value) qs.set('city', $('#ecity').value);
      try { const r = await api('/api/depot/manifest/export?' + qs); const o = $('#export-out'); o.hidden = false; o.textContent = `${r.archive} (${r.rows} rows)\n${r.details}`; toast('Archive created'); } catch (err) { toast(err.message, true); }
    };
    $('#import-form').onsubmit = async (e) => {
      e.preventDefault();
      try {
        const r = await api('/api/depot/manifest/import', { method: 'POST', headers: { 'Content-Type': 'application/xml' }, body: $('#xml').value });
        const o = $('#import-out'); o.hidden = false;
        o.textContent = r.shipments.map((s) => `${s.code}: ${s.status} @ ${s.location} - ${s.note}${s.applied ? '' : ' (not applied)'}`).join('\n') || 'No shipments in the manifest';
        toast('Manifest imported');
      } catch (err) { toast(err.message, true); }
    };
  },
  wallet() { $('#topup').onsubmit = async (e) => { e.preventDefault(); try { await send('/api/wallet/topup', { amount: Number($('#amt').value) }); toast('Funds added'); route(); } catch (err) { toast(err.message, true); } }; },
  users() {
    view.querySelectorAll('[data-role]').forEach((s) => (s.onchange = async () => { try { await send(`/api/admin/users/${encodeURIComponent(s.dataset.role)}/role`, { role: s.value }); toast('Role updated'); } catch (err) { toast(err.message, true); } }));
  },
  settings() {
    const t = $('#theme'); t.value = document.documentElement.dataset.theme === 'dark' ? 'dark' : 'light';
    t.onchange = () => { applyTheme(t.value); try { localStorage.setItem('wb-theme', t.value); } catch { /* ignore */ } };
    $('#photo').onsubmit = async (e) => {
      e.preventDefault();
      try { const r = await send('/api/profile/photo', { url: $('#photo-url').value }); const o = $('#photo-out'); o.hidden = false; o.textContent = `Imported (${r.status}, ${r.size} bytes)\n${r.preview}`; toast('Photo imported'); } catch (err) { toast(err.message, true); }
    };
  },
};

function applyTheme(t) { document.documentElement.dataset.theme = t === 'dark' ? 'dark' : 'light'; }

function renderShell() {
  const items = [['dashboard', 'Home'], ['shipments', 'Shipments'], ['wallet', 'Wallet']];
  const staff = [];
  if (isStaff()) staff.push(['depot', 'Depot desk']);
  if (me.role === 'admin') staff.push(['users', 'Users']);
  const link = ([k, n]) => `<a href="#/${k}" data-k="${k}">${icon(k)}<span>${n}</span></a>`;
  $('#nav').innerHTML = items.map(link).join('') + (staff.length ? '<div class="nav-group">Operations</div>' + staff.map(link).join('') : '') + '<div style="flex:1"></div>' + link(['settings', 'Settings']);
  $('#me').innerHTML = `${avatar(me.fullName, me.email, '', me.avatarUrl)}<div><b>${esc(me.fullName)}</b><small>${esc(me.role)}</small></div><button id="logout" title="Sign out" aria-label="Sign out">${icon('out')}</button>`;
  $('#logout').onclick = async () => { await fetch('/api/auth/logout', { method: 'POST' }); location.href = '/'; };
}

async function route() {
  const key = (location.hash.replace('#/', '') || 'dashboard').split('?')[0];
  const name = pages[key] ? key : 'dashboard';
  document.querySelectorAll('#nav a').forEach((a) => (a.dataset.k === name ? a.setAttribute('aria-current', 'page') : a.removeAttribute('aria-current')));
  try {
    view.innerHTML = await pages[name]();
    document.title = 'Waybill - ' + name[0].toUpperCase() + name.slice(1);
    if (handlers[name]) handlers[name]();
  } catch (err) { view.innerHTML = `<p class="empty">${esc(err.message)}</p>`; }
}

(async function init() {
  try { applyTheme(localStorage.getItem('wb-theme')); } catch { /* ignore */ }
  me = await api('/api/auth/me');
  renderShell();
  window.addEventListener('hashchange', route);
  route();
})();
