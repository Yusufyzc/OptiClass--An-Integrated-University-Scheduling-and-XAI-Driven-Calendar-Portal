/* OptiClass Admin – Single Page Application */

// ══════════════════════════════════════════════════════════
// CONSTANTS & STATE
// ══════════════════════════════════════════════════════════
const DAYS = ['Mon','Tue','Wed','Thu','Fri'];
const TIME_SLOTS = ['08:00 AM','09:00 AM','10:00 AM','11:00 AM','12:00 PM',
                    '01:00 PM','02:00 PM','03:00 PM','04:00 PM','05:00 PM'];

const DEPT_COLORS = [
  '#4f46e5','#0891b2','#16a34a','#d97706','#dc2626','#9333ea',
  '#0e7490','#15803d','#b45309','#c2410c','#7c3aed','#db2777'
];
const deptColorMap = {};
let deptColorIdx = 0;
function deptColor(dept) {
  if (!deptColorMap[dept]) deptColorMap[dept] = DEPT_COLORS[deptColorIdx++ % DEPT_COLORS.length];
  return deptColorMap[dept];
}

let logsEventSource = null;

// ══════════════════════════════════════════════════════════
// PASSWORD / USERNAME UTILITIES  (mirrors Kotlin Utils.kt)
// ══════════════════════════════════════════════════════════
function generatePassword() {
  const upper   = 'ABCDEFGHJKLMNPQRSTUVWXYZ';
  const lower   = 'abcdefghjkmnpqrstuvwxyz';
  const digits  = '23456789';
  const special = '!@#$%&*';
  const all     = upper + lower + digits + special;
  const pick = s => s[Math.floor(Math.random() * s.length)];
  const chars = [pick(upper), pick(lower), pick(digits), pick(special),
                 ...Array.from({length: 4}, () => pick(all))];
  for (let i = chars.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [chars[i], chars[j]] = [chars[j], chars[i]];
  }
  return chars.join('');
}

const ACADEMIC_TITLES_SET = new Set([
  'prof.','dr.','doç.','yrd.','öğr.','gör.','arş.',
  'prof','dr','doç','yrd','öğr','gör','arş'
]);

function normalizeTurkish(str) {
  return str.replace(/[şŞ]/g,'s').replace(/[çÇ]/g,'c').replace(/[üÜ]/g,'u')
            .replace(/[ğĞ]/g,'g').replace(/[ıI]/g,'i').replace(/İ/g,'i')
            .replace(/[öÖ]/g,'o');
}

function generateUsernameFromName(fullName, existingUsernames = []) {
  const parts = fullName.split(' ')
    .map(p => normalizeTurkish(p).toLowerCase())
    .filter(p => !ACADEMIC_TITLES_SET.has(p))
    .map(p => p.replace(/[^a-z0-9]/g, ''))
    .filter(p => p.length > 0);
  let base = parts.length >= 2 ? `${parts[0]}_${parts[parts.length-1]}`
           : parts.length === 1 ? parts[0] : 'user';
  if (!existingUsernames.includes(base)) return base;
  let i = 2;
  while (existingUsernames.includes(`${base}_${i}`)) i++;
  return `${base}_${i}`;
}

function usernameFromEmail(email, fullName, existingUsernames = []) {
  const fromEmail = (email || '').split('@')[0].trim();
  return fromEmail || generateUsernameFromName(fullName, existingUsernames);
}

// Returns null if valid, error string if invalid (mirrors Kotlin validatePassword)
function validatePassword(pw) {
  if (!pw || pw.length < 8)      return 'Password must be at least 8 characters';
  if (!/[A-Z]/.test(pw))         return 'Password must contain at least one uppercase letter';
  if (!/[a-z]/.test(pw))         return 'Password must contain at least one lowercase letter';
  if (!/\d/.test(pw))            return 'Password must contain at least one number';
  if (!/[^a-zA-Z0-9]/.test(pw)) return 'Password must contain at least one special character';
  return null;
}

function userInitials(fullName) {
  const parts = (fullName || '').trim().split(' ').filter(p => p.length > 0);
  if (parts.length >= 2) return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return '?';
}

function avatarBgColor(username) {
  const palette = ['#1976d2','#388e3c','#d32f2f','#7b1fa2','#f57c00','#0097a7','#c2185b','#5d4037'];
  let h = 0;
  for (let i = 0; i < (username || '').length; i++) h = (h * 31 + username.charCodeAt(i)) >>> 0;
  return palette[h % palette.length];
}

async function processAvatarFile(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onerror = reject;
    reader.onload = e => {
      const img = new Image();
      img.onerror = reject;
      img.onload = () => {
        const maxDim = 256;
        const ratio = Math.min(maxDim / img.width, maxDim / img.height, 1);
        const canvas = document.createElement('canvas');
        canvas.width  = Math.round(img.width  * ratio);
        canvas.height = Math.round(img.height * ratio);
        canvas.getContext('2d').drawImage(img, 0, 0, canvas.width, canvas.height);
        resolve(canvas.toDataURL('image/jpeg', 0.7));
      };
      img.src = e.target.result;
    };
    reader.readAsDataURL(file);
  });
}

const state = {
  token: null, username: null, role: null, department: null,
  // calendar
  calInstructor: null,
  calDraft: {},           // {day_slot: CourseDto | null}
  calSelectedCourse: null,
  calMode: null,          // 'lecture' | 'lab' | null
  calAvailability: {},    // {day: [slots]}
  calOtherSchedules: [],  // [{instructorUsername, slots}]
  calClassrooms: [],
  // xai
  xaiResults: null,
  xaiExpandedIdx: -1,
  // weekly
  weeklyResults: null,
  weeklyExpandedIdx: -1,
  _weeklySchedules: [], _weeklyCourses: [], _weeklyInstructorMap: {}, _weeklyClassroomMap: {},
  // logs
  logFilter: '',
  // users pagination
  userFilter: '',
  courseFilter: '',
};

// ══════════════════════════════════════════════════════════
// UI UTILITIES
// ══════════════════════════════════════════════════════════
function setContent(html) {
  document.getElementById('content').innerHTML = html;
}

function showLoading() {
  setContent('<div class="loading-spinner"><div class="spinner"></div></div>');
}

function setTopbarTitle(title) {
  document.getElementById('topbar-title').textContent = title;
}

function setTopbarRight(html) {
  document.getElementById('topbar-right').innerHTML = html;
}

function toast(msg, type = 'success') {
  const icons = { success: '✓', error: '✕', warning: '⚠', info: 'ℹ' };
  const el = document.createElement('div');
  el.className = `toast ${type}`;
  el.innerHTML = `<span class="toast-icon">${icons[type] || '✓'}</span><span>${msg}</span>`;
  const container = document.getElementById('toast-container');
  container.appendChild(el);
  setTimeout(() => { el.style.opacity = '0'; el.style.transition = 'opacity .3s';
                     setTimeout(() => el.remove(), 300); }, 3000);
}

let _modalResolve = null;
function openModal(title, bodyHTML, footerHTML = '', size = '') {
  document.getElementById('modal-title').textContent = title;
  document.getElementById('modal-body').innerHTML = bodyHTML;
  document.getElementById('modal-footer').innerHTML = footerHTML;
  const box = document.getElementById('modal-box');
  box.className = size ? `modal-${size}` : '';
  document.getElementById('modal-overlay').style.display = 'flex';
}
function closeModal() {
  document.getElementById('modal-overlay').style.display = 'none';
  document.getElementById('modal-body').innerHTML = '';
  document.getElementById('modal-footer').innerHTML = '';
  if (_modalResolve) { _modalResolve(false); _modalResolve = null; }
}
function confirmModal(title, message, dangerBtn = 'Delete') {
  return new Promise(resolve => {
    _modalResolve = resolve;
    openModal(title,
      `<p class="confirm-text">${message}</p>`,
      `<button class="btn btn-secondary" onclick="closeModal()">Cancel</button>
       <button class="btn btn-danger" onclick="_modalResolve(true);_modalResolve=null;closeModal()">${dangerBtn}</button>`
    );
  });
}

function esc(str) {
  if (!str) return '';
  return String(str).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}

function formatDate(iso) {
  if (!iso) return '-';
  try { return new Date(iso).toLocaleString(); } catch { return iso; }
}

function roleBadge(role) {
  const colorMap = { ADMIN: 'badge-warning', INSTRUCTOR: 'badge-info', SUPER_ADMIN: 'badge-danger' };
  const labelMap = { ADMIN: 'Admin', INSTRUCTOR: 'Instructor', SUPER_ADMIN: 'System Admin' };
  return `<span class="badge ${colorMap[role] || 'badge-gray'}">${labelMap[role] || role}</span>`;
}

function scoreClass(s) { return s >= 0.85 ? 'high' : s >= 0.60 ? 'mid' : 'low'; }
function scorePct(s) { return (s * 100).toFixed(0) + '%'; }

// ══════════════════════════════════════════════════════════
// ROUTER
// ══════════════════════════════════════════════════════════
const PAGE_RENDERERS = {
  dashboard: renderDashboard,
  users: renderUsers,
  classrooms: renderClassrooms,
  courses: renderCourses,
  calendar: renderCalendar,
  availability: renderAvailability,
  weekly: renderWeekly,
  chatbot: renderChatbot,
  settings: renderSettings,
  logs: renderLogs,
};

const PAGE_TITLES = {
  dashboard: 'Dashboard', users: 'User Management', classrooms: 'Classrooms',
  courses: 'Data Import / Courses', calendar: 'Update Calendar',
  availability: 'Instructor Availability', weekly: 'Weekly Schedule',
  chatbot: 'AI Assistant', settings: 'Settings', logs: 'Audit Logs',
};

function navigate(page) { window.location.hash = '#' + page; }

async function handleRoute() {
  const page = window.location.hash.slice(1) || 'dashboard';
  if (!state.token) { showLogin(); return; }
  if (page === 'logs' && state.role !== 'SUPER_ADMIN') { navigate('dashboard'); return; }
  // Close SSE stream when leaving logs page
  if (page !== 'logs' && logsEventSource) {
    logsEventSource.close();
    logsEventSource = null;
  }

  document.querySelectorAll('.nav-item').forEach(el =>
    el.classList.toggle('active', el.dataset.page === page));

  setTopbarTitle(PAGE_TITLES[page] || page);
  setTopbarRight('');
  showLoading();

  const renderer = PAGE_RENDERERS[page];
  if (renderer) {
    try { await renderer(); }
    catch (e) { setContent(`<div class="empty-state"><p class="text-danger">Error: ${esc(e.message)}</p></div>`); }
  }
}

// ══════════════════════════════════════════════════════════
// AUTHENTICATION
// ══════════════════════════════════════════════════════════
function showLogin() {
  document.getElementById('login-screen').style.display = 'flex';
  document.getElementById('app-shell').style.display = 'none';
}

function showApp() {
  document.getElementById('login-screen').style.display = 'none';
  document.getElementById('app-shell').style.display = 'flex';
}

function initAuth(tokenData) {
  state.token = tokenData.token;
  state.username = tokenData.username;
  state.role = tokenData.role;
  state.department = tokenData.department || null;
  setToken(state.token);
  setUser({ username: state.username, role: state.role, department: state.department });
  buildSidebar();
  showApp();
  document.getElementById('sidebar-username').textContent = state.username;
  document.getElementById('sidebar-role').textContent = ({ADMIN:'Admin',SUPER_ADMIN:'System Admin',INSTRUCTOR:'Instructor'})[state.role] || state.role;
  document.getElementById('sidebar-avatar').textContent = state.username[0].toUpperCase();
  const deptEl = document.getElementById('sidebar-dept');
  if (deptEl) deptEl.textContent = (state.role === 'ADMIN' && state.department) ? state.department : '';
}

async function handleLogin(e) {
  e.preventDefault();
  const username = document.getElementById('login-username').value.trim();
  const password = document.getElementById('login-password').value;
  const errEl = document.getElementById('login-error');
  const btn = document.getElementById('login-btn');

  errEl.style.display = 'none';
  btn.disabled = true;
  btn.classList.add('btn-loading');
  document.getElementById('login-btn-text').textContent = 'Signing in…';

  try {
    const data = await API.login(username, password);
    if (data.role === 'INSTRUCTOR') {
      errEl.textContent = 'Instructor accounts use the mobile app only.';
      errEl.style.display = 'block';
      return;
    }
    if (!['ADMIN','SUPER_ADMIN'].includes(data.role)) {
      errEl.textContent = 'Access denied.';
      errEl.style.display = 'block';
      return;
    }
    initAuth(data);
    navigate('dashboard');
  } catch (err) {
    errEl.textContent = err.message || 'Invalid credentials';
    errEl.style.display = 'block';
  } finally {
    btn.disabled = false;
    btn.classList.remove('btn-loading');
    document.getElementById('login-btn-text').textContent = 'Sign In';
  }
}

function logout() {
  state.token = null; state.username = null; state.role = null; state.department = null;
  clearToken();
  showLogin();
  document.getElementById('login-form').reset();
}

// ══════════════════════════════════════════════════════════
// SIDEBAR
// ══════════════════════════════════════════════════════════
function buildSidebar() {
  const nav = document.getElementById('sidebar-nav');
  const items = [
    { page: 'dashboard', icon: homeIcon(), label: 'Dashboard' },
    null,
    { section: 'Management' },
    { page: 'users', icon: usersIcon(), label: 'Users' },
    { page: 'classrooms', icon: classroomIcon(), label: 'Classrooms' },
    { page: 'courses', icon: coursesIcon(), label: 'Courses' },
    null,
    { section: 'Scheduling' },
    { page: 'calendar', icon: calendarIcon(), label: 'Update Calendar' },
    { page: 'availability', icon: availIcon(), label: 'Availability' },
    { page: 'weekly', icon: weeklyIcon(), label: 'Weekly Schedule' },
    null,
    { section: 'Assistant' },
    { page: 'chatbot', icon: chatbotIcon(), label: 'AI Assistant' },
    null,
    { section: 'Account' },
    { page: 'settings', icon: settingsIcon(), label: 'Settings' },
  ];
  if (state.role === 'SUPER_ADMIN') {
    items.push(null, { section: 'System' }, { page: 'logs', icon: logsIcon(), label: 'Audit Logs' });
  }
  let html = '';
  items.forEach(item => {
    if (!item) return;
    if (item.section) { html += `<div class="nav-section">${item.section}</div>`; return; }
    html += `<div class="nav-item" data-page="${item.page}" onclick="navigate('${item.page}')">
               ${item.icon}<span>${item.label}</span></div>`;
  });
  nav.innerHTML = html;
}

// SVG icons
function homeIcon() { return `<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="m3 9 9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><polyline points="9 22 9 12 15 12 15 22"/></svg>`; }
function usersIcon() { return `<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>`; }
function classroomIcon() { return `<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="3" width="20" height="14" rx="2"/><line x1="8" y1="21" x2="16" y2="21"/><line x1="12" y1="17" x2="12" y2="21"/></svg>`; }
function coursesIcon() { return `<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z"/><path d="M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z"/></svg>`; }
function calendarIcon() { return `<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="4" width="18" height="18" rx="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>`; }
function availIcon() { return `<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 11 12 14 22 4"/><path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/></svg>`; }
function weeklyIcon() { return `<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/></svg>`; }
function logsIcon() { return `<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="8" y1="6" x2="21" y2="6"/><line x1="8" y1="12" x2="21" y2="12"/><line x1="8" y1="18" x2="21" y2="18"/><line x1="3" y1="6" x2="3.01" y2="6"/><line x1="3" y1="12" x2="3.01" y2="12"/><line x1="3" y1="18" x2="3.01" y2="18"/></svg>`; }
function chatbotIcon() { return `<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>`; }
function chatSendIcon() { return `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/></svg>`; }
function settingsIcon() { return `<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>`; }

// ══════════════════════════════════════════════════════════
// DASHBOARD
// ══════════════════════════════════════════════════════════
async function renderDashboard() {
  const { users, classrooms, courses, history, phase, priorities } = await API.getStats();
  const instructors = users.filter(u => u.role === 'INSTRUCTOR');
  const phaseNum = parseInt((phase.phase || 'PHASE_1').replace('PHASE_','')) || 1;
  const totalPhases = priorities.phasePriorities?.length || 1;
  const phasePct = Math.round((phaseNum / totalPhases) * 100);
  const assignedLec = courses.filter(c => c.lecture_assigned).length;
  const assignedLab = courses.filter(c => c.lab_assigned === true).length;
  const needsLab = courses.filter(c => c.labHours > 0).length;

  const recentHistory = (history || []).slice(0, 8);

  // Department admin: check if current phase has unassigned courses in their dept
  let deptWarningHtml = '';
  if (state.role === 'ADMIN' && state.department) {
    const phasePriorities = priorities.phasePriorities || [];
    const currentPriority = phasePriorities[phaseNum - 1] ?? null;
    const unassigned = courses.filter(c =>
      c.department === state.department &&
      (currentPriority === null || c.priority === currentPriority) &&
      (!c.lecture_assigned || (c.labHours > 0 && c.lab_assigned === false))
    );
    if (unassigned.length > 0) {
      deptWarningHtml = `
        <div style="background:#fef3c7;border:1px solid #f59e0b;border-radius:10px;padding:14px 18px;margin-bottom:16px;display:flex;align-items:center;gap:12px">
          <span style="font-size:20px">⚠️</span>
          <div>
            <strong style="color:#92400e">Phase ${phaseNum} is active — please schedule your department's courses.</strong>
            <div style="font-size:13px;color:#78350f;margin-top:2px">
              Department: <strong>${esc(state.department)}</strong> &nbsp;·&nbsp;
              ${unassigned.length} course${unassigned.length > 1 ? 's' : ''} pending assignment.
              <a href="#calendar" style="color:#b45309;font-weight:600;margin-left:8px">Go to Calendar →</a>
            </div>
          </div>
        </div>`;
    }
  }

  setContent(`${deptWarningHtml}
    <div class="stats-grid">
      <div class="stat-card">
        <div class="stat-icon indigo">${usersIcon()}</div>
        <div><div class="stat-value">${users.length}</div><div class="stat-label">Total Users</div></div>
      </div>
      <div class="stat-card">
        <div class="stat-icon blue">${usersIcon()}</div>
        <div><div class="stat-value">${instructors.length}</div><div class="stat-label">Instructors</div></div>
      </div>
      <div class="stat-card">
        <div class="stat-icon green">${classroomIcon()}</div>
        <div><div class="stat-value">${classrooms.length}</div><div class="stat-label">Classrooms</div></div>
      </div>
      <div class="stat-card">
        <div class="stat-icon amber">${coursesIcon()}</div>
        <div><div class="stat-value">${courses.length}</div><div class="stat-label">Courses</div></div>
      </div>
      <div class="stat-card">
        <div class="stat-icon pink">${calendarIcon()}</div>
        <div><div class="stat-value">${assignedLec}</div><div class="stat-label">Lectures Assigned</div></div>
      </div>
    </div>

    <div style="display:grid;grid-template-columns:1fr 1fr;gap:16px">
      <!-- Phase Status -->
      <div class="card">
        <div class="card-header"><span class="card-title">Scheduling Phase</span></div>
        <div class="card-body">
          <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:8px">
            <span style="font-weight:700;font-size:18px;color:var(--primary)">${phase.phase || 'PHASE_1'}</span>
            <span class="text-muted text-sm">Phase ${phaseNum} of ${totalPhases}</span>
          </div>
          <div class="score-bar"><div class="score-bar-fill ${phaseNum >= totalPhases ? 'high' : 'mid'}" style="width:${phasePct}%"></div></div>
          <div style="display:flex;justify-content:space-between;margin-top:12px;font-size:13px">
            <span>Lectures assigned: <strong>${assignedLec}/${courses.length}</strong></span>
            <span>Labs assigned: <strong>${assignedLab}/${needsLab}</strong></span>
          </div>
          <div style="margin-top:10px">
            <span class="text-muted text-sm">Phase priorities: </span>
            ${(priorities.phasePriorities || []).map((p,i) => `<span class="badge ${i+1===phaseNum ? 'badge-primary' : 'badge-gray'}" style="margin:2px">${p}</span>`).join('')}
          </div>
        </div>
      </div>

      <!-- Recent Changes -->
      <div class="card">
        <div class="card-header"><span class="card-title">Recent Schedule Changes</span></div>
        <div class="card-body" style="padding:0">
          ${recentHistory.length === 0
            ? '<div class="empty-state" style="padding:24px"><p>No schedule changes yet</p></div>'
            : `<div class="table-wrapper"><table>
                <thead><tr><th>Instructor</th><th>Day</th><th>Slot</th><th>Change</th><th>By</th></tr></thead>
                <tbody>${recentHistory.map(h => `
                  <tr>
                    <td>${esc(h.instructorFullName || h.instructorUsername)}</td>
                    <td>${esc(h.day)}</td>
                    <td style="font-size:11px">${esc(h.timeSlot)}</td>
                    <td style="font-size:11px">
                      ${h.previousCourseCode ? `<span style="color:var(--danger)">${esc(h.previousCourseCode)}</span> → ` : ''}
                      ${h.newCourseCode ? `<span style="color:var(--success)">${esc(h.newCourseCode)}</span>` : '<em>cleared</em>'}
                    </td>
                    <td style="font-size:11px">${esc(h.changedBy)}</td>
                  </tr>`).join('')}</tbody>
              </table></div>`}
        </div>
      </div>
    </div>

    <div style="margin-top:16px" class="card">
      <div class="card-header"><span class="card-title">Quick Actions</span></div>
      <div class="card-body" style="display:flex;gap:10px;flex-wrap:wrap">
        <button class="btn btn-primary" onclick="navigate('users')">+ Add User</button>
        <button class="btn btn-secondary" onclick="navigate('classrooms')">+ Add Classroom</button>
        <button class="btn btn-secondary" onclick="navigate('courses')">Import Courses</button>
        <button class="btn btn-secondary" onclick="navigate('calendar')">Update Calendar</button>
        <button class="btn btn-secondary" onclick="navigate('weekly')">Weekly XAI</button>
      </div>
    </div>
  `);
}

// ══════════════════════════════════════════════════════════
// USERS
// ══════════════════════════════════════════════════════════
async function renderUsers() {
  const users = await API.getUsers();
  state._users = users;

  setTopbarRight(`<button class="btn btn-primary" onclick="openAddUserModal()">+ Add User</button>`);

  renderUsersTable(users, state.userFilter);
}

function renderUsersTable(users, filter = '') {
  const filtered = users.filter(u =>
    !filter ||
    u.username.toLowerCase().includes(filter) ||
    (u.fullName || '').toLowerCase().includes(filter) ||
    (u.email || '').toLowerCase().includes(filter) ||
    u.role.toLowerCase().includes(filter)
  );

  setContent(`
    <div class="page-toolbar">
      <input class="search-input" placeholder="Search users…" value="${esc(filter)}"
             oninput="state.userFilter=this.value; renderUsersTable(state._users, this.value)">
      <select class="filter-select" onchange="filterUsersByRole(this.value)">
        <option value="">All Roles</option>
        <option value="ADMIN">Admin</option>
        <option value="INSTRUCTOR">Instructor</option>
        <option value="SUPER_ADMIN">Super Admin</option>
      </select>
      <button class="btn btn-primary" onclick="openAddUserModal()">+ Add User</button>
    </div>
    <div class="card">
      <div class="card-header">
        <span class="card-title">Users (${filtered.length})</span>
      </div>
      <div class="table-wrapper">
        <table>
          <thead><tr><th>Username</th><th>Full Name</th><th>Role</th><th>Email</th><th>Department</th><th>Actions</th></tr></thead>
          <tbody>
            ${filtered.length === 0 ? `<tr><td colspan="6" style="text-align:center;color:var(--text-muted)">No users found</td></tr>` :
              filtered.map(u => `
              <tr>
                <td><strong>${esc(u.username)}</strong></td>
                <td>${esc(u.fullName)}</td>
                <td>${roleBadge(u.role)}</td>
                <td class="text-muted text-sm">${esc(u.email || '—')}</td>
                <td class="text-muted text-sm">${esc(u.department || '—')}</td>
                <td>
                  <div class="td-actions">
                    <button class="btn btn-sm btn-ghost" onclick="openEditUserModal('${esc(u.username)}')">Edit</button>
                    <button class="btn btn-sm btn-ghost" onclick="openResetPasswordModal('${esc(u.username)}')">Reset PW</button>
                    ${u.role !== 'SUPER_ADMIN' ? `<button class="btn btn-sm btn-danger" onclick="deleteUser('${esc(u.username)}')">Delete</button>` : ''}
                  </div>
                </td>
              </tr>`).join('')}
          </tbody>
        </table>
      </div>
    </div>
  `);
}

function filterUsersByRole(role) {
  if (!role) { renderUsersTable(state._users, state.userFilter); return; }
  renderUsersTable(state._users.filter(u => u.role === role), state.userFilter);
}

function openAddUserModal() {
  openModal('Add User', `
    <div class="form-group"><label>Username</label><input id="nu-username" placeholder="e.g. john_doe"></div>
    <div class="form-group"><label>Full Name</label><input id="nu-fullname" placeholder="John Doe"></div>
    <div class="form-row">
      <div class="form-group"><label>Role</label>
        <select id="nu-role">
          <option value="INSTRUCTOR">Instructor</option>
          <option value="ADMIN">Admin</option>
        </select>
      </div>
      <div class="form-group"><label>Department</label><input id="nu-dept" placeholder="Computer Engineering"></div>
    </div>
    <div class="form-group"><label>Email</label><input id="nu-email" type="email" placeholder="john@example.com"></div>
    <div class="form-group">
      <label>Password</label>
      <div style="display:flex;gap:8px;align-items:center">
        <input id="nu-password" type="text" placeholder="Min 8 chars, uppercase, number, special" style="flex:1;font-family:monospace">
        <button type="button" class="btn btn-sm btn-ghost" onclick="document.getElementById('nu-password').value=generatePassword()">Generate</button>
      </div>
    </div>
    <p class="hint">User will be prompted to change password on first login.</p>
  `, `
    <button class="btn btn-secondary" onclick="closeModal()">Cancel</button>
    <button class="btn btn-primary" onclick="submitAddUser()">Create User</button>
  `);
}

async function submitAddUser() {
  const username = document.getElementById('nu-username').value.trim();
  const fullName = document.getElementById('nu-fullname').value.trim();
  const role = document.getElementById('nu-role').value;
  const department = document.getElementById('nu-dept').value.trim();
  const email = document.getElementById('nu-email').value.trim();
  const password = document.getElementById('nu-password').value;

  if (!username || !fullName || !password) { toast('Fill in all required fields', 'error'); return; }

  try {
    const passwordHash = await sha256(password);
    await API.createUser({ username, passwordHash, role, fullName, email, department });
    closeModal();
    toast('User created successfully', 'success');
    await renderUsers();
  } catch (e) { toast(e.message, 'error'); }
}

function openEditUserModal(username) {
  const u = state._users.find(x => x.username === username);
  if (!u) return;
  openModal('Edit User: ' + username, `
    <div class="form-group"><label>Full Name</label><input id="eu-fullname" value="${esc(u.fullName)}"></div>
    <div class="form-row">
      <div class="form-group"><label>Role</label>
        <select id="eu-role">
          <option value="INSTRUCTOR" ${u.role==='INSTRUCTOR'?'selected':''}>Instructor</option>
          <option value="ADMIN" ${u.role==='ADMIN'?'selected':''}>Admin</option>
        </select>
      </div>
      <div class="form-group"><label>Department</label><input id="eu-dept" value="${esc(u.department||'')}"></div>
    </div>
    <div class="form-group"><label>Email</label><input id="eu-email" type="email" value="${esc(u.email||'')}"></div>
  `, `
    <button class="btn btn-secondary" onclick="closeModal()">Cancel</button>
    <button class="btn btn-primary" onclick="submitEditUser('${esc(username)}')">Save Changes</button>
  `);
}

async function submitEditUser(username) {
  const fullName = document.getElementById('eu-fullname').value.trim();
  const role = document.getElementById('eu-role').value;
  const department = document.getElementById('eu-dept').value.trim();
  const email = document.getElementById('eu-email').value.trim();
  if (!fullName) { toast('Full name is required', 'error'); return; }
  try {
    await API.updateUser(username, { role, fullName, email, department });
    closeModal();
    toast('User updated', 'success');
    await renderUsers();
  } catch (e) { toast(e.message, 'error'); }
}

function openResetPasswordModal(username) {
  openModal('Reset Password: ' + username, `
    <div class="form-group">
      <label>New Password</label>
      <div style="display:flex;gap:8px;align-items:center">
        <input id="rp-password" type="text" placeholder="Min 8 chars, uppercase, number, special" style="flex:1;font-family:monospace">
        <button type="button" class="btn btn-sm btn-ghost" onclick="document.getElementById('rp-password').value=generatePassword()">Generate</button>
      </div>
    </div>
    <p class="hint">User will be prompted to change password on next login.</p>
  `, `
    <button class="btn btn-secondary" onclick="closeModal()">Cancel</button>
    <button class="btn btn-warning" style="background:var(--warning);color:white" onclick="submitResetPassword('${esc(username)}')">Reset Password</button>
  `);
}

async function submitResetPassword(username) {
  const password = document.getElementById('rp-password').value;
  if (!password || password.length < 6) { toast('Password too short', 'error'); return; }
  try {
    await API.resetPassword(username, password);
    closeModal();
    toast('Password reset successfully', 'success');
  } catch (e) { toast(e.message, 'error'); }
}

async function deleteUser(username) {
  const ok = await confirmModal('Delete User', `Are you sure you want to delete <strong>${esc(username)}</strong>? This cannot be undone.`);
  if (!ok) return;
  try {
    await API.deleteUser(username);
    toast('User deleted', 'success');
    await renderUsers();
  } catch (e) { toast(e.message, 'error'); }
}

// ══════════════════════════════════════════════════════════
// CLASSROOMS
// ══════════════════════════════════════════════════════════
async function renderClassrooms() {
  const classrooms = await API.getClassrooms();
  state._classrooms = classrooms;

  setTopbarRight(`
    <button class="btn btn-secondary" onclick="openImportClassroomsModal()" style="margin-right:8px">📥 Import Excel</button>
    <button class="btn btn-primary" onclick="openAddClassroomModal()">+ Add Classroom</button>
  `);

  setContent(`
    <div class="page-toolbar">
      <input class="search-input" placeholder="Search classrooms…"
             oninput="filterClassrooms(this.value)">
      <button class="btn btn-secondary" onclick="openImportClassroomsModal()">📥 Import Excel</button>
      <button class="btn btn-primary" onclick="openAddClassroomModal()">+ Add Classroom</button>
    </div>
    <div class="card" id="classrooms-card">
      <div class="card-header">
        <span class="card-title">Classrooms (${classrooms.length})</span>
        <span class="text-muted text-sm">Sorted by room code</span>
      </div>
      <div class="table-wrapper">
        <table id="classrooms-table">
          <thead><tr><th>Room Code</th><th>Capacity</th><th>ID</th><th>Actions</th></tr></thead>
          <tbody>${classrooms.sort((a,b) => a.roomCode.localeCompare(b.roomCode)).map(c => `
            <tr data-id="${esc(c.id)}">
              <td><strong>${esc(c.roomCode)}</strong></td>
              <td>${c.capacity}</td>
              <td class="text-muted text-sm">${esc(c.id)}</td>
              <td><button class="btn btn-sm btn-danger" onclick="deleteClassroom('${esc(c.id)}', '${esc(c.roomCode)}')">Delete</button></td>
            </tr>`).join('')}
          </tbody>
        </table>
      </div>
    </div>
  `);
}

function filterClassrooms(q) {
  const rows = document.querySelectorAll('#classrooms-table tbody tr');
  rows.forEach(r => {
    r.style.display = !q || r.textContent.toLowerCase().includes(q.toLowerCase()) ? '' : 'none';
  });
}

function openAddClassroomModal() {
  openModal('Add Classroom', `
    <div class="form-row">
      <div class="form-group"><label>Room Code</label><input id="cr-code" placeholder="e.g. B101"></div>
      <div class="form-group"><label>Capacity</label><input id="cr-cap" type="number" min="1" placeholder="30"></div>
    </div>
    <div class="form-group"><label>ID (leave blank to auto-generate)</label>
      <input id="cr-id" placeholder="Optional custom ID">
    </div>
  `, `
    <button class="btn btn-secondary" onclick="closeModal()">Cancel</button>
    <button class="btn btn-primary" onclick="submitAddClassroom()">Add Classroom</button>
  `);
}

async function submitAddClassroom() {
  const roomCode = document.getElementById('cr-code').value.trim().toUpperCase();
  const capacity = parseInt(document.getElementById('cr-cap').value) || 0;
  let id = document.getElementById('cr-id').value.trim();
  if (!roomCode) { toast('Room code is required', 'error'); return; }
  if (!id) id = roomCode + '_' + Date.now();
  try {
    await API.addClassroom({ id, roomCode, capacity });
    closeModal();
    toast('Classroom added', 'success');
    await renderClassrooms();
  } catch (e) { toast(e.message, 'error'); }
}

async function deleteClassroom(id, code) {
  const ok = await confirmModal('Delete Classroom', `Delete classroom <strong>${esc(code)}</strong>?`);
  if (!ok) return;
  try {
    await API.deleteClassroom(id);
    toast('Classroom deleted', 'success');
    await renderClassrooms();
  } catch (e) { toast(e.message, 'error'); }
}

window._excelClassrooms = [];

function openImportClassroomsModal() {
  window._excelClassrooms = [];
  openModal('Import Classrooms from Excel', `
    <div class="info-box">
      Expected columns (row 1 = header, data from row 2):<br>
      <code style="font-size:11px">Room Code | Capacity</code>
    </div>
    <div class="form-group">
      <label>Excel File (.xlsx / .xls)</label>
      <input type="file" id="classroom-excel-file" accept=".xlsx,.xls" onchange="previewClassroomExcel(this)">
    </div>
    <div id="classroom-excel-preview" style="max-height:300px;overflow-y:auto;margin-top:12px"></div>
  `, `
    <button class="btn btn-secondary" onclick="closeModal()">Cancel</button>
    <button class="btn btn-primary" id="classroom-import-btn" disabled onclick="submitClassroomImport()">Import</button>
  `);
}

async function previewClassroomExcel(input) {
  const file = input.files[0];
  if (!file) return;
  const preview = document.getElementById('classroom-excel-preview');
  preview.innerHTML = '<div class="loading-spinner" style="height:60px"><div class="spinner"></div></div>';
  window._excelClassrooms = [];
  document.getElementById('classroom-import-btn').disabled = true;

  try {
    const buf = await file.arrayBuffer();
    const wb = XLSX.read(buf, { type: 'array' });
    const ws = wb.Sheets[wb.SheetNames[0]];
    const rows = XLSX.utils.sheet_to_json(ws, { header: 1 });

    if (!rows.length) {
      preview.innerHTML = '<p class="text-danger">File is empty.</p>';
      return;
    }

    const headerRow = rows[0] || [];
    const nonEmpty = headerRow.filter(c => String(c || '').trim()).length;
    const h0 = String(headerRow[0] || '').trim().toLowerCase();
    const h1 = String(headerRow[1] || '').trim().toLowerCase();

    if (nonEmpty >= 5) {
      preview.innerHTML = `<p class="text-danger">⛔ This looks like a <strong>Courses</strong> file. Please import it on the <strong>Courses</strong> page.</p>`;
      return;
    }

    if (nonEmpty !== 2 || (h0 !== 'room code' && h0 !== 'classroom code') || h1 !== 'capacity') {
      preview.innerHTML = `
        <p class="text-danger">⛔ Wrong file format.</p>
        <p class="text-muted text-sm" style="margin-top:6px">Expected header (row 1): <code>Room Code | Capacity</code></p>`;
      return;
    }

    // Strict row-by-row validation — any error → reject entire file
    const parsed = [];
    const errors = [];

    for (let i = 1; i < rows.length; i++) {
      const r = rows[i];
      const roomCode = String(r[0] || '').trim().toUpperCase();
      const capRaw   = r[1];

      // Skip fully blank rows
      if (!roomCode && (capRaw === undefined || capRaw === null || String(capRaw).trim() === '')) continue;

      const rowNum = i + 1;

      if (!roomCode) {
        errors.push({ row: rowNum, msg: 'Room Code is required' });
        continue;
      }

      let capacity = 0;
      if (capRaw !== undefined && capRaw !== null && String(capRaw).trim() !== '') {
        const n = Number(String(capRaw).trim());
        if (!Number.isInteger(n) || isNaN(n) || n < 0) {
          errors.push({ row: rowNum, msg: `Capacity "${capRaw}" must be a non-negative integer` });
          continue;
        }
        capacity = n;
      }

      parsed.push({ roomCode, capacity });
    }

    if (errors.length > 0) {
      preview.innerHTML = `
        <p class="text-danger">⛔ <strong>${errors.length} validation error(s)</strong> — fix the file and re-upload. Nothing will be imported.</p>
        <table style="font-size:12px;width:100%;margin-top:8px">
          <thead><tr><th>Row</th><th>Error</th></tr></thead>
          <tbody>${errors.map(e => `<tr><td>Row ${e.row}</td><td>${esc(e.msg)}</td></tr>`).join('')}</tbody>
        </table>`;
      return;
    }

    if (parsed.length === 0) {
      preview.innerHTML = '<p class="text-danger">No data rows found.</p>';
      return;
    }

    // Duplicate detection against existing classrooms
    const existing = await API.getClassrooms();
    const existingCodes = new Set(existing.map(c => c.roomCode.toUpperCase()));

    const newItems = parsed.filter(c => !existingCodes.has(c.roomCode));
    const dupItems = parsed.filter(c =>  existingCodes.has(c.roomCode));

    window._excelClassrooms = newItems;

    const showRows = parsed.slice(0, 15).map(c => {
      const isDup = existingCodes.has(c.roomCode);
      return `<tr style="${isDup ? 'opacity:0.5;' : ''}">
        <td>${isDup ? '⚠ ' : ''}${esc(c.roomCode)}</td>
        <td>${c.capacity}</td>
        <td>${isDup
          ? '<span class="badge badge-warning">Already exists</span>'
          : '<span class="badge badge-success">New</span>'}</td>
      </tr>`;
    }).join('');

    preview.innerHTML = `
      <p style="margin-bottom:8px">
        <strong>${newItems.length} new</strong> to import
        ${dupItems.length > 0 ? ` · <span style="color:#d97706">${dupItems.length} duplicate(s) will be skipped</span>` : ''}
      </p>
      <table style="font-size:12px;width:100%">
        <thead><tr><th>Room Code</th><th>Capacity</th><th>Status</th></tr></thead>
        <tbody>
          ${showRows}
          ${parsed.length > 15 ? `<tr><td colspan="3" style="text-align:center;color:var(--text-muted)">… and ${parsed.length - 15} more</td></tr>` : ''}
        </tbody>
      </table>
      ${newItems.length === 0 ? '<p style="color:#d97706;margin-top:8px">All classrooms already exist. Nothing to import.</p>' : ''}`;

    document.getElementById('classroom-import-btn').disabled = newItems.length === 0;
  } catch (e) {
    preview.innerHTML = `<p class="text-danger">Failed to parse file: ${esc(e.message)}</p>`;
  }
}

async function submitClassroomImport() {
  const classrooms = window._excelClassrooms;
  if (!classrooms?.length) return;

  const btn = document.getElementById('classroom-import-btn');
  btn.disabled = true;
  btn.textContent = 'Importing…';

  let saved = 0;
  let skipped = 0;

  for (const c of classrooms) {
    try {
      const id = c.roomCode + '_' + Date.now() + '_' + Math.random().toString(36).slice(2, 6);
      await API.addClassroom({ id, roomCode: c.roomCode, capacity: c.capacity });
      saved++;
    } catch (e) {
      skipped++;
    }
  }

  closeModal();
  toast(skipped > 0
    ? `${saved} classrooms imported, ${skipped} failed.`
    : `${saved} classrooms imported.`,
    'success');
  await renderClassrooms();
}

// ══════════════════════════════════════════════════════════
// COURSES
// ══════════════════════════════════════════════════════════
async function renderCourses() {
  const courses = await API.getCourses();
  state._courses = courses;

  setTopbarRight(`<button class="btn btn-primary" onclick="openImportModal()">Import Excel</button>`);

  const depts = [...new Set(courses.map(c => c.department).filter(Boolean))].sort();

  setContent(`
    <div class="page-toolbar">
      <input class="search-input" placeholder="Search courses…" value="${esc(state.courseFilter)}"
             oninput="state.courseFilter=this.value; filterCourses()">
      <select class="filter-select" id="course-dept-filter" onchange="filterCourses()">
        <option value="">All Departments</option>
        ${depts.map(d => `<option value="${esc(d)}">${esc(d)}</option>`).join('')}
      </select>
      <button class="btn btn-primary" onclick="openImportModal()">📥 Import Excel</button>
    </div>

    <div class="card">
      <div class="card-header">
        <span class="card-title">Courses (${courses.length})</span>
        <span class="text-muted text-sm">Lecture/Lab assignment status shown</span>
      </div>
      <div class="table-wrapper">
        <table id="courses-table">
          <thead>
            <tr>
              <th>Code</th><th>Name</th><th>Lecturer</th><th>Dept</th>
              <th>Sem</th><th>Students</th><th>Priority</th><th>L/Lab hrs</th>
              <th>Status</th><th>Actions</th>
            </tr>
          </thead>
          <tbody id="courses-tbody">
            ${buildCourseRows(courses)}
          </tbody>
        </table>
      </div>
    </div>
  `);
}

function buildCourseRows(courses) {
  if (courses.length === 0) return `<tr><td colspan="10" style="text-align:center;color:var(--text-muted)">No courses imported yet</td></tr>`;
  return courses.map(c => {
    const lecStatus = c.lecture_assigned
      ? '<span class="badge badge-success">✓ Lec</span>'
      : '<span class="badge badge-gray">Lec</span>';
    const labStatus = c.labHours > 0
      ? (c.lab_assigned === true ? '<span class="badge badge-success">✓ Lab</span>' : '<span class="badge badge-warning">Lab</span>')
      : '';
    const isCommon = c.department === 'COMMON';
    return `<tr>
      <td><strong>${esc(c.code)}</strong>${isCommon ? ' <span class="badge badge-info">COMMON</span>' : ''}</td>
      <td>${esc(c.name)}</td>
      <td class="text-sm">${esc(c.lecturerUsername || '—')}</td>
      <td class="text-sm">${esc(c.department)}</td>
      <td>${c.semester}</td>
      <td>${c.studentCount}</td>
      <td><span class="badge badge-primary">${c.priority}</span></td>
      <td class="text-sm">${c.lectureHours}h / ${c.labHours}h</td>
      <td>${lecStatus} ${labStatus}</td>
      <td><button class="btn btn-sm btn-danger" onclick="deleteCourseConfirm('${esc(c.code)}', '${esc(c.department)}', '${esc(c.email)}')">Delete</button></td>
    </tr>`;
  }).join('');
}

function filterCourses() {
  const q = (state.courseFilter || '').toLowerCase();
  const dept = document.getElementById('course-dept-filter')?.value || '';
  const filtered = (state._courses || []).filter(c =>
    (!q || c.code.toLowerCase().includes(q) || c.name.toLowerCase().includes(q) ||
     (c.lecturerUsername || '').toLowerCase().includes(q)) &&
    (!dept || c.department === dept)
  );
  const tbody = document.getElementById('courses-tbody');
  if (tbody) tbody.innerHTML = buildCourseRows(filtered);
}

async function deleteCourseConfirm(code, dept, email) {
  const ok = await confirmModal('Delete Course', `Delete course <strong>${esc(code)}</strong>?<br><small>Note: deletes all entries with this code.</small>`);
  if (!ok) return;
  try {
    await API.deleteCourse(code, dept, email);
    toast('Course deleted', 'success');
    await renderCourses();
  } catch (e) { toast(e.message, 'error'); }
}

function openImportModal() {
  openModal('Import Courses from Excel', `
    <div class="info-box">
      Expected columns (row 1 = header, data from row 2):<br>
      <code style="font-size:11px">Course Code | Course Name | Lecturer | Department | Email | Semester | StudentCount | priority | LecHours | LabHours</code>
    </div>
    <div class="form-group">
      <label>Excel File (.xlsx / .xls)</label>
      <input type="file" id="excel-file" accept=".xlsx,.xls" onchange="previewExcel(this)">
    </div>
    <div id="excel-preview" style="max-height:300px;overflow-y:auto;margin-top:12px"></div>
  `, `
    <button class="btn btn-secondary" onclick="closeModal()">Cancel</button>
    <button class="btn btn-primary" id="import-btn" disabled onclick="submitImport()">Import</button>
  `);
}

window._excelCourses = [];

async function previewExcel(input) {
  const file = input.files[0];
  if (!file) return;
  const preview = document.getElementById('excel-preview');
  preview.innerHTML = '<div class="loading-spinner" style="height:60px"><div class="spinner"></div></div>';
  window._excelCourses = [];
  document.getElementById('import-btn').disabled = true;

  try {
    const buf = await file.arrayBuffer();
    const wb = XLSX.read(buf, { type: 'array' });
    const ws = wb.Sheets[wb.SheetNames[0]];
    const rows = XLSX.utils.sheet_to_json(ws, { header: 1 });

    if (!rows.length) {
      preview.innerHTML = '<p class="text-danger">File is empty.</p>';
      return;
    }

    const headerRow = rows[0] || [];
    const nonEmpty = headerRow.filter(c => String(c || '').trim()).length;
    const h0 = String(headerRow[0] || '').trim().toLowerCase();
    const h1 = String(headerRow[1] || '').trim().toLowerCase();

    // Detect classrooms file (2 cols: room code + capacity)
    if (nonEmpty <= 2 && (h0 === 'room code' || h0 === 'classroom code') && h1 === 'capacity') {
      preview.innerHTML = `<p class="text-danger">⛔ This looks like a <strong>Classrooms</strong> file. Please import it on the <strong>Classrooms</strong> page.</p>`;
      return;
    }

    // Courses file must have exactly 10 non-empty columns
    if (nonEmpty < 10) {
      preview.innerHTML = `
        <p class="text-danger">⛔ Wrong file format — found ${nonEmpty} column(s), expected 10.</p>
        <p class="text-muted text-sm" style="margin-top:6px">Expected header (row 1):<br>
        <code>Course Code | Course Name | Lecturer | Department | Email | Semester | StudentCount | priority | LecHours | LabHours</code></p>`;
      return;
    }

    // Strict row-by-row validation — any error → reject entire file
    const parsed = [];
    const errors = [];

    for (let i = 1; i < rows.length; i++) {
      const r = rows[i];
      const code       = String(r[0] || '').trim();
      const name       = String(r[1] || '').trim();
      const lecturer   = String(r[2] || '').trim();
      const department = String(r[3] || '').trim();
      const email      = String(r[4] || '').trim();

      // Skip fully blank rows
      if (!code && !name && !lecturer && !department && !email) continue;

      const rowNum = i + 1;
      const rowErrors = [];

      if (!code)       rowErrors.push('Course Code is required');
      if (!name)       rowErrors.push('Course Name is required');
      if (!lecturer)   rowErrors.push('Lecturer is required');
      if (!department) rowErrors.push('Department is required');
      if (!email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email))
        rowErrors.push(`Email "${email || '(empty)'}" is not valid`);

      // Integer fields — text in these fields is a hard error
      const intFields = [
        { idx: 5, name: 'Semester',      min: 1, def: 1 },
        { idx: 6, name: 'StudentCount',  min: 0, def: 0 },
        { idx: 7, name: 'priority',      min: 1, def: 1 },
        { idx: 8, name: 'LecHours',      min: 0, def: 0 },
        { idx: 9, name: 'LabHours',      min: 0, def: 0 },
      ];
      const intVals = {};
      for (const f of intFields) {
        const raw = r[f.idx];
        if (raw === undefined || raw === null || String(raw).trim() === '') {
          intVals[f.name] = f.def;
        } else {
          const n = Number(String(raw).trim());
          if (!Number.isInteger(n) || isNaN(n)) {
            rowErrors.push(`${f.name} "${raw}" must be an integer, not text`);
          } else if (n < f.min) {
            rowErrors.push(`${f.name} must be ≥ ${f.min} (got ${n})`);
          } else {
            intVals[f.name] = n;
          }
        }
      }

      if (rowErrors.length > 0) {
        errors.push({ row: rowNum, msgs: rowErrors });
      } else {
        parsed.push({
          code, name, lecturer, department, email,
          semester:     intVals['Semester'],
          studentCount: intVals['StudentCount'],
          priority:     intVals['priority'],
          lectureHours: intVals['LecHours'],
          labHours:     intVals['LabHours'],
        });
      }
    }

    if (errors.length > 0) {
      const errorRows = errors.flatMap(e =>
        e.msgs.map((m, idx) => `<tr>
          <td>${idx === 0 ? `Row ${e.row}` : ''}</td>
          <td>${esc(m)}</td>
        </tr>`)
      ).join('');
      preview.innerHTML = `
        <p class="text-danger">⛔ <strong>${errors.length} row(s) with errors</strong> — fix the file and re-upload. Nothing will be imported.</p>
        <table style="font-size:12px;width:100%;margin-top:8px">
          <thead><tr><th>Row</th><th>Error</th></tr></thead>
          <tbody>${errorRows}</tbody>
        </table>`;
      return;
    }

    if (parsed.length === 0) {
      preview.innerHTML = '<p class="text-danger">No data rows found.</p>';
      return;
    }

    // Duplicate detection: (code | department | lecturerUsername) triple
    const [existingCourses, existingUsers] = await Promise.all([API.getCourses(), API.getUsers()]);
    const existingUsernames = existingUsers.map(u => u.username);
    const existingKeys = new Set(
      existingCourses.map(c => `${c.code}|${c.department}|${(c.lecturerUsername || '').toLowerCase()}`)
    );

    const withMeta = parsed.map(c => {
      const uname = usernameFromEmail(c.email, c.lecturer, existingUsernames);
      const isDup = existingKeys.has(`${c.code}|${c.department}|${uname.toLowerCase()}`);
      return { ...c, _username: uname, _isDup: isDup };
    });

    const newItems = withMeta.filter(c => !c._isDup);
    const dupItems = withMeta.filter(c =>  c._isDup);

    window._excelCourses = newItems;

    const showRows = withMeta.slice(0, 10).map(c => `
      <tr style="${c._isDup ? 'opacity:0.5;' : ''}">
        <td>${c._isDup ? '⚠ ' : ''}${esc(c.code)}</td>
        <td>${esc(c.name)}</td>
        <td>${esc(c.lecturer)}</td>
        <td>${esc(c.department)}</td>
        <td>${c.semester}</td>
        <td>${c.priority}</td>
        <td>${c._isDup
          ? '<span class="badge badge-warning">Duplicate</span>'
          : '<span class="badge badge-success">New</span>'}</td>
      </tr>`).join('');

    preview.innerHTML = `
      <p style="margin-bottom:8px">
        <strong>${newItems.length} new</strong> to import
        ${dupItems.length > 0 ? ` · <span style="color:#d97706">${dupItems.length} duplicate(s) will be skipped</span>` : ''}
      </p>
      <table style="font-size:12px;width:100%">
        <thead><tr><th>Code</th><th>Name</th><th>Lecturer</th><th>Dept</th><th>Sem</th><th>Pri</th><th>Status</th></tr></thead>
        <tbody>
          ${showRows}
          ${withMeta.length > 10 ? `<tr><td colspan="7" style="text-align:center;color:var(--text-muted)">… and ${withMeta.length - 10} more</td></tr>` : ''}
        </tbody>
      </table>
      ${newItems.length === 0 ? '<p style="color:#d97706;margin-top:8px">All courses already exist. Nothing to import.</p>' : ''}`;

    document.getElementById('import-btn').disabled = newItems.length === 0;
  } catch (e) {
    preview.innerHTML = `<p class="text-danger">Failed to parse file: ${esc(e.message)}</p>`;
  }
}

async function submitImport() {
  const courses = window._excelCourses;
  if (!courses?.length) return;

  const btn = document.getElementById('import-btn');
  btn.disabled = true;
  btn.textContent = 'Importing…';

  try {
    // 1. Fetch existing users to know which instructors already exist
    const existingUsers = await API.getUsers();
    const existingUsernames = existingUsers.map(u => u.username);
    const newCredentials = [];

    // 2. Create missing instructor accounts (same logic as mobile importCourseData)
    const seenUsernames = new Set();
    for (const c of courses) {
      const username = usernameFromEmail(c.email, c.lecturer, existingUsernames);
      if (seenUsernames.has(username)) continue;
      seenUsernames.add(username);

      if (existingUsernames.includes(username)) continue;

      const plainPassword = generatePassword();
      const passwordHash = sha256(plainPassword);
      try {
        await API.createUser({
          username,
          passwordHash,
          role: 'INSTRUCTOR',
          fullName: c.lecturer,
          email: c.email,
          department: c.department,
        });
        newCredentials.push({ username, password: plainPassword });
        existingUsernames.push(username);
      } catch (e) {
        // user might already exist on server; continue
      }
    }

    // 3. Import courses with correct lecturerUsername (from email, not full name)
    const payload = courses.map(c => ({
      code: c.code, name: c.name,
      lecturerUsername: usernameFromEmail(c.email, c.lecturer, existingUsernames),
      department: c.department, email: c.email,
      duration: 1, classroomId: null,
      semester: c.semester, studentCount: c.studentCount,
      priority: c.priority, lectureHours: c.lectureHours, labHours: c.labHours,
    }));
    await API.importCourses(payload);
    closeModal();
    toast(
      `${payload.length} course(s) imported` +
      (newCredentials.length ? `, ${newCredentials.length} instructor account(s) created` : ''),
      'success'
    );

    // 4. Show credentials dialog if new instructors were created
    if (newCredentials.length > 0) {
      showCredentialsDialog(newCredentials);
    }

    await renderCourses();
  } catch (e) {
    toast(e.message, 'error');
    btn.disabled = false;
    btn.textContent = 'Import';
  }
}

function showCredentialsDialog(credentials) {
  const rows = credentials.map(c => `
    <tr>
      <td style="font-family:monospace;font-weight:600">${esc(c.username)}</td>
      <td style="font-family:monospace">${esc(c.password)}</td>
    </tr>`).join('');

  window._credsToCopy = credentials.map(c => `${c.username} / ${c.password}`).join('\n');

  openModal('New Instructor Accounts', `
    <p style="margin-bottom:12px;color:var(--text-muted)">Save these credentials — passwords cannot be retrieved later.</p>
    <div style="max-height:320px;overflow-y:auto">
      <table style="width:100%;font-size:13px">
        <thead><tr><th style="text-align:left">Username</th><th style="text-align:left">Temporary Password</th></tr></thead>
        <tbody>${rows}</tbody>
      </table>
    </div>
  `, `
    <button class="btn btn-ghost" onclick="navigator.clipboard.writeText(window._credsToCopy).then(()=>toast('Copied!','success'))">Copy All</button>
    <button class="btn btn-primary" onclick="closeModal()">Done</button>
  `, 'medium');
}

// ══════════════════════════════════════════════════════════
// CALENDAR
// ══════════════════════════════════════════════════════════
async function renderCalendar() {
  // Load all data in parallel
  const [users, courses, classrooms, schedules, phaseData, priorityData] = await Promise.all([
    API.getUsers(),
    API.getCourses(),
    API.getClassrooms(),
    API.getAllSchedules(),
    API.getPhase(),
    API.getPhasePriorities(),
  ]);

  // Department admins only see instructors who have courses in their dept for this phase
  const isSuperAdmin = state.role === 'SUPER_ADMIN';
  const adminDept = state.department || null;

  const phase = phaseData.phase || 'PHASE_1';
  const priorities = priorityData.phasePriorities || [];
  const phaseIdx = parseInt(phase.replace('PHASE_','')) - 1;
  const currentPriority = priorities[phaseIdx] ?? null;
  const totalPhases = priorities.length;
  const isLastPhase = phaseIdx >= totalPhases - 1;

  const allInstructors = users.filter(u => u.role === 'INSTRUCTOR');
  const instructors = isSuperAdmin
    ? allInstructors
    : allInstructors.filter(instr =>
        courses.some(c =>
          c.lecturerUsername === instr.username &&
          c.department === adminDept &&
          (currentPriority === null || c.priority === currentPriority)
        )
      );

  state.calClassrooms = classrooms;
  state.calOtherSchedules = schedules;
  state._calAllCourses = courses;
  state._calCurrentPriority = currentPriority;
  state._calIsSuperAdmin = isSuperAdmin;
  state._calAdminDept = adminDept;

  // Reset selection state when re-rendering
  state.calSelectedCourse = null;
  state.calMode = null;
  state.xaiResults = null;

  const selInstructor = state.calInstructor && instructors.find(i => i.username === state.calInstructor)
    ? state.calInstructor : (instructors[0]?.username || null);
  state.calInstructor = selInstructor;

  if (selInstructor) {
    const mySchedule = schedules.find(s => s.instructorUsername === selInstructor);
    state.calDraft = mySchedule ? JSON.parse(JSON.stringify(mySchedule.slots || {})) : {};
    const avail = await API.getAvailabilities().then(arr => arr.find(a => a.instructorUsername === selInstructor));
    state.calAvailability = avail?.slots || {};
  } else {
    state.calDraft = {};
    state.calAvailability = {};
  }

  // Phase courses for selected instructor — dept admins only see their dept's courses
  const instrCourses = selInstructor
    ? courses.filter(c => c.lecturerUsername === selInstructor &&
        (currentPriority === null || c.priority === currentPriority) &&
        (isSuperAdmin || c.department === adminDept))
    : [];

  const phaseColor = isLastPhase ? 'complete' : 'active';
  const phaseLabel = isLastPhase
    ? '✓ All phases complete'
    : `Phase ${phaseIdx+1} of ${totalPhases} — Priority ${currentPriority}`;

  setContent(`
    <!-- Phase Banner -->
    <div class="phase-banner ${phaseColor}">
      <div>
        <span class="phase-label ${phaseColor}">${phaseLabel}</span>
        ${currentPriority !== null ? `<span class="text-muted text-sm" style="margin-left:8px">Showing priority ${currentPriority} courses</span>` : ''}
      </div>
      <div style="display:flex;gap:8px">
        ${isSuperAdmin && !isLastPhase && totalPhases > 0 ? `
          <button class="btn btn-sm btn-primary" onclick="advancePhase(${phaseIdx+2}, ${priorities[phaseIdx+1] ?? 0})">
            Phase ${phaseIdx+2} →
          </button>` : ''}
        <button class="btn btn-sm btn-ghost" onclick="renderCalendar()">↻ Refresh</button>
      </div>
    </div>

    <!-- Controls -->
    <div style="display:flex;gap:12px;align-items:flex-start;margin-bottom:16px;flex-wrap:wrap">
      <!-- Instructor dropdown -->
      <div style="min-width:200px">
        <label class="text-sm" style="font-weight:600;display:block;margin-bottom:4px">Instructor</label>
        <select class="filter-select" id="cal-instructor" style="width:100%" onchange="calChangeInstructor(this.value)">
          ${instructors.map(i => `<option value="${esc(i.username)}" ${i.username===selInstructor?'selected':''}>${esc(i.fullName || i.username)}</option>`).join('')}
        </select>
      </div>

      <!-- Course selector -->
      <div style="flex:1;min-width:300px">
        <label class="text-sm" style="font-weight:600;display:block;margin-bottom:4px">
          Select Course ${currentPriority !== null ? `(Priority ${currentPriority})` : ''}
        </label>
        <div class="course-selector" id="course-selector">
          ${instrCourses.length === 0
            ? '<span class="text-muted text-sm">No courses for this phase</span>'
            : instrCourses.sort((a,b) => b.priority - a.priority || b.studentCount - a.studentCount).map(c => `
              <div class="course-pill" data-code="${esc(c.code)}" onclick="calSelectCourse(this, '${esc(c.code)}')">
                ${esc(c.code)}
                ${c.lecture_assigned ? '<small style="color:var(--success)">✓L</small>' : ''}
                ${c.lab_assigned === true ? '<small style="color:var(--success)">✓B</small>' : ''}
              </div>`).join('')}
        </div>
      </div>
    </div>

    <!-- Mode + XAI row (hidden until course selected) -->
    <div id="cal-mode-row" style="display:none;margin-bottom:16px">
      <div class="selected-course-info" id="cal-course-info"></div>
      <div style="display:flex;align-items:center;gap:8px;flex-wrap:wrap">
        <span class="text-sm" style="font-weight:600">Mode:</span>
        <button class="mode-btn" id="mode-lec" onclick="calSetMode('lecture')">Lecture</button>
        <button class="mode-btn lab" id="mode-lab" onclick="calSetMode('lab')" style="display:none">Lab</button>
        <div id="xai-row" style="display:none;margin-left:auto">
          <select class="filter-select" id="xai-classroom" style="width:160px;font-size:12px">
            <option value="">Any room</option>
            ${classrooms.map(c => `<option value="${esc(c.id)}">${esc(c.roomCode)} (${c.capacity})</option>`).join('')}
          </select>
          <button class="btn btn-sm btn-primary" style="background:#7c3aed" onclick="requestXAI()">✨ Suggest Slot</button>
        </div>
      </div>
    </div>

    <!-- Schedule Grid -->
    <div class="card" style="margin-bottom:16px">
      <div class="card-header">
        <span class="card-title">Schedule — ${selInstructor ? esc(instructors.find(i=>i.username===selInstructor)?.fullName || selInstructor) : 'No instructor selected'}</span>
        <span class="text-muted text-sm">Click occupied cell to clear • Click empty cell to assign (select course + mode first)</span>
      </div>
      <div class="card-body" style="padding:0">
        <div class="grid-scroll" id="cal-grid-wrap">
          ${buildCalGrid(state.calDraft, state.calAvailability)}
        </div>
      </div>
    </div>

    <!-- Save Button -->
    <div style="display:flex;gap:10px;align-items:center">
      <button class="btn btn-primary" onclick="saveCalendar()">💾 Save Schedule</button>
      <button class="btn btn-secondary" onclick="calReloadDraft()">↺ Discard Changes</button>
      <button class="btn btn-ghost" onclick="openNotifyModal('${esc(selInstructor || '')}')">🔔 Send Notification</button>
    </div>
  `);
}

function buildCalGrid(draft, availability) {
  const availSet = new Set();
  Object.entries(availability || {}).forEach(([day, slots]) =>
    (slots || []).forEach(s => availSet.add(`${day}_${s}`)));

  let html = `<table class="schedule-table"><thead><tr>
    <th class="time-col">Time</th>`;
  DAYS.forEach(d => html += `<th>${d}</th>`);
  html += `</tr></thead><tbody>`;

  TIME_SLOTS.forEach(slot => {
    html += `<tr>`;
    html += `<td class="time-col">${slot}</td>`;
    DAYS.forEach(day => {
      const key = `${day}_${slot}`;
      const course = draft[key];
      const isAvail = availSet.has(key);
      if (course && course.duration === -1) {
        html += `<td class="grid-cell continuation" title="Continuation">`
              + `<div class="course-chip" style="opacity:.55">`
              + `<div style="font-size:9px">↑ ${esc(course.code)}</div>`
              + `<div class="room">${esc(course.classroomId || '')}</div>`
              + `</div></td>`;
      } else if (course) {
        html += `<td class="grid-cell occupied" onclick="calCellClick('${esc(day)}','${esc(slot)}')" title="Click to clear">`
              + `<div class="course-chip">`
              + `<div>${esc(course.code)}</div>`
              + `<div class="room">${esc(course.classroomId || '')}</div>`
              + `${course.isLab ? '<div class="label">LAB</div>' : ''}`
              + `</div></td>`;
      } else {
        const cellClass = isAvail ? 'grid-cell target' : 'grid-cell empty';
        html += `<td class="${cellClass}" onclick="calCellClick('${esc(day)}','${esc(slot)}')" title="${isAvail ? 'Available' : ''}"></td>`;
      }
    });
    html += `</tr>`;
  });
  html += `</tbody></table>`;
  return html;
}

function refreshCalGrid() {
  const wrap = document.getElementById('cal-grid-wrap');
  if (wrap) wrap.innerHTML = buildCalGrid(state.calDraft, state.calAvailability);
}

async function calChangeInstructor(username) {
  state.calInstructor = username;
  state.calSelectedCourse = null;
  state.calMode = null;
  const scheduleEntry = state.calOtherSchedules.find(s => s.instructorUsername === username);
  state.calDraft = scheduleEntry ? JSON.parse(JSON.stringify(scheduleEntry.slots || {})) : {};
  const availArr = await API.getAvailabilities();
  const avail = availArr.find(a => a.instructorUsername === username);
  state.calAvailability = avail?.slots || {};

  // Update course pills for newly selected instructor
  const allCourses = state._calAllCourses || [];
  const curPriority = state._calCurrentPriority;
  const isSA = state._calIsSuperAdmin;
  const adminDept = state._calAdminDept;
  const instrCourses = allCourses.filter(c =>
    c.lecturerUsername === username &&
    (curPriority === null || c.priority === curPriority) &&
    (isSA || c.department === adminDept)
  );
  const selector = document.getElementById('course-selector');
  if (selector) {
    selector.innerHTML = instrCourses.length === 0
      ? '<span class="text-muted text-sm">No courses for this phase</span>'
      : instrCourses.sort((a,b) => b.priority - a.priority || b.studentCount - a.studentCount).map(c => `
          <div class="course-pill" data-code="${esc(c.code)}" onclick="calSelectCourse(this, '${esc(c.code)}')">
            ${esc(c.code)}
            ${c.lecture_assigned ? '<small style="color:var(--success)">✓L</small>' : ''}
            ${c.lab_assigned === true ? '<small style="color:var(--success)">✓B</small>' : ''}
          </div>`).join('');
  }

  refreshCalGrid();
  document.getElementById('cal-mode-row').style.display = 'none';
}

function calSelectCourse(el, code) {
  const courses = state._courses || (window._calCourses || []);
  const allCourses = courses.length ? courses : [];
  state.calSelectedCourse = allCourses.find(c => c.code === code) || { code };
  state.calMode = null;

  document.querySelectorAll('.course-pill').forEach(p => p.classList.remove('selected'));
  el.classList.add('selected');

  const modeRow = document.getElementById('cal-mode-row');
  const courseInfo = document.getElementById('cal-course-info');
  const labBtn = document.getElementById('mode-lab');
  const xaiRow = document.getElementById('xai-row');

  modeRow.style.display = 'block';
  const c = state.calSelectedCourse;
  courseInfo.innerHTML = `
    <div class="code">${esc(c.code)} — ${esc(c.name || '')}</div>
    <div class="meta">Sem ${c.semester || '?'} · ${c.studentCount || 0} students ·
    Lec ${c.lectureHours || 0}h · Lab ${c.labHours || 0}h · Priority ${c.priority || 1}</div>`;

  labBtn.style.display = (c.labHours && c.labHours > 0) ? '' : 'none';
  document.getElementById('mode-lec').classList.remove('selected');
  labBtn.classList.remove('selected');
  xaiRow.style.display = 'none';
}

function calSetMode(mode) {
  state.calMode = mode;
  document.getElementById('mode-lec').classList.toggle('selected', mode === 'lecture');
  document.getElementById('mode-lab').classList.toggle('selected', mode === 'lab');
  document.getElementById('xai-row').style.display = 'flex';
  document.getElementById('xai-row').style.alignItems = 'center';
  document.getElementById('xai-row').style.gap = '8px';
  toast(`Mode: ${mode} — click a grid cell to assign`, 'info');
}

function calCellClick(day, slot) {
  const key = `${day}_${slot}`;
  const existing = state.calDraft[key];

  if (existing && existing.duration !== -1) {
    // Clear this course's slots
    const codeToRemove = existing.code;
    let removed = 0;
    for (const k of Object.keys(state.calDraft)) {
      const v = state.calDraft[k];
      if (v && v.code === codeToRemove && k.startsWith(day + '_')) {
        delete state.calDraft[k]; removed++;
      }
    }
    refreshCalGrid();
    toast(`Cleared ${codeToRemove} from ${day}`, 'info');
    return;
  }
  if (existing && existing.duration === -1) return; // continuation - do nothing

  if (!state.calSelectedCourse) { toast('Select a course first', 'warning'); return; }
  if (!state.calMode) { toast('Select Lecture or Lab mode', 'warning'); return; }

  openAssignDialog(day, slot);
}

function openAssignDialog(day, slot) {
  const c = state.calSelectedCourse;
  const mode = state.calMode;
  const duration = mode === 'lab' && c.labHours > 0 ? c.labHours : (c.lectureHours > 0 ? c.lectureHours : 1);
  const slotIdx = TIME_SLOTS.indexOf(slot);

  if (slotIdx + duration > TIME_SLOTS.length) {
    toast(`Not enough slots for ${duration}h starting at ${slot}`, 'error'); return;
  }

  const neededSlots = TIME_SLOTS.slice(slotIdx, slotIdx + duration);
  const conflicts = neededSlots.filter(s => state.calDraft[`${day}_${s}`]);
  if (conflicts.length > 0) {
    toast('Slot occupied. Clear it first.', 'error'); return;
  }

  // Build classroom options (filter by capacity)
  const eligibleRooms = state.calClassrooms.filter(r =>
    !c.studentCount || r.capacity >= c.studentCount || r.roomCode.toUpperCase() === 'ONLINE'
  );

  // Find occupied rooms (other instructors' saved schedules)
  const occupiedRooms = new Set();
  state.calOtherSchedules.forEach(sched => {
    if (sched.instructorUsername === state.calInstructor) return;
    neededSlots.forEach(s => {
      const entry = (sched.slots || {})[`${day}_${s}`];
      if (entry && entry.classroomId) occupiedRooms.add(entry.classroomId);
    });
  });

  const roomOptions = eligibleRooms.map(r => {
    const isOccupied = occupiedRooms.has(r.id) && r.roomCode.toUpperCase() !== 'ONLINE';
    return `<option value="${esc(r.id)}" ${isOccupied ? 'disabled' : ''}>${esc(r.roomCode)} (cap: ${r.capacity})${isOccupied ? ' — occupied' : ''}</option>`;
  }).join('');

  openModal(`Assign ${mode === 'lab' ? 'Lab' : 'Lecture'}: ${c.code}`, `
    <div class="selected-course-info">
      <div class="code">${esc(c.code)} — ${esc(c.name || '')}</div>
      <div class="meta">${day} · ${slot} · ${duration}h block · ${mode}</div>
    </div>
    <div class="form-group">
      <label>Classroom</label>
      <select id="assign-room">
        <option value="">— Select classroom —</option>
        ${roomOptions}
      </select>
    </div>
  `, `
    <button class="btn btn-secondary" onclick="closeModal()">Cancel</button>
    <button class="btn btn-primary" onclick="confirmAssign('${esc(day)}','${esc(slot)}',${duration})">Assign</button>
  `);
}

function confirmAssign(day, slot, duration) {
  const roomId = document.getElementById('assign-room').value;
  if (!roomId) { toast('Select a classroom', 'error'); return; }
  const room = state.calClassrooms.find(r => r.id === roomId);
  const c = state.calSelectedCourse;
  const slotIdx = TIME_SLOTS.indexOf(slot);

  // Main slot
  state.calDraft[`${day}_${slot}`] = {
    code: c.code, name: c.name || c.code, lecturerUsername: state.calInstructor,
    department: c.department || '', semester: c.semester || 1,
    studentCount: c.studentCount || 0, priority: c.priority || 1,
    lectureHours: c.lectureHours || 0, labHours: c.labHours || 0,
    classroomId: room?.roomCode || roomId, duration,
    isLab: state.calMode === 'lab',
  };
  // Continuation slots
  for (let i = 1; i < duration; i++) {
    const contSlot = TIME_SLOTS[slotIdx + i];
    state.calDraft[`${day}_${contSlot}`] = {
      ...state.calDraft[`${day}_${slot}`], duration: -1
    };
  }

  // Auto-switch mode: if lecture assigned and lab exists, switch to lab mode
  if (state.calMode === 'lecture' && c.labHours > 0) {
    state.calMode = 'lab';
    document.getElementById('mode-lec')?.classList.remove('selected');
    document.getElementById('mode-lab')?.classList.add('selected');
  } else if (state.calMode === 'lab') {
    state.calMode = null;
    document.getElementById('mode-lec')?.classList.remove('selected');
    document.getElementById('mode-lab')?.classList.remove('selected');
  }

  closeModal();
  refreshCalGrid();
  toast(`Assigned ${c.code} to ${day} ${slot}`, 'success');
}

async function calReloadDraft() {
  if (!state.calInstructor) return;
  const sched = state.calOtherSchedules.find(s => s.instructorUsername === state.calInstructor);
  state.calDraft = sched ? JSON.parse(JSON.stringify(sched.slots || {})) : {};
  refreshCalGrid();
  toast('Draft reset', 'info');
}

async function saveCalendar() {
  if (!state.calInstructor) { toast('No instructor selected', 'error'); return; }

  // Convert draft to API format
  const slots = {};
  for (const [key, val] of Object.entries(state.calDraft)) {
    if (!val) continue;
    slots[key] = {
      code: val.code, name: val.name, lecturerUsername: val.lecturerUsername || state.calInstructor,
      department: val.department || '', email: '', duration: val.duration || 1,
      classroomId: val.classroomId || null, semester: val.semester || 1,
      studentCount: val.studentCount || 0, priority: val.priority || 1,
      lectureHours: val.lectureHours || 0, labHours: val.labHours || 0,
    };
  }

  try {
    await API.saveSchedule(state.calInstructor, slots);
    toast('Schedule saved!', 'success');
    // Refresh other schedules cache
    const schedules = await API.getAllSchedules();
    state.calOtherSchedules = schedules;
  } catch (e) { toast(e.message, 'error'); }
}

async function advancePhase(nextPhaseNum, nextPriority) {
  const ok = await confirmModal(
    'Advance Phase',
    `Move to <strong>PHASE_${nextPhaseNum}</strong> (Priority ${nextPriority})? <br><small>Current phase assignments will be locked.</small>`,
    'Advance'
  );
  if (!ok) return;
  try {
    await API.setPhase(`PHASE_${nextPhaseNum}`);
    toast(`Advanced to Phase ${nextPhaseNum}`, 'success');
    await renderCalendar();
  } catch (e) { toast(e.message, 'error'); }
}

function openNotifyModal(username) {
  openModal('Send Notification', `
    <div class="form-group">
      <label>Recipient Username</label>
      <input id="notif-user" value="${esc(username)}" placeholder="Username">
    </div>
    <div class="form-group">
      <label>Message</label>
      <textarea id="notif-text" rows="3" placeholder="Notification message…"></textarea>
    </div>
  `, `
    <button class="btn btn-secondary" onclick="closeModal()">Cancel</button>
    <button class="btn btn-primary" onclick="sendNotification()">Send</button>
  `);
}

async function sendNotification() {
  const recipientUsername = document.getElementById('notif-user').value.trim();
  const text = document.getElementById('notif-text').value.trim();
  if (!recipientUsername || !text) { toast('Fill in all fields', 'error'); return; }
  try {
    await API.sendNotification({ recipientUsername, text, isRead: false });
    closeModal();
    toast('Notification sent', 'success');
  } catch (e) { toast(e.message, 'error'); }
}

// XAI Single Slot
async function requestXAI() {
  const c = state.calSelectedCourse;
  const username = state.calInstructor;
  if (!c || !username || !state.calMode) { toast('Select instructor, course and mode first', 'warning'); return; }

  const classroomId = document.getElementById('xai-classroom')?.value || null;
  const btn = document.querySelector('[onclick="requestXAI()"]');
  if (btn) { btn.disabled = true; btn.textContent = '⏳ Analyzing…'; }

  try {
    const duration = state.calMode === 'lab' ? (c.labHours || 1) : (c.lectureHours || 1);
    const result = await API.suggestSlot(username, {
      courseCode: c.code, duration,
      lectureHours: c.lectureHours || 0, labHours: c.labHours || 0,
      suggestionType: state.calMode,
      classroomId: classroomId || null,
    });
    state.xaiResults = result;
    state.xaiExpandedIdx = -1;
    showXAIModal(result);
  } catch (e) { toast(e.message, 'error'); }
  finally {
    if (btn) { btn.disabled = false; btn.textContent = '✨ Suggest Slot'; }
  }
}

function showXAIModal(result) {
  if (!result?.suggestions?.length) {
    toast('No valid slots found', 'warning'); return;
  }

  const title = `XAI Suggestion — ${result.suggestionType === 'lab' ? 'Lab' : 'Lecture'}: ${result.courseCode}`;
  const body = `
    <p class="text-muted text-sm" style="margin-bottom:12px">${esc(result.algorithmNote)}</p>
    <div id="xai-accordion">
      ${result.suggestions.slice(0,10).map((s, i) => buildXAIOption(s, i)).join('')}
    </div>`;

  openModal(title, body, '', 'lg');
}

function buildXAIOption(s, i) {
  const sc = scoreClass(s.score);
  const pct = scorePct(s.score);
  return `
    <div class="xai-option">
      <div class="xai-option-header" onclick="toggleXAI(${i})">
        <div>
          <strong>Option ${i+1}:</strong> ${esc(s.day)} · ${esc(s.timeSlot)}
          · <span style="color:var(--text-muted)">${esc(s.classroomCode || 'N/A')}</span>
        </div>
        <div style="display:flex;align-items:center;gap:8px">
          <span class="xai-score ${sc}">${pct}</span>
          <span style="font-size:12px;color:var(--text-muted)" id="xai-arrow-${i}">▼</span>
        </div>
      </div>
      <div class="xai-option-body" id="xai-body-${i}">
        <button class="btn btn-primary btn-sm" style="margin-bottom:12px"
                onclick="applyXAI(${i})">Apply Option ${i+1}</button>
        <div class="score-bar-wrap">
          <div style="display:flex;justify-content:space-between;font-size:11px;margin-bottom:3px">
            <span>Score</span><span class="xai-score ${sc}">${pct}</span>
          </div>
          <div class="score-bar"><div class="score-bar-fill ${sc}" style="width:${pct}"></div></div>
        </div>
        <div class="dt-path">
          ${(s.path || []).map(n => {
            const icon = n.result === 'pass' ? '✅' : n.result === 'fail_hard' ? '❌'
                       : n.result === 'partial' ? '⚠️' : n.result === 'info' ? 'ℹ️' : '·';
            const badge = n.isHard ? '<span class="dt-badge hard">[required]</span>'
                        : n.scoreContribution < 0 ? `<span class="dt-badge soft">[${(n.scoreContribution*100).toFixed(0)}%]</span>` : '';
            return `<div class="dt-node">
              <span class="dt-icon">${icon}</span>
              <div>
                <span class="dt-label">${esc(n.label)}</span> ${badge}
                <div class="dt-desc">${esc(n.description)}</div>
              </div>
            </div>`;
          }).join('')}
        </div>
        <div style="background:#f8fafc;border-radius:6px;padding:10px;font-size:12px;margin-top:8px">
          ${esc(s.summary)}
        </div>
      </div>
    </div>`;
}

function toggleXAI(idx) {
  const body = document.getElementById(`xai-body-${idx}`);
  const arrow = document.getElementById(`xai-arrow-${idx}`);
  const isOpen = body?.classList.contains('open');
  // Close all
  document.querySelectorAll('.xai-option-body').forEach(b => b.classList.remove('open'));
  document.querySelectorAll('[id^="xai-arrow-"]').forEach(a => a.textContent = '▼');
  if (!isOpen) { body?.classList.add('open'); if (arrow) arrow.textContent = '▲'; }
}

function applyXAI(idx) {
  const s = state.xaiResults?.suggestions?.[idx];
  if (!s || !state.calSelectedCourse) return;

  const c = state.calSelectedCourse;
  const duration = state.calMode === 'lab' ? (c.labHours || 1) : (c.lectureHours || 1);
  const slotIdx = TIME_SLOTS.indexOf(s.timeSlot);

  // Check for conflicts
  const neededSlots = TIME_SLOTS.slice(slotIdx, slotIdx + duration);
  const conflicts = neededSlots.filter(sl => state.calDraft[`${s.day}_${sl}`]);
  if (conflicts.length > 0) { toast('Slot occupied in draft. Clear first.', 'error'); return; }

  state.calDraft[`${s.day}_${s.timeSlot}`] = {
    code: c.code, name: c.name || c.code, lecturerUsername: state.calInstructor,
    department: c.department || '', semester: c.semester || 1,
    studentCount: c.studentCount || 0, priority: c.priority || 1,
    lectureHours: c.lectureHours || 0, labHours: c.labHours || 0,
    classroomId: s.classroomCode || s.classroomId, duration,
    isLab: state.calMode === 'lab',
  };
  for (let i = 1; i < duration; i++) {
    const contSlot = TIME_SLOTS[slotIdx + i];
    state.calDraft[`${s.day}_${contSlot}`] = { ...state.calDraft[`${s.day}_${s.timeSlot}`], duration: -1 };
  }

  if (state.calMode === 'lecture' && c.labHours > 0) {
    state.calMode = 'lab';
    document.getElementById('mode-lec')?.classList.remove('selected');
    document.getElementById('mode-lab')?.classList.add('selected');
  }

  closeModal();
  refreshCalGrid();
  toast(`Applied: ${c.code} → ${s.day} ${s.timeSlot}`, 'success');
}

// ══════════════════════════════════════════════════════════
// AVAILABILITY
// ══════════════════════════════════════════════════════════
async function renderAvailability() {
  const [users, avails] = await Promise.all([API.getUsers(), API.getAvailabilities()]);
  const instructors = users.filter(u => u.role === 'INSTRUCTOR');

  const selUser = state._availInstructor || instructors[0]?.username || null;
  state._availInstructor = selUser;

  const avail = avails.find(a => a.instructorUsername === selUser);
  const slots = avail?.slots || {};

  const availSet = new Set();
  Object.entries(slots).forEach(([day, s]) => (s || []).forEach(sl => availSet.add(`${day}_${sl}`)));

  // Count available slots
  let availCount = 0; let totalSlots = DAYS.length * TIME_SLOTS.length;
  DAYS.forEach(d => TIME_SLOTS.forEach(s => { if (availSet.has(`${d}_${s}`)) availCount++; }));

  setContent(`
    <div style="display:flex;gap:12px;align-items:center;margin-bottom:16px;flex-wrap:wrap">
      <div>
        <label class="text-sm" style="font-weight:600;display:block;margin-bottom:4px">Instructor</label>
        <select class="filter-select" onchange="changeAvailInstructor(this.value)">
          ${instructors.map(i => `<option value="${esc(i.username)}" ${i.username===selUser?'selected':''}>${esc(i.fullName || i.username)}</option>`).join('')}
        </select>
      </div>
      <div class="text-muted text-sm" style="margin-top:20px">
        ${selUser ? `${availCount} / ${totalSlots} slots available` : ''}
      </div>
      <button class="btn btn-sm btn-ghost" style="margin-top:20px" onclick="renderAvailability()">↻ Refresh</button>
    </div>

    <div class="card">
      <div class="card-header">
        <span class="card-title">Availability — ${selUser ? esc(instructors.find(i=>i.username===selUser)?.fullName || selUser) : '—'}</span>
        <span class="text-muted text-sm">Green = Available</span>
      </div>
      <div class="card-body" style="padding:0">
        <div style="overflow-x:auto">
          <table class="schedule-table" style="min-width:600px">
            <thead><tr>
              <th class="time-col">Time</th>
              ${DAYS.map(d => `<th>${d}</th>`).join('')}
            </tr></thead>
            <tbody>
              ${TIME_SLOTS.map(slot => `
                <tr>
                  <td class="time-col">${slot}</td>
                  ${DAYS.map(day => {
                    const yes = availSet.has(`${day}_${slot}`);
                    return `<td class="avail-cell ${yes ? 'yes' : 'no'}" style="text-align:center">
                      ${yes ? '<span class="avail-dot yes" title="Available"></span>' : ''}
                    </td>`;
                  }).join('')}
                </tr>`).join('')}
            </tbody>
          </table>
        </div>
      </div>
    </div>

    ${!avail ? '<div class="warn-box" style="margin-top:12px">This instructor has not submitted availability yet.</div>' : ''}
  `);
}

async function changeAvailInstructor(username) {
  state._availInstructor = username;
  await renderAvailability();
}

// ══════════════════════════════════════════════════════════
// WEEKLY SCHEDULE
// ══════════════════════════════════════════════════════════
async function renderWeekly() {
  const [phaseData, priorityData, schedules, courses, users, classrooms] = await Promise.all([
    API.getPhase(), API.getPhasePriorities(), API.getAllSchedules(), API.getCourses(), API.getUsers(), API.getClassrooms()
  ]);

  const phase = phaseData.phase || 'PHASE_1';
  const priorities = priorityData.phasePriorities || [];

  const instructorMap = {};
  users.forEach(u => { instructorMap[u.username] = u.fullName || u.username; });

  const classroomMap = {};
  (classrooms || []).forEach(c => { classroomMap[c.id] = c.roomCode; });

  state._weeklySchedules = schedules;
  state._weeklyCourses = courses;
  state._weeklyInstructorMap = instructorMap;
  state._weeklyClassroomMap = classroomMap;

  const depts = [...new Set((courses || []).map(c => c.department).filter(Boolean))].sort();
  const semesters = [...new Set((courses || []).map(c => c.semester).filter(s => s != null))].sort((a, b) => a - b);

  setContent(`
    <div style="display:flex;gap:10px;align-items:center;flex-wrap:wrap;margin-bottom:16px">
      <div>
        <label class="text-sm" style="font-weight:600;display:block;margin-bottom:4px">Phase for XAI</label>
        <select class="filter-select" id="weekly-phase">
          ${priorities.map((p, i) => {
            const ph = `PHASE_${i+1}`;
            return `<option value="${ph}" ${ph===phase?'selected':''}>${ph} (Priority ${p})</option>`;
          }).join('')}
        </select>
      </div>
      <button class="btn btn-primary" style="background:#7c3aed;margin-top:20px" onclick="generateWeekly()">
        ✨ Generate XAI Suggestions
      </button>
      <button class="btn btn-ghost" style="margin-top:20px" onclick="renderWeekly()">↻ Refresh</button>
    </div>

    <div style="display:flex;gap:8px;margin-bottom:12px">
      <button id="weekly-btn-phase" class="btn btn-primary" onclick="weeklySetView('phase')" style="font-size:13px;padding:6px 14px">Phase View</button>
      <button id="weekly-btn-dept" class="btn btn-ghost" onclick="weeklySetView('dept')" style="font-size:13px;padding:6px 14px">Dept / Semester View</button>
    </div>

    <!-- Phase View -->
    <div id="weekly-phase-view" class="card" style="margin-bottom:16px">
      <div class="card-header">
        <span class="card-title">Current Schedule Overview</span>
        <span class="text-muted text-sm">${schedules.length} instructors with schedules</span>
      </div>
      <div class="card-body" style="padding:0">
        ${buildCurrentScheduleGrid(schedules, instructorMap, classroomMap)}
      </div>
    </div>

    <!-- Dept / Semester View -->
    <div id="weekly-dept-view" style="display:none;margin-bottom:16px">
      <div style="display:flex;gap:10px;align-items:flex-end;margin-bottom:12px;flex-wrap:wrap">
        <div>
          <label class="text-sm" style="font-weight:600;display:block;margin-bottom:4px">Department</label>
          <select class="filter-select" id="weekly-dept-select" onchange="weeklyFilterGrid()">
            <option value="">All Departments</option>
            ${depts.map(d => `<option value="${esc(d)}">${esc(d)}</option>`).join('')}
          </select>
        </div>
        <div>
          <label class="text-sm" style="font-weight:600;display:block;margin-bottom:4px">Semester</label>
          <select class="filter-select" id="weekly-sem-select" onchange="weeklyFilterGrid()">
            <option value="">All Semesters</option>
            ${semesters.map(s => `<option value="${s}">Semester ${s}</option>`).join('')}
          </select>
        </div>
      </div>
      <div id="weekly-filtered-grid"></div>
    </div>

    <!-- XAI Results (loaded on demand) -->
    <div id="weekly-results"></div>
  `);
}

function buildCurrentScheduleGrid(schedules, instructorMap, classroomMap) {
  classroomMap = classroomMap || {};
  const grid = {};
  schedules.forEach(s => {
    Object.entries(s.slots || {}).forEach(([key, c]) => {
      if (!c) return;
      const isCont = c.duration === -1;
      if (!grid[key]) grid[key] = [];
      const roomCode = (c.classroomId && classroomMap[c.classroomId]) || c.classroomId || '';
      grid[key].push({
        code: c.code,
        instructor: instructorMap[s.instructorUsername] || s.instructorUsername,
        dept: c.department || '',
        room: roomCode,
        cont: isCont
      });
    });
  });

  let html = `<div style="overflow-x:auto"><table class="schedule-table" style="min-width:700px">
    <thead><tr><th class="time-col">Time</th>${DAYS.map(d=>`<th>${d}</th>`).join('')}</tr></thead>
    <tbody>`;

  TIME_SLOTS.forEach(slot => {
    html += `<tr><td class="time-col">${slot}</td>`;
    DAYS.forEach(day => {
      const key = `${day}_${slot}`;
      const items = grid[key] || [];
      if (items.length === 0) {
        html += `<td class="grid-cell" style="background:#fafafa"></td>`;
      } else {
        const chips = items.map(it => {
          const color = deptColor(it.dept);
          return `<div style="font-size:9px;background:${color}18;border-left:2px solid ${color};padding:1px 4px;margin-bottom:1px;border-radius:2px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;opacity:${it.cont ? 0.55 : 1}">
            <strong style="color:${color}">${esc(it.code)}</strong>
            ${it.room ? `<span style="color:var(--text-muted)"> ${esc(it.room)}</span>` : ''}
          </div>`;
        }).join('');
        html += `<td class="grid-cell occupied" style="padding:3px;vertical-align:top">${chips}</td>`;
      }
    });
    html += `</tr>`;
  });

  html += `</tbody></table></div>`;
  return html;
}

async function generateWeekly() {
  const phase = document.getElementById('weekly-phase')?.value || 'PHASE_1';
  const btn = document.querySelector('[onclick="generateWeekly()"]');
  if (btn) { btn.disabled = true; btn.textContent = '⏳ Generating…'; }

  const resultsDiv = document.getElementById('weekly-results');
  resultsDiv.innerHTML = '<div class="loading-spinner"><div class="spinner"></div></div>';

  try {
    const result = await API.suggestWeekly(phase);
    state.weeklyResults = result;
    state.weeklyExpandedIdx = -1;
    renderWeeklyResults(result);
  } catch (e) {
    resultsDiv.innerHTML = `<div class="warn-box">Error: ${esc(e.message)}</div>`;
  } finally {
    if (btn) { btn.disabled = false; btn.textContent = '✨ Generate XAI Suggestions'; }
  }
}

function renderWeeklyResults(result) {
  const div = document.getElementById('weekly-results');
  if (!result?.suggestions?.length) { div.innerHTML = '<div class="warn-box">No suggestions generated.</div>'; return; }

  const totalCourses = result.suggestions[0] ?
    result.suggestions[0].assignments.length + result.suggestions[0].unassignedCourses.length : 0;

  div.innerHTML = `
    <div class="section-header">
      <span class="card-title">XAI Weekly Suggestions — ${esc(result.phase)}</span>
      <span class="text-muted text-sm">${result.suggestions.length} strategies</span>
    </div>
    <p class="hint" style="margin-bottom:12px">${esc(result.algorithmNote)}</p>
    ${result.suggestions.map((s, i) => {
      const coverage = totalCourses > 0 ? Math.round(s.assignments.length / totalCourses * 100) : 0;
      return `
        <div class="weekly-option">
          <div class="weekly-option-header" onclick="toggleWeekly(${i})">
            <div>
              <div class="weekly-option-title">${esc(s.title)}</div>
              <div class="text-muted text-sm">${esc(s.description)}</div>
            </div>
            <div class="weekly-option-meta">
              <span class="badge badge-success">✓ ${s.assignments.length} assigned</span>
              ${s.unassignedCourses.length > 0 ? `<span class="badge badge-warning">${s.unassignedCourses.length} unassigned</span>` : ''}
              <span class="badge badge-info">${coverage}% coverage</span>
              <span id="weekly-arrow-${i}" style="font-size:12px;color:var(--text-muted)">▼</span>
            </div>
          </div>
          <div class="weekly-option-body" id="weekly-body-${i}">
            ${buildWeeklyMiniGrid(s.assignments)}
            ${s.unassignedCourses.length > 0 ? `
              <div style="padding:12px 16px;border-top:1px solid var(--border)">
                <p class="text-sm" style="font-weight:600;margin-bottom:8px">Unassigned (${s.unassignedCourses.length}):</p>
                ${s.unassignedCourses.map(c => `<span class="badge badge-warning" style="margin:2px">${esc(c)}</span>`).join('')}
              </div>` : ''}
          </div>
        </div>`;
    }).join('')}`;
}

function buildWeeklyMiniGrid(assignments) {
  // Build grid from assignments
  const grid = {};
  assignments.forEach(a => {
    const slotIdx = TIME_SLOTS.indexOf(a.timeSlot);
    for (let i = 0; i < (a.duration || 1); i++) {
      const s = TIME_SLOTS[slotIdx + i];
      if (!s) continue;
      const key = `${a.day}_${s}`;
      if (!grid[key]) grid[key] = [];
      if (i === 0) grid[key].push({ code: a.courseCode, room: a.classroomCode,
                                     dept: a.department, isLab: a.isLab });
      else grid[key].push({ code: `↑${a.courseCode}`, room: '', dept: a.department, isLab: a.isLab, cont: true });
    }
  });

  let html = `<div style="overflow-x:auto;padding:0 16px 16px"><table class="schedule-table" style="min-width:600px">
    <thead><tr><th class="time-col">Time</th>${DAYS.map(d=>`<th>${d}</th>`).join('')}</tr></thead>
    <tbody>`;
  TIME_SLOTS.forEach(slot => {
    html += `<tr><td class="time-col">${slot}</td>`;
    DAYS.forEach(day => {
      const key = `${day}_${slot}`;
      const items = grid[key] || [];
      if (!items.length) { html += `<td class="grid-cell" style="background:#fafafa"></td>`; }
      else {
        const chips = items.map(it => {
          const color = deptColor(it.dept);
          return `<div style="font-size:9px;background:${color}18;border-left:2px solid ${color};padding:1px 4px;margin-bottom:1px;border-radius:2px;white-space:nowrap;opacity:${it.cont?0.5:1}">
            <strong style="color:${color}">${esc(it.code)}</strong>${it.isLab?' <em style="color:#7c3aed">LAB</em>':''}
            ${it.room ? `<span style="color:var(--text-muted)"> ${esc(it.room)}</span>` : ''}
          </div>`;
        }).join('');
        html += `<td class="grid-cell occupied" style="padding:3px;vertical-align:top">${chips}</td>`;
      }
    });
    html += `</tr>`;
  });
  html += `</tbody></table></div>`;
  return html;
}

function weeklySetView(mode) {
  const phaseView = document.getElementById('weekly-phase-view');
  const deptView  = document.getElementById('weekly-dept-view');
  const phaseBtn  = document.getElementById('weekly-btn-phase');
  const deptBtn   = document.getElementById('weekly-btn-dept');
  if (mode === 'phase') {
    if (phaseView) phaseView.style.display = '';
    if (deptView)  deptView.style.display  = 'none';
    if (phaseBtn)  phaseBtn.className = 'btn btn-primary';
    if (deptBtn)   deptBtn.className  = 'btn btn-ghost';
  } else {
    if (phaseView) phaseView.style.display = 'none';
    if (deptView)  deptView.style.display  = '';
    if (phaseBtn)  phaseBtn.className = 'btn btn-ghost';
    if (deptBtn)   deptBtn.className  = 'btn btn-primary';
    weeklyFilterGrid();
  }
}

function weeklyFilterGrid() {
  const dept    = document.getElementById('weekly-dept-select')?.value || '';
  const semStr  = document.getElementById('weekly-sem-select')?.value  || '';
  const semester = semStr ? parseInt(semStr) : null;

  const schedules      = state._weeklySchedules      || [];
  const instructorMap  = state._weeklyInstructorMap  || {};
  const classroomMap   = state._weeklyClassroomMap   || {};

  const filteredSchedules = schedules.map(s => {
    const filteredSlots = Object.fromEntries(
      Object.entries(s.slots || {}).filter(([, c]) => {
        if (!c) return false;
        if (dept && c.department !== dept) return false;
        if (semester !== null && c.semester !== semester) return false;
        return true;
      })
    );
    return { ...s, slots: filteredSlots };
  }).filter(s => Object.keys(s.slots).length > 0);

  const label   = (dept || 'All Depts') + ' · ' + (semester !== null ? `Semester ${semester}` : 'All Semesters');
  const gridDiv = document.getElementById('weekly-filtered-grid');
  if (!gridDiv) return;
  gridDiv.innerHTML = `
    <div class="card">
      <div class="card-header">
        <span class="card-title">${esc(label)}</span>
        <span class="text-muted text-sm">${filteredSchedules.length} instructor(s)</span>
      </div>
      <div class="card-body" style="padding:0">
        ${filteredSchedules.length > 0
          ? buildCurrentScheduleGrid(filteredSchedules, instructorMap, classroomMap)
          : '<p class="hint" style="padding:16px">No scheduled courses match this filter.</p>'}
      </div>
    </div>`;
}

function toggleWeekly(idx) {
  const body = document.getElementById(`weekly-body-${idx}`);
  const arrow = document.getElementById(`weekly-arrow-${idx}`);
  const isOpen = body?.classList.contains('open');
  document.querySelectorAll('.weekly-option-body').forEach(b => b.classList.remove('open'));
  document.querySelectorAll('[id^="weekly-arrow-"]').forEach(a => a.textContent = '▼');
  if (!isOpen) { body?.classList.add('open'); if (arrow) arrow.textContent = '▲'; }
}

// ══════════════════════════════════════════════════════════
// AUDIT LOGS (SUPER_ADMIN only)
// ══════════════════════════════════════════════════════════
function parseBrowser(ua) {
  if (!ua) return '—';
  if (/Edg\//.test(ua))     return 'Edge';
  if (/OPR\//.test(ua))     return 'Opera';
  if (/Chrome\//.test(ua))  return 'Chrome';
  if (/Firefox\//.test(ua)) return 'Firefox';
  if (/Safari\//.test(ua))  return 'Safari';
  if (/MSIE|Trident/.test(ua)) return 'IE';
  if (/curl\//.test(ua))    return 'curl';
  if (/python/.test(ua))    return 'Python';
  return ua.split('/')[0].slice(0, 12) || '—';
}
function _logRowHtml(l) {
  return `<tr data-log-id="${l.id}">
    <td class="text-sm" style="white-space:nowrap">${esc(formatDate(l.timestamp))}</td>
    <td><strong>${esc(l.actor_username)}</strong></td>
    <td>${roleBadge(l.actor_role)}</td>
    <td><span class="log-action">${esc(l.action)}</span></td>
    <td class="text-sm">${esc(l.target || '—')}</td>
    <td><div class="log-details" title="${esc(l.details)}">${esc(l.details || '—')}</div></td>
    <td class="text-muted text-sm">${esc(l.ip_address || '—')}</td>
    <td class="text-sm" title="${esc(l.user_agent || '')}">${esc(parseBrowser(l.user_agent))}</td>
  </tr>`;
}

// ══════════════════════════════════════════════════════════
// SETTINGS
// ══════════════════════════════════════════════════════════
async function renderSettings() {
  const users = await API.getUsers();
  const user  = users.find(u => u.username === state.username) || {};

  const bg       = avatarBgColor(state.username);
  const initials = userInitials(user.fullName || state.username);
  const avatarHtml = user.avatarUrl
    ? `<img src="${esc(user.avatarUrl)}" style="width:72px;height:72px;border-radius:50%;object-fit:cover">`
    : `<div style="width:72px;height:72px;border-radius:50%;background:${bg};display:flex;align-items:center;justify-content:center;color:white;font-size:24px;font-weight:700">${esc(initials)}</div>`;

  setContent(`
    <div style="max-width:580px;display:flex;flex-direction:column;gap:16px;padding:4px 0">

      <!-- Profile card -->
      <div class="card">
        <div class="card-header"><span class="card-title">Profile</span></div>
        <div class="card-body" style="display:flex;flex-direction:column;gap:14px">

          <div style="display:flex;align-items:center;gap:18px">
            <div style="position:relative;cursor:pointer;flex-shrink:0" title="Change photo" onclick="document.getElementById('settings-avatar-input').click()">
              ${avatarHtml}
              <div style="position:absolute;bottom:0;right:0;width:22px;height:22px;border-radius:50%;background:var(--primary);display:flex;align-items:center;justify-content:center">
                <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="2.5"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
              </div>
            </div>
            <input type="file" id="settings-avatar-input" accept="image/*" style="display:none" onchange="settingsUploadAvatar(this)">

            <div style="display:flex;flex-direction:column;gap:4px">
              <div style="font-weight:700;font-size:17px">${esc(user.fullName || '—')}</div>
              <div style="color:var(--text-muted);font-size:13px">@${esc(state.username)}</div>
              ${user.department ? `<div style="font-size:12px;color:var(--text-muted)">${esc(user.department)}</div>` : ''}
              ${roleBadge(state.role)}
            </div>
          </div>

          <hr style="border:none;border-top:1px solid var(--border);margin:0">

          ${user.email ? `
          <div style="display:flex;align-items:center;gap:8px;font-size:13px;color:var(--text-muted)">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"/><polyline points="22,6 12,13 2,6"/></svg>
            ${esc(user.email)}
          </div>` : ''}

          <button class="btn btn-secondary" style="width:100%" onclick="openChangePasswordModal()">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="margin-right:6px"><rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
            Change Password
          </button>
        </div>
      </div>

      <!-- About card -->
      <div class="card">
        <div class="card-header"><span class="card-title">About</span></div>
        <div class="card-body" style="display:flex;flex-direction:column;gap:4px">
          <div style="font-weight:600">OptiClass v1.0</div>
          <div class="text-muted text-sm">University Scheduling &amp; XAI-Driven Calendar Portal</div>
        </div>
      </div>

    </div>
  `);
}

async function settingsUploadAvatar(input) {
  const file = input.files[0];
  if (!file) return;
  input.value = '';
  try {
    const dataUrl = await processAvatarFile(file);
    await API.updateAvatar(state.username, dataUrl);
    toast('Avatar updated', 'success');
    renderSettings();
  } catch (e) {
    toast('Failed to upload avatar: ' + e.message, 'error');
  }
}

function openChangePasswordModal() {
  openModal('Change Password', `
    <div class="form-group">
      <label>Current Password</label>
      <input id="cp-current" type="password" placeholder="Enter current password">
    </div>
    <div class="form-group">
      <label>New Password</label>
      <input id="cp-new" type="password" placeholder="Min 8 chars, uppercase, number, special">
      <p class="hint" style="margin-top:4px">Min 8 chars · uppercase · lowercase · number · special char (!@#$…)</p>
    </div>
    <div class="form-group">
      <label>Confirm New Password</label>
      <input id="cp-confirm" type="password" placeholder="Repeat new password">
    </div>
    <div id="cp-error" style="color:var(--danger,#dc2626);font-size:13px;display:none;margin-top:4px"></div>
  `, `
    <button class="btn btn-secondary" onclick="closeModal()">Cancel</button>
    <button class="btn btn-primary" onclick="submitChangePassword()">Save</button>
  `);
}

async function submitChangePassword() {
  const current = document.getElementById('cp-current').value;
  const newPw   = document.getElementById('cp-new').value;
  const confirm = document.getElementById('cp-confirm').value;
  const errEl   = document.getElementById('cp-error');

  const pwError = validatePassword(newPw);
  if (pwError) {
    errEl.textContent = pwError; errEl.style.display = 'block'; return;
  }
  if (newPw !== confirm) {
    errEl.textContent = 'Passwords do not match'; errEl.style.display = 'block'; return;
  }

  const saveBtn = document.querySelector('#modal-footer .btn-primary');
  if (saveBtn) { saveBtn.disabled = true; saveBtn.textContent = 'Saving…'; }
  errEl.style.display = 'none';

  try {
    await API.changePassword(state.username, current, newPw);
    closeModal();
    toast('Password changed successfully', 'success');
  } catch (e) {
    errEl.textContent = e.message || 'Password change failed';
    errEl.style.display = 'block';
    if (saveBtn) { saveBtn.disabled = false; saveBtn.textContent = 'Save'; }
  }
}

// ══════════════════════════════════════════════════════════
// CHATBOT
// ══════════════════════════════════════════════════════════
const CHATBOT_SUGGESTED = [
  'Where is my schedule?',
  'How do I fill availability?',
  'How to assign a course?',
  'What is a scheduling phase?',
  'What is a common course?',
  'How to change password?',
  'How to import courses?',
  'How does the AI suggestion work?',
];

function renderChatbot() {
  setTopbarTitle('AI Assistant');
  setTopbarRight('');

  if (!state.chatbotMessages || state.chatbotMessages.length === 0) {
    state.chatbotMessages = [{
      text: "Hello! I'm the OptiClass Assistant. I can help you navigate the app, understand features, and answer questions about scheduling. What would you like to know?",
      isUser: false,
    }];
  }
  state.chatbotWaiting = false;

  setContent(`
    <style>
      @keyframes cb-dot-bounce {0%,80%,100%{opacity:.3;transform:translateY(0)} 40%{opacity:1;transform:translateY(-4px)}}
      .cb-dot{width:7px;height:7px;border-radius:50%;background:var(--text-muted,#94a3b8);display:inline-block;animation:cb-dot-bounce 1.2s ease-in-out infinite}
      .cb-dot:nth-child(2){animation-delay:.15s}.cb-dot:nth-child(3){animation-delay:.3s}
    </style>
    <div style="display:flex;flex-direction:column;height:calc(100vh - 57px)">
      <div id="cb-msgs" style="flex:1;overflow-y:auto;padding:16px;display:flex;flex-direction:column;gap:10px"></div>
      <div id="cb-sugg" style="padding:0 16px 8px;display:flex;gap:8px;overflow-x:auto;flex-shrink:0;scrollbar-width:none"></div>
      <div style="padding:10px 16px 14px;border-top:1px solid var(--border);display:flex;gap:10px;align-items:flex-end;flex-shrink:0;background:var(--surface)">
        <textarea id="cb-input" rows="1" placeholder="Ask something…"
          style="flex:1;resize:none;padding:10px 16px;border:1.5px solid var(--border);border-radius:22px;font-size:14px;font-family:inherit;background:var(--bg);color:var(--text);outline:none;line-height:1.45;max-height:100px;overflow-y:auto;transition:border-color .15s"
          onfocus="this.style.borderColor='var(--primary)'"
          onblur="this.style.borderColor='var(--border)'"
          onkeydown="if(event.key==='Enter'&&!event.shiftKey){event.preventDefault();chatbotSend()}"
          oninput="this.style.height='auto';this.style.height=Math.min(this.scrollHeight,100)+'px'"
        ></textarea>
        <button onclick="chatbotSend()" title="Send"
          style="width:42px;height:42px;border-radius:50%;border:none;background:var(--primary);color:white;cursor:pointer;display:flex;align-items:center;justify-content:center;flex-shrink:0;transition:opacity .15s"
          onmouseover="this.style.opacity='.85'" onmouseout="this.style.opacity='1'"
        >${chatSendIcon()}</button>
      </div>
    </div>
  `);

  chatbotRefreshMessages();
  chatbotRefreshSuggestions();
  chatbotScrollBottom();
  setTimeout(() => { const inp = document.getElementById('cb-input'); if (inp) inp.focus(); }, 50);
}

function chatbotBubbleHTML(msg) {
  if (msg.isTyping) {
    return `<div style="display:flex;justify-content:flex-start">
      <div style="background:var(--surface-raised,#f1f5f9);border-radius:4px 16px 16px 16px;padding:12px 16px;display:flex;gap:5px;align-items:center">
        <span class="cb-dot"></span><span class="cb-dot"></span><span class="cb-dot"></span>
      </div>
    </div>`;
  }
  const bg     = msg.isUser ? 'var(--primary)' : 'var(--surface-raised,#f1f5f9)';
  const color  = msg.isUser ? 'white' : 'var(--text)';
  const radius = msg.isUser ? '16px 4px 16px 16px' : '4px 16px 16px 16px';
  const align  = msg.isUser ? 'flex-end' : 'flex-start';
  const side   = msg.isUser ? 'margin-left:18%' : 'margin-right:18%';
  return `<div style="display:flex;justify-content:${align}">
    <div style="background:${bg};color:${color};border-radius:${radius};padding:10px 14px;font-size:14px;line-height:1.5;${side};word-break:break-word;white-space:pre-wrap;max-width:100%">${esc(msg.text)}</div>
  </div>`;
}

function chatbotRefreshMessages() {
  const el = document.getElementById('cb-msgs');
  if (!el) return;
  el.innerHTML = (state.chatbotMessages || []).map(chatbotBubbleHTML).join('');
}

function chatbotRefreshSuggestions() {
  const el = document.getElementById('cb-sugg');
  if (!el) return;
  if ((state.chatbotMessages || []).length > 1) { el.innerHTML = ''; return; }
  el.innerHTML = CHATBOT_SUGGESTED.map(q =>
    `<button onclick="chatbotSendText('${esc(q)}')"
      style="white-space:nowrap;padding:6px 14px;border:1px solid var(--border);border-radius:16px;background:var(--bg);color:var(--text);cursor:pointer;font-size:12px;flex-shrink:0;transition:background .15s"
      onmouseover="this.style.background='var(--surface-raised,#f1f5f9)'"
      onmouseout="this.style.background='var(--bg)'"
    >${esc(q)}</button>`
  ).join('');
}

function chatbotScrollBottom() {
  const el = document.getElementById('cb-msgs');
  if (el) el.scrollTop = el.scrollHeight;
}

function chatbotSend() {
  const inp = document.getElementById('cb-input');
  if (!inp) return;
  const text = inp.value.trim();
  if (!text) return;
  inp.value = '';
  inp.style.height = 'auto';
  chatbotSendText(text);
}

async function chatbotSendText(text) {
  if (!text || state.chatbotWaiting) return;
  state.chatbotMessages = state.chatbotMessages || [];
  state.chatbotMessages.push({ text, isUser: true });
  state.chatbotWaiting = true;
  state.chatbotMessages.push({ isTyping: true, isUser: false });
  chatbotRefreshMessages();
  chatbotRefreshSuggestions();
  chatbotScrollBottom();

  try {
    const result = await API.sendChatbotMessage(text);
    state.chatbotMessages = state.chatbotMessages.filter(m => !m.isTyping);
    state.chatbotMessages.push({ text: result?.response || "Sorry, I couldn't get a response.", isUser: false });
  } catch (e) {
    state.chatbotMessages = state.chatbotMessages.filter(m => !m.isTyping);
    state.chatbotMessages.push({ text: 'The assistant is currently unavailable. Please make sure the RASA server is running.', isUser: false });
  }

  state.chatbotWaiting = false;
  chatbotRefreshMessages();
  chatbotRefreshSuggestions();
  chatbotScrollBottom();
}

// ══════════════════════════════════════════════════════════
// LOGS
// ══════════════════════════════════════════════════════════
async function renderLogs() {
  if (state.role !== 'SUPER_ADMIN') { navigate('dashboard'); return; }

  if (logsEventSource) { logsEventSource.close(); logsEventSource = null; }

  const actionTypes = ['LOGIN', 'LOGOUT', 'CREATE_USER', 'UPDATE_USER', 'DELETE_USER',
    'RESET_PASSWORD', 'ADD_CLASSROOM', 'DELETE_CLASSROOM', 'IMPORT_COURSES', 'DELETE_COURSE',
    'SAVE_SCHEDULE', 'SET_PHASE', 'SEND_NOTIFICATION'];

  let logs = [];
  try {
    const params = { limit: 200 };
    if (state.logFilter) params.action_filter = state.logFilter;
    logs = await API.getLogs(params);
  } catch (e) { toast(e.message, 'error'); }

  setTopbarRight(`
    <span id="log-live-badge" style="margin-right:8px;padding:2px 8px;border-radius:9999px;font-size:12px;background:#dcfce7;color:#16a34a;font-weight:600">● Live</span>
    <button class="btn btn-ghost btn-sm" onclick="exportLogs()">⬇ Export CSV</button>
  `);

  setContent(`
    <div class="page-toolbar">
      <input class="search-input" placeholder="Filter by action…" value="${esc(state.logFilter)}"
             oninput="state.logFilter=this.value"
             onkeydown="if(event.key==='Enter') renderLogs()">
      <select class="filter-select" onchange="state.logFilter=this.value; renderLogs()">
        <option value="">All Actions</option>
        ${actionTypes.map(a => `<option value="${a}" ${state.logFilter===a?'selected':''}>${a}</option>`).join('')}
      </select>
      <button class="btn btn-secondary" onclick="renderLogs()">Search</button>
      <button class="btn btn-ghost" onclick="state.logFilter=''; renderLogs()">Clear</button>
    </div>
    <div class="card">
      <div class="card-header">
        <span class="card-title">Audit Logs</span>
        <span id="log-count" class="text-muted text-sm" data-count="${logs.length}">${logs.length} entries loaded</span>
      </div>
      <div class="table-wrapper">
        <table>
          <thead>
            <tr><th>Timestamp</th><th>Actor</th><th>Role</th><th>Action</th><th>Target</th><th>Details</th><th>IP</th><th>Device</th></tr>
          </thead>
          <tbody id="log-tbody">
            ${logs.length === 0
              ? `<tr id="log-empty"><td colspan="8" style="text-align:center;color:var(--text-muted)">No log entries found</td></tr>`
              : logs.map(_logRowHtml).join('')}
          </tbody>
        </table>
      </div>
    </div>
  `);

  _connectLogStream();
}

function _connectLogStream() {
  if (logsEventSource) return;
  logsEventSource = new EventSource(`/logs/stream?token=${encodeURIComponent(state.token)}`);

  logsEventSource.onmessage = (e) => {
    let l;
    try { l = JSON.parse(e.data); } catch { return; }

    if (state.logFilter && !l.action.toLowerCase().includes(state.logFilter.toLowerCase())) return;

    const tbody = document.getElementById('log-tbody');
    if (!tbody) return;

    const empty = document.getElementById('log-empty');
    if (empty) empty.remove();

    tbody.insertAdjacentHTML('afterbegin', _logRowHtml(l));

    const countEl = document.getElementById('log-count');
    if (countEl) {
      const next = parseInt(countEl.dataset.count || '0', 10) + 1;
      countEl.dataset.count = next;
      countEl.textContent = `${next} entries loaded`;
    }

    const newRow = tbody.rows[0];
    if (newRow) {
      newRow.style.transition = 'background 0.3s';
      newRow.style.background = 'rgba(79,70,229,0.12)';
      setTimeout(() => { if (newRow) newRow.style.background = ''; }, 1500);
    }
  };

  logsEventSource.onerror = () => {
    const badge = document.getElementById('log-live-badge');
    if (badge) { badge.textContent = '○ Disconnected'; badge.style.background='#fef9c3'; badge.style.color='#854d0e'; }
  };
}

function exportLogs() {
  const rows = document.querySelectorAll('#content table tbody tr');
  if (!rows.length) { toast('No data to export', 'warning'); return; }
  const headers = ['Timestamp','Actor','Role','Action','Target','Details','IP','Device'];
  const lines = [headers.join(',')];
  rows.forEach(r => {
    const cells = Array.from(r.querySelectorAll('td')).map(td => `"${td.textContent.trim().replace(/"/g,'""')}"`);
    lines.push(cells.join(','));
  });
  const blob = new Blob([lines.join('\n')], { type: 'text/csv' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a'); a.href = url; a.download = 'audit_logs.csv';
  a.click(); URL.revokeObjectURL(url);
  toast('Exported', 'success');
}

// ══════════════════════════════════════════════════════════
// INIT
// ══════════════════════════════════════════════════════════
document.addEventListener('DOMContentLoaded', () => {
  // Login form
  document.getElementById('login-form').addEventListener('submit', handleLogin);

  // Logout
  document.getElementById('logout-btn').addEventListener('click', async () => {
    const confirmed = await new Promise(resolve => {
      _modalResolve = resolve;
      openModal(
        'Sign Out',
        `<div style="text-align:center;padding:8px 0">
          <div style="font-size:40px;margin-bottom:12px">👋</div>
          <p style="font-size:15px;color:var(--text-main);margin-bottom:4px">Are you sure you want to sign out?</p>
          <p class="text-muted text-sm">You will be returned to the login screen.</p>
        </div>`,
        `<button class="btn btn-secondary" onclick="closeModal()">Cancel</button>
         <button class="btn btn-primary" onclick="_modalResolve(true);_modalResolve=null;closeModal()">Sign Out</button>`
      );
    });
    if (confirmed) logout();
  });

  // Close modal on overlay click
  document.getElementById('modal-overlay').addEventListener('click', e => {
    if (e.target === document.getElementById('modal-overlay')) closeModal();
  });

  // Router
  window.addEventListener('hashchange', handleRoute);

  // Restore session
  const token = getToken();
  const user = getUser();
  if (token && user) {
    state.token = token;
    state.username = user.username;
    state.role = user.role;
    state.department = user.department || null;
    buildSidebar();
    showApp();
    document.getElementById('sidebar-username').textContent = state.username;
    document.getElementById('sidebar-role').textContent = ({ADMIN:'Admin',SUPER_ADMIN:'System Admin',INSTRUCTOR:'Instructor'})[state.role] || state.role;
    document.getElementById('sidebar-avatar').textContent = state.username[0].toUpperCase();
    const deptEl = document.getElementById('sidebar-dept');
    if (deptEl) deptEl.textContent = (state.role === 'ADMIN' && state.department) ? state.department : '';
    handleRoute();
  } else {
    showLogin();
  }
});
