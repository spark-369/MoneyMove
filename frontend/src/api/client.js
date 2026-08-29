const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api';

export const TOKEN_KEY = 'mm_token';
export const USER_KEY = 'mm_user';

export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token) {
  if (token) localStorage.setItem(TOKEN_KEY, token);
  else localStorage.removeItem(TOKEN_KEY);
}

export function getUser() {
  try {
    const raw = localStorage.getItem(USER_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

export function setUser(user) {
  if (user) localStorage.setItem(USER_KEY, JSON.stringify(user));
  else localStorage.removeItem(USER_KEY);
}

export class ApiError extends Error {
  constructor(message, status, data) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.data = data;
  }
}

function buildHeaders(requireAuth) {
  const headers = { 'Content-Type': 'application/json' };
  if (requireAuth) {
    const token = getToken();
    if (token) headers['Authorization'] = `Bearer ${token}`;
  }
  return headers;
}

function extractErrorMessage(data, fallback) {
  if (!data) return fallback;
  if (typeof data === 'string') return data;
  if (data.message) {
    if (typeof data.message === 'object') {
      return Object.values(data.message).join(', ');
    }
    return data.message;
  }
  if (data.error) return data.error;
  return fallback;
}

export async function request(method, path, { body, auth = true, query } = {}) {
  let url = `${API_BASE_URL}${path}`;
  if (query) {
    const params = new URLSearchParams();
    Object.entries(query).forEach(([k, v]) => {
      if (v !== undefined && v !== null && v !== '') params.append(k, v);
    });
    const qs = params.toString();
    if (qs) url += `?${qs}`;
  }

  const options = {
    method,
    headers: buildHeaders(auth),
  };
  if (body !== undefined) options.body = JSON.stringify(body);

  let response;
  try {
    response = await fetch(url, options);
  } catch {
    throw new ApiError('Network error. Is the backend running?', 0, null);
  }

  const contentType = response.headers.get('content-type') || '';
  const isJson = contentType.includes('application/json');
  const data = isJson ? await response.json().catch(() => null) : await response.text().catch(() => null);

  if (!response.ok) {
    if (response.status === 401) {
      setToken(null);
    }
    throw new ApiError(
      extractErrorMessage(data, `Request failed with status ${response.status}`),
      response.status,
      data
    );
  }

  return data;
}

export const api = {
  get: (path, opts) => request('GET', path, { ...opts, method: 'GET' }),
  post: (path, body, opts) => request('POST', path, { ...opts, body, method: 'POST' }),
  put: (path, body, opts) => request('PUT', path, { ...opts, body, method: 'PUT' }),
  patch: (path, body, opts) => request('PATCH', path, { ...opts, body, method: 'PATCH' }),
  del: (path, opts) => request('DELETE', path, { ...opts, method: 'DELETE' }),
};
