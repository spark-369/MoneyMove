export function formatMoney(value, currency) {
  if (value === undefined || value === null) return '-';
  const num = typeof value === 'string' ? Number(value) : value;
  try {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: currency || 'USD',
    }).format(num);
  } catch {
    return `${num} ${currency || ''}`;
  }
}

export function formatDate(value) {
  if (!value) return '-';
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return '-';
  return d.toLocaleString();
}

export function statusBadgeClass(status) {
  if (!status) return 'badge';
  const s = status.toLowerCase();
  if (s.includes('pending')) return 'badge badge-warning';
  if (s.includes('accept') || s.includes('completed') || s.includes('settled')) return 'badge badge-success';
  if (s.includes('declin') || s.includes('fail')) return 'badge badge-danger';
  return 'badge';
}
