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

// Claim feedback and the one-time handover code deliberately live only in
// memory. They are never written to localStorage or included in a URL.
const claimMessages = new Map();
const handoverCodes = new Map();
const CLAIM_STATUS_LABELS = {
  PENDING_VERIFICATION: 'Awaiting verification',
  VERIFIED: 'Verified — arrange handover',
  RESOLVED: 'Resolved',
  REJECTED: 'Rejected',
  CANCELLED: 'Cancelled',
};

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
    verificationAvailable: Boolean(item.verificationAvailable),
  };
}

// ============================================================
// Backend API layer
// ============================================================
async function api(path, config = {}) {
  const headers = config.body instanceof FormData ? {} : { 'Content-Type': 'application/json' };
  // Temporary local-demo identity bridge. The server applies ownership rules for
  // this identity, but an email header is not real authentication; replace it
  // with a server-authenticated session/JWT before deployment.
  const identity = currentUser?.email ? { 'X-User-Email': currentUser.email } : {};
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...config,
    headers: { ...headers, ...identity, ...(config.headers || {}) },
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
function claimStatusLabel(value) {
  return CLAIM_STATUS_LABELS[String(value || '').toUpperCase()] || 'Claim update unavailable';
}
function claimStatusClass(value) {
  return String(value || 'unknown').toLowerCase().replace(/[^a-z0-9]+/g, '-');
}
function claimItemName(item) {
  return item?.itemName || item?.title || 'Item unavailable';
}
function claimDateTime(value) {
  if (!value) return 'Not available';
  const date = new Date(value);
  return Number.isNaN(date.valueOf())
    ? value
    : date.toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
}
function rememberClaimMessage(id, type, text) {
  claimMessages.set(String(id), { type, text });
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
  const verification = document.getElementById('verification-details');
  verification.hidden = !lost;
  verification.querySelectorAll('input').forEach((input) => { input.required = lost; });
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
  if (type === 'LOST') {
    item.interiorColorOrLining = data.get('interiorColorOrLining');
    item.uniqueMarks = data.get('uniqueMarks');
    item.contents = data.get('contents');
  }
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
async function buildClaimCard(claim) {
  const template = await loadTemplate('components/claim-card.html');
  const node = template.content.cloneNode(true);
  const statusNode = node.querySelector('.claim-card-status');
  statusNode.textContent = claimStatusLabel(claim.status);
  statusNode.classList.add(claimStatusClass(claim.status));
  node.querySelector('.claim-card-lost').textContent = claimItemName(claim.lostItem);
  node.querySelector('.claim-card-found').textContent = claimItemName(claim.foundItem);
  node.querySelector('.claim-card-role').textContent =
    String(claim.role || '').toUpperCase() === 'FINDER' ? 'You reported the found item' : 'You reported the lost item';
  node.querySelector('.claim-card-created').textContent = `Created ${claimDateTime(claim.createdAt)}`;
  const score = Number(claim.matchScore);
  node.querySelector('.claim-card-score').textContent = Number.isFinite(score)
    ? `Similarity context: ${Math.round(score * 100)}%`
    : 'Similarity suggestion only';
  node.querySelector('.claim-card-view').addEventListener('click', () => navigate(`claim-${claim.id}`));
  return node;
}
async function renderClaims(container) {
  container.innerHTML = await loadPartial('pages/claims.html');
  const target = document.getElementById('claims-list');
  if (!backendAvailable) {
    target.innerHTML = empty('Claims need the live API', 'Start the backend to create, verify, or resolve a claim.');
    return;
  }
  try {
    const claims = await api('/claims/me');
    document.getElementById('claims-status').outerHTML = status();
    if (!claims.length) {
      target.innerHTML = empty('No claims yet', 'Choose a suggested found-item match from one of your lost reports.');
      return;
    }
    target.replaceChildren(...(await Promise.all(claims.map(buildClaimCard))));
  } catch (error) {
    target.innerHTML = empty('Claims unavailable', error.message || 'The live API could not load your claims.');
  }
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
    loadMatches(item.serverId, item.verificationAvailable);
  }
}
function setMatchClaimFeedback(type, text) {
  const result = document.getElementById('match-claim-result');
  if (!result) return;
  result.textContent = text;
  result.className = `claim-result ${type}`;
}
async function createClaim(lostItemId, foundItemId, button) {
  if (!backendAvailable) {
    setMatchClaimFeedback('error', 'Claims require the live API.');
    return;
  }
  button.disabled = true;
  const originalLabel = button.textContent;
  button.textContent = 'Creating claim…';
  try {
    // The backend validates report ownership, item state, duplicates, and the
    // candidate pairing. The browser only selects a suggested candidate.
    const claim = await api('/claims', {
      method: 'POST',
      body: JSON.stringify({ lostItemId, foundItemId }),
    });
    navigate(`claim-${claim.id}`);
  } catch (error) {
    button.disabled = false;
    button.textContent = originalLabel;
    setMatchClaimFeedback('error', error.message || 'The claim could not be created.');
  }
}
async function buildMatchRow(m, lostItemId, verificationAvailable) {
  const template = await loadTemplate('components/match-row.html');
  const node = template.content.cloneNode(true);
  const candidate = m.item || m.foundItem || {};
  node.querySelector('.match-title').textContent = claimItemName(candidate);
  node.querySelector('.match-meta').textContent = `${candidate.category || 'Other'} · ${candidate.location || 'Location unavailable'}`;
  const score = Number(m.score);
  node.querySelector('.match-score').textContent = Number.isFinite(score)
    ? `Similarity score: ${Math.round(score * 100)}%`
    : 'Similarity suggestion';
  const claimButton = node.querySelector('.match-claim');
  const foundItemId = candidate.id ?? m.foundItemId;
  if (!backendAvailable || !currentUser?.email || foundItemId == null) {
    claimButton.disabled = true;
    claimButton.textContent = backendAvailable ? 'Sign in to claim' : 'Live API required';
  } else if (!verificationAvailable) {
    // The server enforces this too. This client-side explanation avoids offering
    // an unusable claim for a legacy report that has no complete secret record.
    claimButton.disabled = true;
    claimButton.textContent = 'Private details required';
    claimButton.title = 'This lost report needs all three private verification details before it can be claimed.';
  } else if (m.canCreateClaim === false) {
    // This flag is calculated by the server. Keep the control visible so an
    // ineligible viewer understands why there is no action to take.
    claimButton.disabled = true;
    claimButton.textContent = 'Not eligible to claim';
    claimButton.title =
      'Only the owner of an active lost report can claim a found item, and a finder cannot claim their own report.';
  } else {
    claimButton.addEventListener('click', () => createClaim(lostItemId, foundItemId, claimButton));
  }
  return node;
}
async function loadMatches(id, verificationAvailable = true) {
  const target = document.getElementById('matches');
  if (!target) return;
  try {
    const matches = (await api(`/lost-items/${id}/matches?limit=5`)).filter((m) => m.score > 0);
    document.getElementById('match-note').textContent = verificationAvailable
      ? 'Similarity ranks color, location, and keywords. It is a suggestion, not proof of ownership.'
      : 'This older lost report has no complete private verification details, so it cannot be claimed. Similarity is still only a suggestion.';
    if (matches.length) {
      target.replaceChildren(...(await Promise.all(matches.map((match) => buildMatchRow(match, id, verificationAvailable)))));
    } else {
      target.textContent = 'No meaningful candidates yet.';
    }
  } catch (error) {
    document.getElementById('match-note').textContent =
      'Could not retrieve matches from the backend.';
  }
}
function renderClaimFeedback(id) {
  const feedback = claimMessages.get(String(id));
  const node = document.getElementById('claim-feedback');
  if (!node || !feedback) return;
  node.textContent = feedback.text;
  node.className = `claim-result ${feedback.type}`;
}
async function copyHandoverCode(id) {
  const code = handoverCodes.get(String(id));
  if (!code) return;
  try {
    await navigator.clipboard.writeText(code);
    rememberClaimMessage(id, 'success', 'One-time handover code copied. Share it only in person.');
  } catch (_) {
    rememberClaimMessage(id, 'error', 'Copy is unavailable. Select the one-time code and copy it manually.');
  }
  renderClaimFeedback(id);
}
async function verifyClaim(event, claimId) {
  event.preventDefault();
  const form = new FormData(event.target);
  const submit = event.target.querySelector('button[type="submit"]');
  if (submit) submit.disabled = true;
  try {
    const verification = await api(`/claims/${claimId}/verify`, {
      method: 'POST',
      body: JSON.stringify({
        interiorColorOrLining: form.get('interiorColorOrLining'),
        uniqueMarks: form.get('uniqueMarks'),
        contents: form.get('contents'),
      }),
    });
    if (verification.handoverCode) handoverCodes.set(String(claimId), verification.handoverCode);
    if (verification.passed) {
      rememberClaimMessage(
        claimId,
        'success',
        'Ownership verified. Arrange a handover; the finder must confirm the one-time code.',
      );
    } else if (verification.rejected) {
      rememberClaimMessage(claimId, 'error', 'Verification failed and this claim was rejected.');
    } else {
      const remaining = verification.attemptsRemaining ?? verification.claim?.verificationAttemptsRemaining;
      rememberClaimMessage(
        claimId,
        'error',
        Number.isFinite(Number(remaining))
          ? `Verification did not pass. ${remaining} attempt${Number(remaining) === 1 ? '' : 's'} remaining.`
          : 'Verification did not pass. Please try again.',
      );
    }
    await render();
  } catch (error) {
    rememberClaimMessage(claimId, 'error', error.message || 'Verification could not be completed.');
    if (submit) submit.disabled = false;
    renderClaimFeedback(claimId);
  }
}
async function resolveClaim(event, claimId) {
  event.preventDefault();
  const form = new FormData(event.target);
  const submit = event.target.querySelector('button[type="submit"]');
  if (submit) submit.disabled = true;
  try {
    await api(`/claims/${claimId}/resolve`, {
      method: 'POST',
      body: JSON.stringify({ handoverCode: form.get('handoverCode') }),
    });
    handoverCodes.delete(String(claimId));
    rememberClaimMessage(claimId, 'success', 'Handover confirmed. Both reports are now resolved.');
    await loadItems();
    await render();
  } catch (error) {
    rememberClaimMessage(claimId, 'error', error.message || 'The handover could not be confirmed.');
    if (submit) submit.disabled = false;
    renderClaimFeedback(claimId);
  }
}
async function cancelClaim(claimId, button) {
  button.disabled = true;
  try {
    await api(`/claims/${claimId}/cancel`, { method: 'POST' });
    handoverCodes.delete(String(claimId));
    rememberClaimMessage(claimId, 'success', 'This claim was cancelled.');
    await render();
  } catch (error) {
    button.disabled = false;
    rememberClaimMessage(claimId, 'error', error.message || 'The claim could not be cancelled.');
    renderClaimFeedback(claimId);
  }
}
async function renderClaimDetail(container, key) {
  if (!backendAvailable) {
    container.innerHTML = empty('Claims need the live API', 'Start the backend to view or act on a claim.');
    return;
  }
  container.innerHTML = await loadPartial('pages/claim-detail.html');
  const claimId = decodeURIComponent(key);
  let claim;
  try {
    const claims = await api('/claims/me');
    claim = claims.find((entry) => String(entry.id) === String(claimId));
  } catch (error) {
    container.innerHTML = empty('Claim unavailable', error.message || 'The live API could not load this claim.');
    return;
  }
  if (!claim) {
    container.innerHTML = empty('Claim not found', 'It may not belong to the signed-in user or may no longer be available.');
    return;
  }

  const statusValue = String(claim.status || '').toUpperCase();
  const role = String(claim.role || '').toUpperCase();
  const statusNode = document.getElementById('claim-detail-status');
  statusNode.textContent = claimStatusLabel(statusValue);
  statusNode.classList.add(claimStatusClass(statusValue));
  document.getElementById('claim-detail-title').textContent = `${claimItemName(claim.lostItem)} → ${claimItemName(claim.foundItem)}`;
  document.getElementById('claim-detail-role').textContent =
    role === 'FINDER' ? 'You reported the found item.' : 'You reported the lost item.';
  document.getElementById('claim-lost-item').textContent = claimItemName(claim.lostItem);
  document.getElementById('claim-found-item').textContent = claimItemName(claim.foundItem);
  const score = Number(claim.matchScore);
  document.getElementById('claim-match-score').textContent = Number.isFinite(score)
    ? `Similarity context: ${Math.round(score * 100)}% (not proof of ownership)`
    : 'Similarity context is unavailable';
  document.getElementById('claim-created-at').textContent = claimDateTime(claim.createdAt);
  document.getElementById('claim-verified-at').textContent = claim.verifiedAt ? claimDateTime(claim.verifiedAt) : 'Not yet verified';
  document.getElementById('claim-resolved-at').textContent = claim.resolvedAt ? claimDateTime(claim.resolvedAt) : 'Not yet resolved';

  const stateNote = document.getElementById('claim-state-note');
  const verificationSection = document.getElementById('claim-verification-section');
  const handoverSection = document.getElementById('claim-handover-section');
  const cancelSection = document.getElementById('claim-cancel-section');
  if (claim.canVerify) {
    verificationSection.hidden = false;
    const remaining = claim.verificationAttemptsRemaining;
    document.getElementById('claim-attempt-note').textContent = Number.isFinite(Number(remaining))
      ? `${remaining} verification attempt${Number(remaining) === 1 ? '' : 's'} remaining.`
      : 'Answer all three private questions.';
    document
      .getElementById('claim-verification-form')
      .addEventListener('submit', (event) => verifyClaim(event, claim.id));
  } else if (statusValue === 'PENDING_VERIFICATION') {
    stateNote.textContent =
      role === 'FINDER'
        ? 'The lost-item owner must complete private verification before a handover can be arranged.'
        : 'This claim is awaiting a server-authorized verification step.';
  }

  if (statusValue === 'VERIFIED') {
    handoverSection.hidden = false;
    const code = handoverCodes.get(String(claim.id));
    const codeCard = document.getElementById('claim-code-card');
    if (code) {
      codeCard.hidden = false;
      document.getElementById('claim-handover-code').textContent = code;
      document.getElementById('copy-handover-code').addEventListener('click', () => copyHandoverCode(claim.id));
    } else if (role === 'CLAIMANT') {
      document.getElementById('claim-code-note').textContent =
        'The one-time code is shown immediately after verification and is not saved in local storage or a URL.';
    }
    if (claim.canResolve) {
      document.getElementById('claim-resolve-form').hidden = false;
      document.getElementById('claim-resolve-form').addEventListener('submit', (event) => resolveClaim(event, claim.id));
      document.getElementById('claim-handover-instruction').textContent =
        'At the handover, enter the one-time code supplied by the verified owner.';
    } else if (role === 'FINDER') {
      document.getElementById('claim-handover-instruction').textContent =
        'Only the server-authorized finder can confirm this handover.';
    } else {
      document.getElementById('claim-handover-instruction').textContent =
        'Meet the finder and share the one-time code only in person.';
    }
  } else if (statusValue === 'RESOLVED') {
    stateNote.textContent = 'Handover confirmed. Both reports have been resolved.';
  } else if (statusValue === 'REJECTED') {
    stateNote.textContent = 'This claim was rejected after verification did not pass.';
  } else if (statusValue === 'CANCELLED') {
    stateNote.textContent = 'This claim was cancelled before handover.';
  }

  if (claim.canCancel) {
    cancelSection.hidden = false;
    document.getElementById('claim-cancel-button').addEventListener('click', (event) => cancelClaim(claim.id, event.currentTarget));
  }
  renderClaimFeedback(claim.id);
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
    claims: 'My Claims',
    settings: 'Settings',
  };
  const isItemDetail = activePage.startsWith('item-');
  const isClaimDetail = activePage.startsWith('claim-');
  wireShell(
    isItemDetail ? 'Item Details' : isClaimDetail ? 'Claim Details' : titles[activePage] || 'Dashboard',
  );

  const content = document.getElementById('page-content');
  if (activePage === 'dashboard') await renderDashboard(content);
  else if (activePage === 'browse') await renderBrowse(content);
  else if (activePage === 'report-lost') await renderReportForm(content, 'LOST');
  else if (activePage === 'report-found') await renderReportForm(content, 'FOUND');
  else if (activePage === 'reports') await renderReports(content);
  else if (activePage === 'claims') await renderClaims(content);
  else if (activePage === 'settings') await renderSettings(content);
  else if (isItemDetail) await renderDetail(content, activePage.slice(5));
  else if (isClaimDetail) await renderClaimDetail(content, activePage.slice(6));
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
