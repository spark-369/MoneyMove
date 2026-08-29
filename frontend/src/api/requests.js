import { api } from './client';

export function createRequest(payload) {
  return api.post('/requests', payload);
}

export function acceptRequest(id) {
  return api.post(`/requests/${id}/accept`);
}

export function declineRequest(id) {
  return api.post(`/requests/${id}/decline`);
}

export function listIncomingRequests() {
  return api.get('/requests/incoming');
}

export function listOutgoingRequests() {
  return api.get('/requests/outgoing');
}
