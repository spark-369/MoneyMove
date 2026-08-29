import { api } from './client';

export function transfer(payload) {
  return api.post('/transfers', payload);
}

export function getTransferByReference(referenceId) {
  return api.get(`/transfers/${encodeURIComponent(referenceId)}`);
}
