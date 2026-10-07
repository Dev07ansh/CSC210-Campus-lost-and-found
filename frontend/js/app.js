// ============================================================
// Configuration
// ============================================================
const app = document.getElementById('app');
const GOOGLE_CLIENT_ID = '882163697172-d3d0e5hbog9sp23dcd1cmnvjgjd7o7of.apps.googleusercontent.com';
const API_ORIGIN = `http://${location.hostname || 'localhost'}:8080`;
const API_BASE_URL = `${API_ORIGIN}/api`;
const CATEGORIES = [
  'Electronics',
  'Wallet / Purse',
  'Keys',
  'Bottle',
  'Books / Stationery',
  'Clothing',
  'Cards / ID',
  'Accessories',
  'Other',
];
const COLORS = [
  'Black',
  'White',
  'Grey',
  'Blue',
  'Red',
  'Green',
  'Yellow',
  'Brown',
  'Silver',
  'Gold',
  'Multicolor',
  'Other',
];
const LOCATIONS = [
  'School of Arts and Sciences(SAS)',
  'SAS — Big Fan Area',
  'School of Engineering and Applied Sciences (SEAS)',
  'SEAS — Airport Area',
  'Heritage School',
  'AMSOM Heritage Building',
  'AMSOM BK Building',
  'University Centre (UC)',
  'Student Lounge',
  'Silent Study Area',
  'Multipurpose Hall (MPH)',
  'Futsal Court',
  'Cafeteria',
  'Other / Not listed',
];

// ============================================================
// Demo data (shown when the backend is unavailable)
// ============================================================
const demoItems = [
  {
    id: 'demo-found-1',
    type: 'FOUND',
    title: 'Black Wallet',
    category: 'Wallet / Purse',
    color: 'Black',
    location: 'Silent Study Area',
    date: '26 Aug 2026',
    status: 'Active',
    description: 'Black leather wallet found near the reading area.',
  },
  {
    id: 'demo-lost-2',
    type: 'LOST',
    title: 'Silver Water Bottle',
    category: 'Bottle',
    color: 'Silver',
    location: 'Futsal Court',
    date: '25 Aug 2026',
    status: 'Active',
    description: 'Metallic silver bottle with a small sticker near the base.',
  },
  {
    id: 'demo-found-3',
    type: 'FOUND',
    title: 'Blue Umbrella',
    category: 'Accessories',
    color: 'Blue',
    location: 'SAS',
    date: '24 Aug 2026',
    status: 'Active',
    description: 'Foldable blue umbrella found near the entrance.',
  },
];

// ============================================================
// App state
// ============================================================
let currentUser = JSON.parse(localStorage.getItem('lf_user') || 'null');
let activePage = location.hash.slice(1) || (currentUser ? 'dashboard' : 'login');
let apiItems = [],
  backendAvailable = false,
  googleReady = false,
  debounce;

// ============================================================
// HTML partial & component loading
//
// Pages/partials/components used to be template-literal strings
// built inside this file. They now live as plain .html files;
// this section is the only new piece of infrastructure needed to
// fetch and reuse them.
// ============================================================
const partialCache = new Map();
async function loadPartial(path) {
  if (!partialCache.has(path)) {
    const response = await fetch(path);
    partialCache.set(path, await response.text());
  }
  return partialCache.get(path);
}
const templateCache = new Map();
async function loadTemplate(path) {
  if (!templateCache.has(path)) {
    const html = await loadPartial(path);
    const wrapper = document.createElement('div');
    wrapper.innerHTML = html;
    templateCache.set(path, wrapper.querySelector('template'));
  }
  return templateCache.get(path);
}

// ============================================================
// Small helpers
// ============================================================
const esc = (value = '') =>
  String(value).replace(
    /[&<>"']/g,
    (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' })[c],
  );
const items = () => (backendAvailable ? apiItems : demoItems);
const saveUser = (user) => {
  currentUser = user;
  localStorage.setItem('lf_user', JSON.stringify(user));
};
const imageSrc = (imageUrl) =>
  imageUrl && imageUrl.startsWith('/') ? `${API_ORIGIN}${imageUrl}` : imageUrl;
function populateSelect(id, values) {
  document.getElementById(id).append(...values.map((value) => new Option(value, value)));
}
function empty(title, message) {
  return `<div class="empty"><h3>${esc(title)}</h3><p>${esc(message)}</p></div>`;
}

// ============================================================
// Navigation & formatting
// ============================================================
function navigate(page) {
  activePage = page;
  location.hash = page;
  render();
}
function logout() {
  window.google?.accounts?.id?.disableAutoSelect();
  localStorage.removeItem('lf_user');
  currentUser = null;
  navigate('login');
}
function dateText(value) {
  const date = new Date(value);
  return Number.isNaN(date.valueOf())
    ? value || 'Date unavailable'
    : date.toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' });
}
function displayItem(item, type) {
  return {
    id: `${type.toLowerCase()}-${item.id}`,
    serverId: item.id,
    type,
    title: item.itemName || 'Untitled item',
    category: item.category || 'Other',
    color: item.color || 'Not specified',
    location: item.location || 'Not specified',
    date: dateText(item.dateTime),
    status: item.status || 'Active',
    description: item.description || 'No description provided.',
    imageUrl: item.imageUrl || '',
  };
}

// ============================================================
// Backend API layer
// ============================================================
async function api(path, config = {}) {
  const headers = config.body instanceof FormData ? {} : { 'Content-Type': 'application/json' };
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...config,
    headers: { ...headers, ...(config.headers || {}) },
  });
  if (!response.ok) {
    let message = `API error ${response.status}`;
    try {
      const error = await response.json();
      message = error.detail || error.message || message;
    } catch (_) {}
    throw new Error(message);
  }
  return response.status === 204 ? null : response.json();
}
async function loadItems() {
  try {
    const [lost, found] = await Promise.all([api('/lost-items'), api('/found-items')]);
    apiItems = [
      ...lost.map((i) => displayItem(i, 'LOST')),
      ...found.map((i) => displayItem(i, 'FOUND')),
    ];
    backendAvailable = true;
  } catch (error) {
    backendAvailable = false;
  }
}
function status() {
  return backendAvailable
    ? '<span class="api-status online">● Live API</span>'
    : '<span class="api-status demo">● Demo data</span>';
}

// ============================================================
// Shared UI chrome (shell wiring, item cards)
// ============================================================
function wireShell(title) {
  document.getElementById('page-title').textContent = title;
  document.getElementById('avatar-btn').textContent = (currentUser?.name || 'S')[0].toUpperCase();
  document.querySelectorAll('#sidebar-nav [data-page]').forEach((btn) => {
    btn.classList.toggle('active', btn.dataset.page === activePage);
  });
}
async function buildCard(item) {
  const template = await loadTemplate('components/item-card.html');
  const node = template.content.cloneNode(true);
  const img = node.querySelector('.card-img');
  const placeholder = node.querySelector('.card-placeholder');
  if (item.imageUrl) {
    img.src = imageSrc(item.imageUrl);
    img.alt = item.title;
    img.hidden = false;
    placeholder.remove();
  } else {
    img.remove();
    placeholder.textContent = item.type === 'LOST' ? '?' : '✓';
  }
  const typeBadge = node.querySelector('.card-type');
  typeBadge.textContent = item.type;
  typeBadge.classList.add(item.type.toLowerCase());
  node.querySelector('.card-date').textContent = item.date;
  node.querySelector('.card-title').textContent = item.title;
  node.querySelector('.card-meta').textContent = `${item.category} · ${item.location}`;
  node.querySelector('.card-status').textContent = item.status;
  node
    .querySelector('.card-view')
    .addEventListener('click', () => navigate(`item-${encodeURIComponent(item.id)}`));
  return node;
}

// ============================================================
// Page views (dashboard, browse, report forms, detail, etc.)
// ============================================================
async function renderDashboard(container) {
  container.innerHTML = await loadPartial('pages/dashboard.html');
  const all = items();
  document.getElementById('stat-active').textContent = all.filter((i) => i.status === 'Active').length;
  document.getElementById('stat-found').textContent = all.filter((i) => i.type === 'FOUND').length;
  document.getElementById('stat-lost').textContent = all.filter((i) => i.type === 'LOST').length;
  document.getElementById('dashboard-status').outerHTML = status();
  const grid = document.getElementById('dashboard-grid');
  const recent = all.slice(0, 3);
  grid.innerHTML = recent.length ? '' : empty('No reports yet', 'Be the first to add an item.');
  if (recent.length) grid.replaceChildren(...(await Promise.all(recent.map(buildCard))));
}
function searchHome() {
  localStorage.setItem('lf_query', document.getElementById('home-search').value);
  navigate('browse');
}
async function renderBrowse(container) {
  container.innerHTML = await loadPartial('pages/browse.html');
  document.getElementById('browse-status').outerHTML = status();
  populateSelect('category', CATEGORIES);
  populateSelect('color', COLORS);
  populateSelect('location', LOCATIONS);
  document.getElementById('query').value = localStorage.getItem('lf_query') || '';
  await filterItems();
}
function queueFilter() {
  clearTimeout(debounce);
  debounce = setTimeout(filterItems, 200);
}
function same(a, b) {
  return a.serverId === b.serverId && a.type === b.type;
}
async function filterItems() {
  const results = document.getElementById('results');
  if (!results) return;
  const f = {
    q: (document.getElementById('query').value || '').trim().toLowerCase(),
    type: document.getElementById('type').value,
    category: document.getElementById('category').value,
    color: document.getElementById('color').value,
    location: document.getElementById('location').value,
    start: document.getElementById('start').value,
    end: document.getElementById('end').value,
  };
  let visible = items(),
    found = visible.filter((i) => i.type === 'FOUND'),
    algorithms = [];
  try {
    if (backendAvailable && f.type !== 'LOST') {
      if (f.q) {
        const matches = (
          await api(`/found-items/search?query=${encodeURIComponent(f.q)}&prefix=true`)
        ).map((i) => displayItem(i, 'FOUND'));
        found = found.filter((i) => matches.some((m) => same(i, m)));
        algorithms.push('normalized trie prefix search');
      }
      if (f.category) {
        const matches = (await api(`/found-items/category/${encodeURIComponent(f.category)}`)).map(
          (i) => displayItem(i, 'FOUND'),
        );
        found = found.filter((i) => matches.some((m) => same(i, m)));
        algorithms.push('hash-table category lookup');
      }
      if (f.start && f.end) {
        const matches = (
          await api(
            `/found-items/date-range?start=${encodeURIComponent(f.start + 'T00:00:00')}&end=${encodeURIComponent(f.end + 'T23:59:59')}`,
          )
        ).map((i) => displayItem(i, 'FOUND'));
        found = found.filter((i) => matches.some((m) => same(i, m)));
        algorithms.push('binary date-range search');
      }
      visible = [...visible.filter((i) => i.type !== 'FOUND'), ...found];
    }
  } catch (error) {
    backendAvailable = false;
    visible = demoItems;
  }
  const filtered = visible.filter((i) => {
    const text = `${i.title} ${i.category} ${i.location} ${i.description}`.toLowerCase();
    return (
      (!f.q || text.includes(f.q)) &&
      (!f.type || i.type === f.type) &&
      (!f.category || i.category === f.category) &&
      (!f.color || i.color === f.color) &&
      (!f.location || i.location === f.location)
    );
  });
  document.getElementById('algorithm').textContent = algorithms.length
    ? `Using: ${algorithms.join(' · ')}.`
    : backendAvailable
      ? 'Use a keyword, found-item category, or full date range to use a backend index.'
      : 'Backend unavailable — using local demo filters.';
  if (filtered.length) {
    results.replaceChildren(...(await Promise.all(filtered.map(buildCard))));
  } else {
    results.innerHTML = empty('No matching reports', 'Try a broader keyword or remove a filter.');
  }
}
async function renderReportForm(container, type) {
  const lost = type === 'LOST';
  container.innerHTML = await loadPartial('pages/report-form.html');
  document.getElementById('report-eyebrow').textContent = lost ? 'Lost item' : 'Found item';
  document.getElementById('report-heading').textContent = `Report ${lost ? 'a lost' : 'a found'} item`;
  populateSelect('category', CATEGORIES);
  populateSelect('color', COLORS);
  populateSelect('location', LOCATIONS);
  document.querySelector('#report-form [name="image"]').required = !lost;
  document.getElementById('photo-flag').textContent = lost ? '(optional)' : '*';
  document.getElementById('report-form').addEventListener('submit', (event) => submitReport(event, type));
}
function previewImage(input) {
  const preview = document.getElementById('image-preview'),
    file = input.files?.[0];
  if (!preview) return;
  if (!file) {
    preview.textContent = 'No image selected';
    return;
  }
  preview.innerHTML = `<img src="${URL.createObjectURL(file)}" alt="Selected image preview">`;
}
async function submitReport(event, type) {
  event.preventDefault();
  const data = new FormData(event.target),
    item = {
      itemName: data.get('title'),
      category: data.get('category'),
      color: data.get('color'),
      location: data.get('location'),
      description: data.get('description'),
      dateTime: `${data.get('date')}T${data.get('time') || '12:00'}:00`,
      status: 'Active',
    };
  const payload = new FormData();
  payload.append('item', new Blob([JSON.stringify(item)], { type: 'application/json' }));
  const image = data.get('image');
  if (image instanceof File && image.size > 0) payload.append('image', image);
  try {
    await api(type === 'LOST' ? '/lost-items' : '/found-items', { method: 'POST', body: payload });
    await loadItems();
    alert('Report submitted successfully.');
    navigate('reports');
  } catch (error) {
    alert(error.message || 'Report could not be submitted.');
  }
}
async function renderReports(container) {
  container.innerHTML = await loadPartial('pages/reports.html');
  document.getElementById('reports-status').outerHTML = status();
  const grid = document.getElementById('reports-grid');
  const all = items();
  grid.innerHTML = all.length ? '' : empty('No reports yet', 'Create a report to add it here.');
  if (all.length) grid.replaceChildren(...(await Promise.all(all.map(buildCard))));
}
async function renderSettings(container) {
  container.innerHTML = await loadPartial('pages/settings.html');
  document.getElementById('settings-name').value = currentUser?.name || 'Student';
  document.getElementById('settings-email').value = currentUser?.email || 'student@example.com';
}
async function renderDetail(container, key) {
  const item = items().find((i) => String(i.id) === decodeURIComponent(key));
  if (!item) {
    container.innerHTML = empty('Item not found', 'Return to Browse & Search.');
    return;
  }
  container.innerHTML = await loadPartial('pages/detail.html');
  const imageBox = document.getElementById('details-image');
  if (item.imageUrl) {
    const img = document.createElement('img');
    img.src = imageSrc(item.imageUrl);
    img.alt = item.title;
    imageBox.replaceChildren(img);
  } else {
    imageBox.textContent = item.type === 'LOST' ? '?' : '✓';
  }
  const badge = document.getElementById('details-badge');
  badge.textContent = item.type;
  badge.classList.add(item.type.toLowerCase());
  document.getElementById('details-title').textContent = item.title;
  document.getElementById('details-description').textContent = item.description;
  document.getElementById('details-category').textContent = item.category;
  document.getElementById('details-color').textContent = item.color;
  document.getElementById('details-location').textContent = item.location;
  document.getElementById('details-date').textContent = item.date;
  const matchSection = document.getElementById('match-section');
  if (item.type === 'LOST' && item.serverId) {
    matchSection.hidden = false;
    loadMatches(item.serverId);
  }
}
async function buildMatchRow(m) {
  const template = await loadTemplate('components/match-row.html');
  const node = template.content.cloneNode(true);
  node.querySelector('.match-title').textContent = m.item.itemName;
  node.querySelector('.match-meta').textContent = `${m.item.category} · ${m.item.location}`;
  node.querySelector('.match-score').textContent = `${Math.round(m.score * 100)}% match`;
  return node;
}
async function loadMatches(id) {
  const target = document.getElementById('matches');
  if (!target) return;
  try {
    const matches = (await api(`/lost-items/${id}/matches?limit=5`)).filter((m) => m.score > 0);
    document.getElementById('match-note').textContent =
      'Scores use the supplied color, location, keyword scorer and MaxHeap ranking.';
    if (matches.length) {
      target.replaceChildren(...(await Promise.all(matches.map(buildMatchRow))));
    } else {
      target.textContent = 'No meaningful candidates yet.';
    }
  } catch (error) {
    document.getElementById('match-note').textContent =
      'Could not retrieve matches from the backend.';
  }
}

// ============================================================
// Authentication (email/password + Google Identity Services)
// ============================================================
function setAuthMessage(message) {
  const node = document.getElementById('auth-message');
  if (node) node.textContent = message;
}
function handleAuth(event, mode) {
  event.preventDefault();
  const data = new FormData(event.target);
  if (mode === 'signup' && data.get('password') !== data.get('confirm'))
    return setAuthMessage('Passwords do not match.');
  saveUser({ name: data.get('name') || data.get('email').split('@')[0], email: data.get('email') });
  navigate('dashboard');
}
async function googleCredential(response) {
  setAuthMessage('Verifying your Google account…');
  try {
    saveUser(
      await api('/auth/google', {
        method: 'POST',
        body: JSON.stringify({ credential: response.credential }),
      }),
    );
    navigate('dashboard');
  } catch (error) {
    setAuthMessage(
      'Google sign-in could not be verified. Ensure the backend runs and this origin is authorised in Google Cloud.',
    );
  }
}
function setupGoogle() {
  const mount = document.getElementById('google-button');
  if (!mount || !window.google?.accounts?.id) return;
  if (!googleReady) {
    window.google.accounts.id.initialize({
      client_id: GOOGLE_CLIENT_ID,
      callback: googleCredential,
      auto_select: false,
    });
    googleReady = true;
  }
  window.google.accounts.id.renderButton(mount, {
    theme: 'outline',
    size: 'large',
    width: 360,
    text: 'signin_with',
  });
}

// ============================================================
// Router / bootstrap
// ============================================================
// navigate() and the hashchange listener below can both trigger a
// render() for the same navigation. That was harmless when render()
// built one HTML string synchronously, but now that it awaits
// fetch() for each partial, two overlapping calls could interleave
// and write into each other's (already-replaced) DOM. Queuing calls
// so each render fully finishes before the next one starts keeps
// that impossible, matching the original's effectively-sequential
// behavior.
let renderChain = Promise.resolve();
function render() {
  renderChain = renderChain.then(renderNow, renderNow);
  return renderChain;
}
async function renderNow() {
  if (!currentUser && !['login', 'signup'].includes(activePage)) activePage = 'login';

  if (activePage === 'login' || activePage === 'signup') {
    app.innerHTML = await loadPartial(`pages/${activePage}.html`);
    if (activePage === 'login') setupGoogle();
    return;
  }

  app.innerHTML = await loadPartial('partials/shell.html');
  const titles = {
    dashboard: 'Dashboard',
    browse: 'Browse & Search',
    'report-lost': 'Report Lost',
    'report-found': 'Report Found',
    reports: 'My Reports',
    settings: 'Settings',
  };
  const isDetail = activePage.startsWith('item-');
  wireShell(isDetail ? 'Item Details' : titles[activePage] || 'Dashboard');

  const content = document.getElementById('page-content');
  if (activePage === 'dashboard') await renderDashboard(content);
  else if (activePage === 'browse') await renderBrowse(content);
  else if (activePage === 'report-lost') await renderReportForm(content, 'LOST');
  else if (activePage === 'report-found') await renderReportForm(content, 'FOUND');
  else if (activePage === 'reports') await renderReports(content);
  else if (activePage === 'settings') await renderSettings(content);
  else if (isDetail) await renderDetail(content, activePage.slice(5));
  else await renderDashboard(content);
}
window.onGoogleLibraryLoad = setupGoogle;
window.addEventListener('hashchange', () => {
  activePage = location.hash.slice(1) || (currentUser ? 'dashboard' : 'login');
  render();
});
render();
loadItems().then(() => {
  if (currentUser) render();
});
