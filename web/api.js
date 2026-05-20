/* OptiClass Admin – API Client */

const API_BASE = '';   // same origin; nginx proxies port 80 → 8000

function sha256(str) {
  function rr(w,b){return(w>>>b)|(w<<(32-b));}
  const K=[0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,
    0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,
    0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,
    0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,
    0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,
    0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,0xd192e819,0xd6990624,0xf40e3585,0x106aa070,
    0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,
    0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2];
  let h=[0x6a09e667,0xbb67ae85,0x3c6ef372,0xa54ff53a,0x510e527f,0x9b05688c,0x1f83d9ab,0x5be0cd19];
  const bytes=new TextEncoder().encode(str);
  const len=bytes.length, bitLen=len*8;
  const padLen=((len+8)>>6<<6)+64;
  const buf=new Uint8Array(padLen);
  buf.set(bytes); buf[len]=0x80;
  const dv=new DataView(buf.buffer);
  dv.setUint32(padLen-4,bitLen>>>0,false);
  dv.setUint32(padLen-8,Math.floor(bitLen/2**32),false);
  for(let i=0;i<padLen;i+=64){
    const w=new Int32Array(64);
    for(let j=0;j<16;j++)w[j]=dv.getInt32(i+j*4,false);
    for(let j=16;j<64;j++){
      const s0=(rr(w[j-15],7)^rr(w[j-15],18)^(w[j-15]>>>3));
      const s1=(rr(w[j-2],17)^rr(w[j-2],19)^(w[j-2]>>>10));
      w[j]=(w[j-16]+s0+w[j-7]+s1)|0;
    }
    let [a,b,c,d,e,f,g,hh]=h;
    for(let j=0;j<64;j++){
      const t1=(hh+(rr(e,6)^rr(e,11)^rr(e,25))+((e&f)^(~e&g))+K[j]+w[j])|0;
      const t2=((rr(a,2)^rr(a,13)^rr(a,22))+((a&b)^(a&c)^(b&c)))|0;
      hh=g;g=f;f=e;e=(d+t1)|0;d=c;c=b;b=a;a=(t1+t2)|0;
    }
    h[0]=(h[0]+a)|0;h[1]=(h[1]+b)|0;h[2]=(h[2]+c)|0;h[3]=(h[3]+d)|0;
    h[4]=(h[4]+e)|0;h[5]=(h[5]+f)|0;h[6]=(h[6]+g)|0;h[7]=(h[7]+hh)|0;
  }
  return h.map(v=>(v>>>0).toString(16).padStart(8,'0')).join('');
}

function getToken() { return localStorage.getItem('oc_token'); }
function setToken(t) { localStorage.setItem('oc_token', t); }
function clearToken() { localStorage.removeItem('oc_token'); localStorage.removeItem('oc_user'); }

function setUser(u) { localStorage.setItem('oc_user', JSON.stringify(u)); }
function getUser() {
  try { return JSON.parse(localStorage.getItem('oc_user')); } catch { return null; }
}

async function apiFetch(path, opts = {}) {
  const token = getToken();
  const headers = { 'Content-Type': 'application/json', ...(opts.headers || {}) };
  if (token) headers['Authorization'] = `Bearer ${token}`;
  const res = await fetch(API_BASE + path, { ...opts, headers });
  if (res.status === 401) { clearToken(); location.reload(); return null; }
  if (!res.ok) {
    let msg = `HTTP ${res.status}`;
    try { const j = await res.json(); msg = j.detail || msg; } catch {}
    throw new Error(msg);
  }
  if (res.status === 204) return null;
  const ct = res.headers.get('content-type') || '';
  return ct.includes('application/json') ? res.json() : res.text();
}

const API = {
  // Auth
  async login(username, password) {
    const passwordHash = await sha256(password);
    return apiFetch('/auth/login', { method: 'POST', body: JSON.stringify({ username, passwordHash }) });
  },

  // Users
  getUsers: () => apiFetch('/users'),
  createUser: (data) => apiFetch('/users', { method: 'POST', body: JSON.stringify(data) }),
  updateUser: (username, data) => apiFetch(`/users/${username}`, { method: 'PUT', body: JSON.stringify(data) }),
  deleteUser: (username) => apiFetch(`/users/${username}`, { method: 'DELETE' }),
  async resetPassword(username, newPassword) {
    const newPasswordHash = await sha256(newPassword);
    return apiFetch(`/users/${username}/reset-password`, { method: 'PUT', body: JSON.stringify({ newPasswordHash }) });
  },
  async changePassword(username, current, next) {
    const currentPasswordHash = await sha256(current);
    const newPasswordHash = await sha256(next);
    return apiFetch(`/users/${username}/password`, { method: 'PUT', body: JSON.stringify({ currentPasswordHash, newPasswordHash }) });
  },

  // Classrooms
  getClassrooms: () => apiFetch('/classrooms'),
  addClassroom: (data) => apiFetch('/classrooms', { method: 'POST', body: JSON.stringify(data) }),
  deleteClassroom: (id) => apiFetch(`/classrooms/${id}`, { method: 'DELETE' }),

  // Courses
  getCourses: () => apiFetch('/courses'),
  importCourses: (courses) => apiFetch('/courses/bulk', { method: 'POST', body: JSON.stringify(courses) }),
  deleteCourse: (code, department, email) => apiFetch(`/courses/${encodeURIComponent(code)}?department=${encodeURIComponent(department)}&email=${encodeURIComponent(email)}`, { method: 'DELETE' }),

  // Schedules
  getAllSchedules: () => apiFetch('/schedules'),
  getSchedule: (username) => apiFetch(`/schedules/${username}`),
  saveSchedule: (username, slots) => apiFetch(`/schedules/${username}`, { method: 'PUT', body: JSON.stringify({ instructorUsername: username, slots }) }),

  // Availability
  getAvailabilities: () => apiFetch('/availabilities'),

  // Messages
  getMessages: (withUser) => apiFetch(withUser ? `/messages?with_user=${withUser}` : '/messages'),
  sendMessage: (data) => apiFetch('/messages', { method: 'POST', body: JSON.stringify(data) }),

  // Notifications
  getNotifications: () => apiFetch('/notifications'),
  sendNotification: (data) => apiFetch('/notifications', { method: 'POST', body: JSON.stringify(data) }),

  // History
  getHistory: () => apiFetch('/history'),

  // Settings
  getPhase: () => apiFetch('/settings/scheduling_phase'),
  getPhasePriorities: () => apiFetch('/settings/phase_priorities'),
  setPhase: (phase) => apiFetch('/settings/scheduling_phase', { method: 'PUT', body: JSON.stringify({ phase }) }),

  // Avatar
  updateAvatar: (username, avatarUrl) => apiFetch(`/users/${encodeURIComponent(username)}/avatar`, { method: 'PUT', body: JSON.stringify({ avatarUrl }) }),

  // ChatBot
  sendChatbotMessage: (message) => apiFetch('/chatbot', { method: 'POST', body: JSON.stringify({ message }) }),

  // XAI single slot
  suggestSlot: (username, body) => apiFetch(`/schedule/suggest/${username}`, { method: 'POST', body: JSON.stringify(body) }),

  // XAI weekly
  suggestWeekly: (phase) => apiFetch('/schedule/suggest-weekly', { method: 'POST', body: JSON.stringify({ phase }) }),

  // Logs (SUPER_ADMIN only)
  getLogs: (params = {}) => {
    const q = new URLSearchParams(params).toString();
    return apiFetch('/logs' + (q ? '?' + q : ''));
  },

  // Stats helper
  getStats: async () => {
    const [users, classrooms, courses, history, phase, priorities] = await Promise.all([
      apiFetch('/users').catch(() => []),
      apiFetch('/classrooms').catch(() => []),
      apiFetch('/courses').catch(() => []),
      apiFetch('/history').catch(() => []),
      apiFetch('/settings/scheduling_phase').catch(() => ({ phase: 'PHASE_1' })),
      apiFetch('/settings/phase_priorities').catch(() => ({ phasePriorities: [] })),
    ]);
    return { users, classrooms, courses, history, phase, priorities };
  },
};
