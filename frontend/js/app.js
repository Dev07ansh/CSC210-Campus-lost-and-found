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

// Claim feedback is transient UI state only. Saved private report details are
// intentionally never copied into browser storage or normal claim views.
const claimMessages = new Map();
const CLAIM_STATUS_LABELS = {
  PENDING_STAFF_REVIEW: 'Awaiting staff review',
  PENDING_VERIFICATION: 'Awaiting staff review',
  APPROVED_FOR_HANDOVER: 'Approved for handover',
  VERIFIED: 'Approved for handover',
  RESOLVED: 'Handover completed',
  REJECTED: 'Not approved',
  CANCELLED: 'Cancelled',
};
const DEFAULT_NOTIFICATION_PREFERENCES = Object.freeze({
  inAppEnabled: true,
  emailEnabled: false,
  matchAlerts: true,
  reportUpdates: true,
  claimUpdates: true,
});
let notificationUnreadCount = 0;
let notificationPreferences = null;
let notificationInboxAvailable = null;
let staffReviewAccess = null;

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
    // Pages are split into individual files. Avoid a browser reusing an older
    // partial after the local frontend has been reorganized or restarted.
    const response = await fetch(path, { cache: 'no-store' });
    if (!response.ok) throw new Error(`Could not load ${path}`);
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
  notificationUnreadCount = 0;
  notificationPreferences = null;
  staffReviewAccess = null;
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
  notificationUnreadCount = 0;
  notificationPreferences = null;
  staffReviewAccess = null;
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
// Notifications
// ============================================================
function notificationPreferenceKey() {
  return `lf_notification_preferences:${String(currentUser?.email || 'anonymous').trim().toLowerCase()}`;
}
function normaliseNotificationPreferences(value = {}) {
  return {
    inAppEnabled: value?.inAppEnabled !== false,
    emailEnabled: value?.emailEnabled === true,
    matchAlerts: value?.matchAlerts !== false,
    reportUpdates: value?.reportUpdates !== false,
    claimUpdates: value?.claimUpdates !== false,
  };
}
function savedNotificationPreferences() {
  try {
    return normaliseNotificationPreferences(JSON.parse(localStorage.getItem(notificationPreferenceKey()) || '{}'));
  } catch (_) {
    return { ...DEFAULT_NOTIFICATION_PREFERENCES };
  }
}
function rememberNotificationPreferences(preferences) {
  notificationPreferences = normaliseNotificationPreferences(preferences);
  try {
    localStorage.setItem(notificationPreferenceKey(), JSON.stringify(notificationPreferences));
  } catch (_) {}
  return notificationPreferences;
}
function notificationEntries(value) {
  if (Array.isArray(value)) return value;
  if (Array.isArray(value?.notifications)) return value.notifications;
  return [];
}
function notificationDateTime(value) {
  if (!value) return 'Recently';
  const date = new Date(value);
  return Number.isNaN(date.valueOf())
    ? String(value)
    : date.toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
}
function notificationTypeLabel(type) {
  const labels = {
    MATCH: 'Possible match',
    POSSIBLE_MATCH: 'Possible match',
    REPORT: 'Report update',
    REPORT_SUBMITTED: 'Report submitted',
    CLAIM: 'Claim update',
    CLAIM_CREATED: 'Claim started',
    CLAIM_PENDING_STAFF_REVIEW: 'Staff review pending',
    CLAIM_REVIEWED: 'Staff review complete',
    CLAIM_APPROVED_FOR_HANDOVER: 'Handover approved',
    CLAIM_VERIFIED: 'Handover approved',
    CLAIM_REJECTED: 'Claim rejected',
    CLAIM_RESOLVED: 'Handover confirmed',
    CLAIM_HANDOVER_COMPLETED: 'Handover confirmed',
    CLAIM_CANCELLED: 'Claim cancelled',
    HANDOVER: 'Handover update',
    SYSTEM: 'Account update',
  };
  return labels[String(type || '').toUpperCase()] || 'Update';
}
function notificationRoute(actionUrl) {
  const route = String(actionUrl || '')
    .trim()
    .replace(/^#\/?/, '')
    .replace(/^\//, '');
  // Notification routes are supplied by our API. Keep them as local hash routes
  // only; an unexpected absolute URL must not cause an automatic redirect.
  return /^[a-z0-9_-]+$/i.test(route) ? route : null;
}
function updateNotificationBadges(count = 0) {
  notificationUnreadCount = Math.max(0, Number(count) || 0);
  const label = notificationUnreadCount > 99 ? '99+' : String(notificationUnreadCount);
  ['notification-unread-badge', 'topbar-notification-badge'].forEach((id) => {
    const badge = document.getElementById(id);
    if (!badge) return;
    badge.hidden = notificationUnreadCount === 0;
    badge.textContent = label;
    badge.setAttribute('aria-label', `${notificationUnreadCount} unread notification${notificationUnreadCount === 1 ? '' : 's'}`);
  });
}
function latestNotificationKey(notification) {
  return String(notification?.id ?? notification?.createdAt ?? '');
}
function notificationSeenKey() {
  return `lf_last_notification:${String(currentUser?.email || 'anonymous').trim().toLowerCase()}`;
}
function showNotificationToast(notification) {
  const region = document.getElementById('notification-toast-region');
  if (!region || !notification) return;
  const toast = document.createElement('article');
  toast.className = 'notification-toast';
  const eyebrow = document.createElement('span');
  eyebrow.className = 'eyebrow';
  eyebrow.textContent = notificationTypeLabel(notification.type);
  const heading = document.createElement('strong');
  heading.textContent = notification.title || 'New update';
  const message = document.createElement('p');
  message.textContent = notification.message || 'Open notifications to view this update.';
  const actions = document.createElement('div');
  actions.className = 'notification-toast-actions';
  const view = document.createElement('button');
  view.className = 'text-btn';
  view.type = 'button';
  view.textContent = notificationRoute(notification.actionUrl) ? 'View update →' : 'Open notifications →';
  view.addEventListener('click', () => openNotification(notification));
  const dismiss = document.createElement('button');
  dismiss.className = 'notification-dismiss';
  dismiss.type = 'button';
  dismiss.setAttribute('aria-label', 'Dismiss notification');
  dismiss.textContent = '×';
  dismiss.addEventListener('click', () => toast.remove());
  actions.append(view, dismiss);
  toast.append(eyebrow, heading, message, actions);
  region.replaceChildren(toast);
  window.setTimeout(() => toast.remove(), 8000);
}
function announceLatestNotification(notifications) {
  const latest = notifications[0];
  if (!latest) return;
  const key = latestNotificationKey(latest);
  if (!key) return;
  try {
    const storageKey = notificationSeenKey();
    const previous = sessionStorage.getItem(storageKey);
    sessionStorage.setItem(storageKey, key);
    // Do not show a stack of old alerts when the page first opens. A toast is
    // reserved for an unread update that arrives while this browser session is open.
    if (previous && previous !== key && !latest.read) showNotificationToast(latest);
  } catch (_) {}
}
async function refreshNotificationBadge({ announce = false } = {}) {
  if (!currentUser?.email || !backendAvailable) {
    updateNotificationBadges(0);
    return [];
  }
  try {
    const notifications = notificationEntries(await api('/notifications'));
    notificationInboxAvailable = true;
    updateNotificationBadges(notifications.filter((notification) => !notification.read).length);
    if (announce) announceLatestNotification(notifications);
    return notifications;
  } catch (_) {
    // A project may briefly run an older backend while the UI has been updated.
    // Keep the rest of the app usable and avoid showing a false unread count.
    notificationInboxAvailable = false;
    updateNotificationBadges(0);
    return [];
  }
}
async function loadNotificationPreferences() {
  const localPreferences = savedNotificationPreferences();
  if (!backendAvailable || !currentUser?.email) {
    return { preferences: localPreferences, savedOnServer: false };
  }
  try {
    const preferences = rememberNotificationPreferences(await api('/notifications/preferences'));
    return { preferences, savedOnServer: true };
  } catch (_) {
    return { preferences: localPreferences, savedOnServer: false };
  }
}
function setNotificationPreferencesMessage(message, type = '') {
  const node = document.getElementById('notification-preferences-status');
  if (!node) return;
  node.textContent = message;
  node.className = `settings-save-state ${type}`.trim();
}
async function saveNotificationPreferences(event) {
  event.preventDefault();
  const preferences = {
    inAppEnabled: document.getElementById('preference-in-app').checked,
    emailEnabled: document.getElementById('preference-email').checked,
    matchAlerts: document.getElementById('preference-matches').checked,
    reportUpdates: document.getElementById('preference-reports').checked,
    claimUpdates: document.getElementById('preference-claims').checked,
  };
  rememberNotificationPreferences(preferences);
  const submit = event.currentTarget.querySelector('button[type="submit"]');
  if (submit) submit.disabled = true;
  if (!backendAvailable || !currentUser?.email) {
    setNotificationPreferencesMessage('Saved in this browser until the live notification service is available.', 'warning');
    if (submit) submit.disabled = false;
    return;
  }
  setNotificationPreferencesMessage('Saving…');
  try {
    const saved = rememberNotificationPreferences(
      await api('/notifications/preferences', { method: 'PUT', body: JSON.stringify(preferences) }),
    );
    ['inAppEnabled', 'emailEnabled', 'matchAlerts', 'reportUpdates', 'claimUpdates'].forEach((key) => {
      const input = document.querySelector(`[name="${key}"]`);
      if (input) input.checked = saved[key];
    });
    setNotificationPreferencesMessage('Saved to your account.', 'success');
  } catch (_) {
    setNotificationPreferencesMessage('Could not reach the notification service. Your choices are saved in this browser.', 'warning');
  } finally {
    if (submit) submit.disabled = false;
  }
}
async function markNotificationRead(notification) {
  if (!notification || notification.read || !backendAvailable) return;
  await api(`/notifications/${encodeURIComponent(notification.id)}/read`, { method: 'POST' });
  notification.read = true;
  updateNotificationBadges(Math.max(0, notificationUnreadCount - 1));
}
async function openNotification(notification) {
  try {
    await markNotificationRead(notification);
  } catch (_) {}
  const route = notificationRoute(notification?.actionUrl);
  if (route) navigate(route);
  else navigate('notifications');
}
function buildNotificationCard(notification) {
  const card = document.createElement('article');
  card.className = `notification-card${notification.read ? '' : ' unread'}`;
  const content = document.createElement('div');
  content.className = 'notification-card-content';
  const meta = document.createElement('div');
  meta.className = 'notification-card-meta';
  const type = document.createElement('span');
  type.className = 'notification-type';
  type.textContent = notificationTypeLabel(notification.type);
  const time = document.createElement('time');
  time.textContent = notificationDateTime(notification.createdAt);
  meta.append(type, time);
  const heading = document.createElement('h3');
  heading.textContent = notification.title || 'Update';
  const message = document.createElement('p');
  message.textContent = notification.message || 'Open this update for more details.';
  content.append(meta, heading, message);
  const actions = document.createElement('div');
  actions.className = 'notification-card-actions';
  if (notificationRoute(notification.actionUrl)) {
    const open = document.createElement('button');
    open.type = 'button';
    open.className = 'btn secondary notification-open';
    open.textContent = 'View update';
    open.addEventListener('click', () => openNotification(notification));
    actions.append(open);
  }
  if (!notification.read) {
    const read = document.createElement('button');
    read.type = 'button';
    read.className = 'text-btn';
    read.textContent = 'Mark as read';
    read.addEventListener('click', async () => {
      read.disabled = true;
      try {
        await markNotificationRead(notification);
        await render();
      } catch (error) {
        read.disabled = false;
        read.textContent = error.message || 'Could not mark as read';
      }
    });
    actions.append(read);
  }
  card.append(content, actions);
  return card;
}
async function renderNotifications(container) {
  container.innerHTML = await loadPartial('pages/notifications.html');
  const target = document.getElementById('notification-list');
  const note = document.getElementById('notification-inbox-note');
  const markAll = document.getElementById('mark-all-notifications');
  const statusNode = document.getElementById('notifications-status');
  if (!backendAvailable) {
    note.textContent = 'Start the backend to receive and manage account updates.';
    statusNode.outerHTML = status();
    target.innerHTML = empty('Notifications need the live API', 'Your inbox will appear here once the backend is running.');
    return;
  }
  try {
    const notifications = notificationEntries(await api('/notifications'));
    notificationInboxAvailable = true;
    const unread = notifications.filter((notification) => !notification.read).length;
    updateNotificationBadges(unread);
    statusNode.outerHTML = status();
    note.textContent = unread
      ? `${unread} unread update${unread === 1 ? '' : 's'}.`
      : 'You are all caught up.';
    markAll.disabled = unread === 0;
    markAll.addEventListener('click', async () => {
      markAll.disabled = true;
      try {
        await api('/notifications/read-all', { method: 'POST' });
        updateNotificationBadges(0);
        await render();
      } catch (error) {
        markAll.disabled = false;
        note.textContent = error.message || 'Could not mark notifications as read.';
      }
    });
    if (!notifications.length) {
      target.innerHTML = empty('No notifications yet', 'Updates about reports, matches, claims, and handovers will appear here.');
      return;
    }
    target.replaceChildren(...notifications.map(buildNotificationCard));
  } catch (error) {
    notificationInboxAvailable = false;
    updateNotificationBadges(0);
    note.textContent = 'The notification service is unavailable on this backend.';
    target.innerHTML = empty('Notifications unavailable', error.message || 'Restart the backend after adding the notification API.');
  }
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
  const role = String(claim.role || '').toUpperCase();
  node.querySelector('.claim-card-role').textContent =
    role === 'FINDER'
      ? 'You reported the found item'
      : role === 'STAFF'
        ? 'Staff review access'
        : 'You reported the lost item';
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
    target.innerHTML = empty('Claims need the live API', 'Start the backend to create a claim and track staff review.');
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
  const { preferences, savedOnServer } = await loadNotificationPreferences();
  document.getElementById('preference-in-app').checked = preferences.inAppEnabled;
  document.getElementById('preference-email').checked = preferences.emailEnabled;
  document.getElementById('preference-matches').checked = preferences.matchAlerts;
  document.getElementById('preference-reports').checked = preferences.reportUpdates;
  document.getElementById('preference-claims').checked = preferences.claimUpdates;
  setNotificationPreferencesMessage(
    savedOnServer
      ? 'Loaded from your account.'
      : backendAvailable
        ? 'Notification service unavailable — showing choices saved in this browser.'
        : 'Start the backend to save choices to your account.',
    savedOnServer ? '' : 'warning',
  );
  document
    .getElementById('notification-preferences-form')
    .addEventListener('submit', saveNotificationPreferences);
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
    claimButton.title = 'This lost report needs all three saved private details before staff can review a claim.';
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
      ? 'Similarity ranks color, location, and keywords. It is a suggestion, not proof of ownership. Staff can review your saved private details only after you create a claim.'
      : 'This older lost report has no complete private details, so staff cannot review it and it cannot be claimed. Similarity is still only a suggestion.';
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
async function cancelClaim(claimId, button) {
  button.disabled = true;
  try {
    await api(`/claims/${claimId}/cancel`, { method: 'POST' });
    rememberClaimMessage(claimId, 'success', 'This claim was cancelled.');
    await render();
  } catch (error) {
    button.disabled = false;
    rememberClaimMessage(claimId, 'error', error.message || 'The claim could not be cancelled.');
    renderClaimFeedback(claimId);
  }
}
function claimReviewMessage(statusValue, role) {
  const finder = role === 'FINDER';
  switch (statusValue) {
    case 'PENDING_STAFF_REVIEW':
    case 'PENDING_VERIFICATION':
      return finder
        ? 'A staff member is reviewing the claimant\'s saved private details. They are not shared with you.'
        : 'Your claim is awaiting staff review.';
    case 'APPROVED_FOR_HANDOVER':
    case 'VERIFIED':
      return 'Staff approved this claim for handover. Follow the campus handover process; staff will record completion.';
    case 'RESOLVED':
      return 'Staff recorded the completed handover. Both reports are resolved.';
    case 'REJECTED':
      return finder
        ? 'Staff could not approve this claim. The claimant\'s private details remain hidden from you.'
        : 'Staff could not approve this claim. Your saved private details remain private.';
    case 'CANCELLED':
      return 'This claim was cancelled before the handover was completed.';
    default:
      return 'Claim status is being updated.';
  }
}
async function renderClaimDetail(container, key) {
  if (!backendAvailable) {
    container.innerHTML = empty('Claims need the live API', 'Start the backend to view claim status.');
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
    role === 'FINDER'
      ? 'You reported the found item.'
      : role === 'STAFF'
        ? 'Use the Staff Review workspace for private review actions.'
        : 'You reported the lost item.';
  document.getElementById('claim-lost-item').textContent = claimItemName(claim.lostItem);
  document.getElementById('claim-found-item').textContent = claimItemName(claim.foundItem);
  const score = Number(claim.matchScore);
  document.getElementById('claim-match-score').textContent = Number.isFinite(score)
    ? `Similarity context: ${Math.round(score * 100)}% (not proof of ownership)`
    : 'Similarity context is unavailable';
  document.getElementById('claim-created-at').textContent = claimDateTime(claim.createdAt);
  document.getElementById('claim-reviewed-at').textContent = claim.reviewedAt
    ? claimDateTime(claim.reviewedAt)
    : 'Not yet reviewed';
  document.getElementById('claim-resolved-at').textContent = claim.resolvedAt
    ? claimDateTime(claim.resolvedAt)
    : 'Not yet completed';

  const stateNote = document.getElementById('claim-state-note');
  const reviewHeading = document.getElementById('claim-review-heading');
  const reviewInstruction = document.getElementById('claim-review-instruction');
  stateNote.textContent = claimReviewMessage(statusValue, role);
  reviewInstruction.textContent = stateNote.textContent;
  if (statusValue === 'APPROVED_FOR_HANDOVER' || statusValue === 'VERIFIED') {
    reviewHeading.textContent = 'Handover approved';
  } else if (statusValue === 'RESOLVED') {
    reviewHeading.textContent = 'Handover completed';
  } else if (statusValue === 'REJECTED') {
    reviewHeading.textContent = 'Staff review complete';
  } else if (statusValue === 'CANCELLED') {
    reviewHeading.textContent = 'Claim cancelled';
  }

  if (claim.canCancel) {
    const cancelSection = document.getElementById('claim-cancel-section');
    cancelSection.hidden = false;
    document.getElementById('claim-cancel-button').addEventListener('click', (event) => cancelClaim(claim.id, event.currentTarget));
  }
  renderClaimFeedback(claim.id);
}

// ============================================================
// Staff-only review queue
// ============================================================
function setStaffReviewNavigation(allowed) {
  const button = document.getElementById('staff-review-nav');
  if (button) button.hidden = !allowed;
}
async function refreshStaffReviewAccess({ force = false } = {}) {
  if (!currentUser?.email || !backendAvailable) {
    setStaffReviewNavigation(false);
    return false;
  }
  if (staffReviewAccess === null || force) {
    try {
      const access = await api('/claims/staff-access');
      staffReviewAccess = access?.staff === true;
    } catch (_) {
      // Treat an unavailable or unauthorised endpoint as no staff access. The
      // server remains the authority for the queue and every staff action.
      staffReviewAccess = false;
    }
  }
  setStaffReviewNavigation(staffReviewAccess);
  return staffReviewAccess;
}
function staffQueueEntries(value) {
  if (Array.isArray(value)) return value;
  if (Array.isArray(value?.claims)) return value.claims;
  if (Array.isArray(value?.queue)) return value.queue;
  return [];
}
function personText(person, fallback) {
  const name = String(person?.name || fallback);
  const email = String(person?.email || '').trim();
  return email ? `${name} (${email})` : name;
}
function addStaffReviewFact(container, label, value) {
  const fact = document.createElement('div');
  const heading = document.createElement('dt');
  const detail = document.createElement('dd');
  heading.textContent = label;
  detail.textContent = value || 'Not provided';
  fact.append(heading, detail);
  container.append(fact);
}
function buildStaffReportPanel(label, item) {
  const panel = document.createElement('section');
  panel.className = 'staff-report-panel';
  const heading = document.createElement('h4');
  heading.textContent = label;
  const title = document.createElement('strong');
  title.textContent = claimItemName(item);
  const summary = document.createElement('p');
  summary.textContent = [item?.category, item?.color, item?.location].filter(Boolean).join(' · ') || 'Report details unavailable';
  panel.append(heading, title, summary);
  const reportDate = item?.dateTime || item?.date;
  if (reportDate) {
    const date = document.createElement('p');
    date.textContent = `Reported item date: ${dateText(reportDate)}`;
    panel.append(date);
  }
  if (item?.description) {
    const description = document.createElement('p');
    description.className = 'staff-report-description';
    description.textContent = item.description;
    panel.append(description);
  }
  if (item?.imageUrl) {
    const image = document.createElement('img');
    image.className = 'staff-report-image';
    image.src = imageSrc(item.imageUrl);
    image.alt = `${label}: ${claimItemName(item)}`;
    panel.append(image);
  }
  return panel;
}
function staffFeedback(container, message, type = 'error') {
  let node = container.querySelector('.staff-review-feedback');
  if (!node) {
    node = document.createElement('p');
    node.className = 'staff-review-feedback';
    container.append(node);
  }
  node.textContent = message;
  node.className = `staff-review-feedback ${type}`;
}
async function submitStaffReview(claimId, decision, note, container) {
  const controls = container.querySelectorAll('button, textarea');
  controls.forEach((control) => { control.disabled = true; });
  try {
    await api(`/claims/${encodeURIComponent(claimId)}/review`, {
      method: 'POST',
      body: JSON.stringify({ decision, staffNote: note.trim() || null }),
    });
    await loadItems();
    await render();
  } catch (error) {
    controls.forEach((control) => { control.disabled = false; });
    staffFeedback(container, error.message || 'The staff review could not be saved.');
  }
}
async function completeStaffHandover(claimId, container) {
  if (!window.confirm('Mark this handover as complete? This resolves both reports.')) return;
  const controls = container.querySelectorAll('button');
  controls.forEach((control) => { control.disabled = true; });
  try {
    await api(`/claims/${encodeURIComponent(claimId)}/complete-handover`, { method: 'POST' });
    await loadItems();
    await render();
  } catch (error) {
    controls.forEach((control) => { control.disabled = false; });
    staffFeedback(container, error.message || 'The handover could not be completed.');
  }
}
function buildStaffReviewCard(entry) {
  const claim = entry?.claim || entry || {};
  const card = document.createElement('article');
  card.className = 'staff-review-card';
  const header = document.createElement('div');
  header.className = 'staff-review-card-header';
  const heading = document.createElement('div');
  const statusNode = document.createElement('span');
  statusNode.className = `claim-status ${claimStatusClass(claim.status)}`;
  statusNode.textContent = claimStatusLabel(claim.status);
  const title = document.createElement('h3');
  title.textContent = `${claimItemName(claim.lostItem)} → ${claimItemName(claim.foundItem)}`;
  const created = document.createElement('p');
  created.className = 'muted';
  created.textContent = `Claim created ${claimDateTime(claim.createdAt)}`;
  heading.append(statusNode, title, created);
  const score = document.createElement('p');
  score.className = 'staff-match-context';
  const matchScore = Number(claim.matchScore);
  score.textContent = Number.isFinite(matchScore)
    ? `Similarity context: ${Math.round(matchScore * 100)}% — not proof of ownership`
    : 'Similarity is only context, not proof of ownership';
  header.append(heading, score);
  card.append(header);

  const people = document.createElement('dl');
  people.className = 'staff-people';
  addStaffReviewFact(people, 'Claimant', personText(entry.claimant || claim.claimant, 'Lost-report owner'));
  addStaffReviewFact(people, 'Finder', personText(entry.finder || claim.finder, 'Found-report reporter'));
  card.append(people);

  const reports = document.createElement('div');
  reports.className = 'staff-report-grid';
  reports.append(buildStaffReportPanel('Lost report', claim.lostItem), buildStaffReportPanel('Found report', claim.foundItem));
  card.append(reports);

  const privateDetails = entry.privateDetails || claim.privateDetails;
  const privateSection = document.createElement('section');
  privateSection.className = 'staff-private-details';
  const privateHeading = document.createElement('h4');
  privateHeading.textContent = 'Private lost-report details';
  const privateNote = document.createElement('p');
  privateNote.textContent = 'Staff-only: compare these saved details with the item. Do not disclose them to the finder or claimant.';
  privateSection.append(privateHeading, privateNote);
  const privateList = document.createElement('dl');
  privateList.className = 'staff-private-list';
  addStaffReviewFact(privateList, 'Interior color or lining', privateDetails?.interiorColorOrLining);
  addStaffReviewFact(privateList, 'Unique marks', privateDetails?.uniqueMarks);
  addStaffReviewFact(privateList, 'Contents', privateDetails?.contents);
  privateSection.append(privateList);
  card.append(privateSection);

  if (claim.staffNote) {
    const previousNote = document.createElement('p');
    previousNote.className = 'staff-note';
    previousNote.textContent = `Previous staff note: ${claim.staffNote}`;
    card.append(previousNote);
  }

  if (claim.canReview) {
    const actions = document.createElement('section');
    actions.className = 'staff-review-actions';
    const actionHeading = document.createElement('h4');
    actionHeading.textContent = 'Record staff decision';
    const noteLabel = document.createElement('label');
    noteLabel.textContent = 'Staff note (optional; not shown to claim participants)';
    const note = document.createElement('textarea');
    note.rows = 3;
    note.maxLength = 500;
    note.autocomplete = 'off';
    note.placeholder = 'Record only the minimum necessary review note.';
    noteLabel.append(note);
    const buttons = document.createElement('div');
    buttons.className = 'staff-review-buttons';
    const approve = document.createElement('button');
    approve.type = 'button';
    approve.className = 'btn primary';
    approve.textContent = 'Approve for handover';
    approve.addEventListener('click', () => submitStaffReview(claim.id, 'APPROVE', note.value, actions));
    const reject = document.createElement('button');
    reject.type = 'button';
    reject.className = 'btn secondary staff-reject-button';
    reject.textContent = 'Reject claim';
    reject.addEventListener('click', () => submitStaffReview(claim.id, 'REJECT', note.value, actions));
    buttons.append(approve, reject);
    actions.append(actionHeading, noteLabel, buttons);
    card.append(actions);
  } else if (claim.canCompleteHandover) {
    const complete = document.createElement('section');
    complete.className = 'staff-review-actions';
    const heading = document.createElement('h4');
    heading.textContent = 'Complete the handover';
    const note = document.createElement('p');
    note.textContent = 'Use this only after the item has been handed over in person according to the campus process.';
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'btn primary';
    button.textContent = 'Mark handover complete';
    button.addEventListener('click', () => completeStaffHandover(claim.id, complete));
    complete.append(heading, note, button);
    card.append(complete);
  }
  return card;
}
async function renderStaffReview(container) {
  container.innerHTML = await loadPartial('pages/staff-review.html');
  const target = document.getElementById('staff-review-list');
  const note = document.getElementById('staff-review-note');
  const statusNode = document.getElementById('staff-review-status');
  if (!backendAvailable) {
    statusNode.outerHTML = status();
    note.textContent = 'Start the live backend before accessing staff review.';
    target.innerHTML = empty('Staff review needs the live API', 'The review queue is unavailable while the backend is offline.');
    return;
  }
  const allowed = await refreshStaffReviewAccess({ force: true });
  if (!allowed) {
    statusNode.outerHTML = status();
    note.textContent = 'This restricted workspace is available only to authorised staff accounts.';
    target.innerHTML = empty('Staff access required', 'Sign in with an authorised staff account to review claims.');
    return;
  }
  try {
    const queue = staffQueueEntries(await api('/claims/review-queue'));
    note.textContent = queue.length
      ? `${queue.length} claim${queue.length === 1 ? '' : 's'} awaiting a staff action.`
      : 'There are no claims awaiting a staff action.';
    statusNode.outerHTML = status();
    if (!queue.length) {
      target.innerHTML = empty('Review queue is clear', 'New claims will appear here for private staff review.');
      return;
    }
    target.replaceChildren(...queue.map(buildStaffReviewCard));
  } catch (error) {
    staffReviewAccess = false;
    setStaffReviewNavigation(false);
    note.textContent = 'The staff review service is unavailable.';
    target.innerHTML = empty('Staff review unavailable', error.message || 'The review queue could not be loaded.');
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
    claims: 'My Claims',
    'staff-review': 'Staff Review',
    notifications: 'Notifications',
    settings: 'Settings',
    help: 'Help & safe handover guide',
  };
  const isItemDetail = activePage.startsWith('item-');
  const isClaimDetail = activePage.startsWith('claim-');
  wireShell(
    isItemDetail ? 'Item Details' : isClaimDetail ? 'Claim Details' : titles[activePage] || 'Dashboard',
  );
  refreshNotificationBadge({ announce: true });
  if (activePage !== 'staff-review') void refreshStaffReviewAccess();

  const content = document.getElementById('page-content');
  if (activePage === 'dashboard') await renderDashboard(content);
  else if (activePage === 'browse') await renderBrowse(content);
  else if (activePage === 'report-lost') await renderReportForm(content, 'LOST');
  else if (activePage === 'report-found') await renderReportForm(content, 'FOUND');
  else if (activePage === 'reports') await renderReports(content);
  else if (activePage === 'claims') await renderClaims(content);
  else if (activePage === 'staff-review') await renderStaffReview(content);
  else if (activePage === 'notifications') await renderNotifications(content);
  else if (activePage === 'settings') await renderSettings(content);
  else if (activePage === 'help') content.innerHTML = await loadPartial('pages/help.html');
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
window.setInterval(() => {
  if (currentUser && backendAvailable && document.visibilityState !== 'hidden') {
    refreshNotificationBadge({ announce: true });
  }
}, 45000);
