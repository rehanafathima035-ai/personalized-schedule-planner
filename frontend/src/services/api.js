const BASE = import.meta.env.VITE_API_BASE || '';
const TOKEN_KEY = 'lifeplanner.token';

export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token) {
  if (token) localStorage.setItem(TOKEN_KEY, token);
  else localStorage.removeItem(TOKEN_KEY);
}

export class ApiError extends Error {
  constructor(message, status, code) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

async function request(path, { method = 'GET', body } = {}) {
  const headers = { 'Content-Type': 'application/json' };
  const token = getToken();

  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  let response;

  try {
    response = await fetch(`${BASE}/api${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(
      'Cannot reach the server. Check that the backend is running.',
      0,
      'NETWORK',
    );
  }

  if (response.status === 401) {
    setToken(null);
    throw new ApiError(
      'Your session expired. Sign in again.',
      401,
      'UNAUTHORIZED',
    );
  }

  const text = await response.text();
  let payload = null;

  try {
    payload = text ? JSON.parse(text) : null;
  } catch {
    throw new ApiError(
      'The server returned an invalid response.',
      response.status,
      'INVALID_RESPONSE',
    );
  }

  if (!response.ok) {
    throw new ApiError(
      payload?.message || 'That request did not go through.',
      response.status,
      payload?.error,
    );
  }

  return payload;
}

export const api = {
  listTasks: () =>
    request('/tasks'),

  getTask: (id) =>
    request(`/tasks/${id}`),

  createTask: (data) =>
    request('/tasks', {
      method: 'POST',
      body: data,
    }),

  updateTask: (id, data) =>
    request(`/tasks/${id}`, {
      method: 'PUT',
      body: data,
    }),

  completeTask: (id) =>
    request(`/tasks/${id}/complete`, {
      method: 'POST',
    }),

  skipTask: (id) =>
    request(`/tasks/${id}/skip`, {
      method: 'POST',
    }),

  deleteTask: (id) =>
    request(`/tasks/${id}`, {
      method: 'DELETE',
    }),
  
  register: (data) =>
    request('/auth/register', {
      method: 'POST',
      body: data,
    }),

  login: (data) =>
    request('/auth/login', {
      method: 'POST',
      body: data,
    }),

  me: () => request('/auth/me'),

  listHabits: () => request('/habits'),

  completeHabit: (habitId, date) =>
    request(`/habits/${habitId}/complete?date=${date}`, {
      method: 'POST',
    }),

  uncompleteHabit: (habitId, date) =>
    request(`/habits/${habitId}/complete?date=${date}`, {
      method: 'DELETE',
    }),

  habitCompletions: (from, to) =>
    request(`/habits/completions?from=${from}&to=${to}`),

  interpret: (text) =>
    request('/habits/interpret', {
      method: 'POST',
      body: { text },
    }),

  previewHabit: (data) =>
    request('/habits/preview', {
      method: 'POST',
      body: data,
    }),

  approveProposal: (id) =>
    request(`/schedule/proposals/${id}/approve`, {
      method: 'POST',
    }),

  rejectProposal: (id) =>
    request(`/schedule/proposals/${id}/reject`, {
      method: 'POST',
    }),

  deleteScheduledItem: (id) =>
    request(`/schedule/${id}`, {
      method: 'DELETE',
    }),

  scheduleRange: (from, to) =>
    request(`/schedule?from=${from}&to=${to}`),

  today: () => request('/schedule/today'),

  prayerSettings: () =>
    request('/prayers/settings'),

  savePrayerSettings: (data) =>
    request('/prayers/settings', {
      method: 'POST',
      body: data,
    }),

  prayerCompletions: (from, to) =>
    request(`/prayers/completions?from=${from}&to=${to}`),
  
    habitStreak: (habitId) =>
    request(`/habits/${habitId}/streak`),

  prayerStreak: () =>
    request('/prayers/streak'),

  analytics: (from, to) =>
    request(`/analytics?from=${from}&to=${to}`),

  completionHistory: (from, to) =>
    request(`/completion-history?from=${from}&to=${to}`),
  
  completePrayer: (prayerName, date) =>
    request(`/prayers/completions/${prayerName}?date=${date}`, {
      method: 'POST',
    }),

  uncompletePrayer: (prayerName, date) =>
    request(`/prayers/completions/${prayerName}?date=${date}`, {
      method: 'DELETE',
    }),
  listGoals: () =>
    request('/goals'),

  getGoal: (id) =>
    request(`/goals/${id}`),

  createGoal: (data) =>
    request('/goals', {
      method: 'POST',
      body: data,
    }),

  updateGoal: (id, data) =>
    request(`/goals/${id}`, {
      method: 'PUT',
      body: data,
    }),

  updateGoalStatus: (id, status) =>
    request(`/goals/${id}/status?status=${status}`, {
      method: 'PUT',
    }),

  updateGoalProgress: (id, achievedCount) =>
    request(`/goals/${id}/progress?achievedCount=${achievedCount}`, {
      method: 'PUT',
    }),

  deleteGoal: (id) =>
    request(`/goals/${id}`, {
      method: 'DELETE',
    }),

  getGoalProgress: (id) =>
    request(`/goals/${id}/progress`),

  listMilestones: (goalId) =>
    request(`/goals/${goalId}/milestones`),

  createMilestone: (goalId, data) =>
    request(`/goals/${goalId}/milestones`, {
      method: 'POST',
      body: data,
    }),

  updateMilestone: (goalId, milestoneId, data) =>
    request(`/goals/${goalId}/milestones/${milestoneId}`, {
      method: 'PUT',
      body: data,
    }),

  completeMilestone: (goalId, milestoneId) =>
    request(`/goals/${goalId}/milestones/${milestoneId}/complete`, {
      method: 'POST',
    }),

  reopenMilestone: (goalId, milestoneId) =>
    request(`/goals/${goalId}/milestones/${milestoneId}/reopen`, {
      method: 'POST',
    }),

  deleteMilestone: (goalId, milestoneId) =>
    request(`/goals/${goalId}/milestones/${milestoneId}`, {
      method: 'DELETE',
    }),
  listReminders: () =>
    request('/reminders'),

  createReminder: (data) =>
    request('/reminders', {
      method: 'POST',
      body: data,
    }),

  dueReminders: (date, currentMinute) =>
    request(
      `/reminders/due?date=${date}&currentMinute=${currentMinute}`,
    ),  
};