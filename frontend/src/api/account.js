import { api } from './client';

export function getMyBalance() {
  return api.get('/account/me/balance');
}

export function getHistory() {
  return api.get('/account/me/history');
}

export function searchUsers(q) {
  return api.get('/account/users/search', { query: { q } });
}
