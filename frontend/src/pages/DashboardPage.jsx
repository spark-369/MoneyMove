import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/useAuth';
import * as accountApi from '../api/account';
import { ApiError } from '../api/client';
import { formatMoney, formatDate, statusBadgeClass } from '../utils/format';

export default function DashboardPage() {
  const { user, updateBalance } = useAuth();
  const navigate = useNavigate();
  const [balance, setBalance] = useState(null);
  const [currency, setCurrency] = useState(user?.currency || 'BDT');
  const [recent, setRecent] = useState([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    async function load() {
      try {
        const balanceCall = user?.username
          ? accountApi.getBalance(user.username)
          : accountApi.getMyBalance();
        const [bal, hist] = await Promise.all([balanceCall, accountApi.getHistory()]);
        if (!active) return;
        setBalance(bal.balance);
        setCurrency(bal.currency);
        updateBalance(bal.balance, bal.currency);
        setRecent(Array.isArray(hist) ? hist.slice(0, 5) : []);
      } catch (err) {
        if (active) setError(err instanceof ApiError ? err.message : 'Failed to load dashboard');
      } finally {
        if (active) setLoading(false);
      }
    }
    load();
    return () => {
      active = false;
    };
  }, [updateBalance, user?.username]);

  return (
    <div className="dashboard">
      {error && <div className="alert alert-danger">{error}</div>}

      <section className="card balance-card">
        <span className="balance-label">Current balance</span>
        {loading ? (
          <div className="skeleton skeleton-block" style={{ width: 220, height: 44, marginTop: 8 }} />
        ) : (
          <h2 className="balance-amount">{formatMoney(balance, currency)}</h2>
        )}
        <div className="balance-actions">
          <button className="btn btn-primary" onClick={() => navigate('/transfer')}>
            Send money
          </button>
          <button className="btn btn-secondary" onClick={() => navigate('/requests')}>
            Request money
          </button>
        </div>
      </section>

      <section className="card">
        <div className="card-head">
          <h3 className="card-title">Recent activity</h3>
          <button className="btn btn-link" onClick={() => navigate('/history')}>
            View all
          </button>
        </div>
        {loading ? (
          <div>
            {[0, 1, 2].map((i) => (
              <div key={i} className="skeleton skeleton-block" />
            ))}
          </div>
        ) : recent.length === 0 ? (
          <div className="empty">
            <div className="empty-icon">🧾</div>
            <div className="empty-title">No transactions yet</div>
            <div className="empty-sub">Send or request money to get started.</div>
          </div>
        ) : (
          <ul className="tx-list">
            {recent.map((tx) => (
              <li key={tx.referenceId} className="tx-item">
                <div className="tx-main">
                  <span className="tx-type">{tx.type}</span>
                  <span className="tx-desc">{tx.description || '-'}</span>
                </div>
                <div className="tx-meta">
                  <span className="tx-amount">{formatMoney(tx.amount, tx.currency)}</span>
                  <span className={statusBadgeClass(tx.status)}>{tx.status}</span>
                  <span className="muted tx-date">{formatDate(tx.createdAt)}</span>
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
