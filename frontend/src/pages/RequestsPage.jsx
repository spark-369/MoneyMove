import { useCallback, useEffect, useState } from 'react';
import * as requestApi from '../api/requests';
import * as accountApi from '../api/account';
import { ApiError } from '../api/client';
import { useAuth } from '../context/useAuth';
import { formatMoney, formatDate, statusBadgeClass } from '../utils/format';

export default function RequestsPage() {
  const { updateBalance } = useAuth();
  const [tab, setTab] = useState('incoming');
  const [items, setItems] = useState([]);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busyId, setBusyId] = useState(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setError('');
    setLoading(true);
    try {
      const data = tab === 'incoming' ? await requestApi.listIncomingRequests() : await requestApi.listOutgoingRequests();
      setItems(Array.isArray(data) ? data : []);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to load requests');
    } finally {
      setLoading(false);
    }
  }, [tab]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    load();
  }, [load]);

  async function handleAccept(id) {
    setBusyId(id);
    setError('');
    setNotice('');
    try {
      // The accept endpoint returns the settlement TransactionResponse, which
      // carries the transfer reference — surface it as a receipt.
      const tx = await requestApi.acceptRequest(id);
      // Paying a request moves money out of the caller's account; refresh the
      // cached balance before showing the refreshed list.
      await load();
      try {
        const bal = await accountApi.getMyBalance();
        updateBalance(bal.balance, bal.currency);
      } catch {
        // Balance refresh is best-effort; the request itself already succeeded.
      }
      if (tx?.referenceId) {
        setNotice(`Paid ${formatMoney(tx.amount, tx.currency)}. Ref: ${tx.referenceId}`);
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to accept');
    } finally {
      setBusyId(null);
    }
  }

  async function handleDecline(id) {
    setBusyId(id);
    setError('');
    setNotice('');
    try {
      await requestApi.declineRequest(id);
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to decline');
    } finally {
      setBusyId(null);
    }
  }

  return (
    <div className="page-narrow">
      <h1 className="page-title">Money requests</h1>
      {error && <div className="alert alert-danger">{error}</div>}
      {notice && <div className="alert alert-success">{notice}</div>}

      <div className="tabs">
        <button className={tab === 'incoming' ? 'tab active' : 'tab'} onClick={() => { setNotice(''); setTab('incoming'); }}>
          Incoming
        </button>
        <button className={tab === 'outgoing' ? 'tab active' : 'tab'} onClick={() => { setNotice(''); setTab('outgoing'); }}>
          Outgoing
        </button>
        <button className="tab tab-action" onClick={() => { setNotice(''); setTab('create'); }}>
          + New request
        </button>
      </div>

      {tab === 'create' ? (
        <CreateRequestForm onDone={() => { setTab('outgoing'); }} />
      ) : (
        <div className="card">
          {loading ? (
            <div>
              {[0, 1, 2].map((i) => (
                <div key={i} className="skeleton skeleton-block" />
              ))}
            </div>
          ) : items.length === 0 ? (
            <div className="empty">
              <div className="empty-icon">{tab === 'incoming' ? '📥' : '📤'}</div>
              <div className="empty-title">No {tab} requests</div>
              <div className="empty-sub">
                {tab === 'incoming'
                  ? 'When someone requests money from you, it will show here.'
                  : 'Requests you send will appear here.'}
              </div>
            </div>
          ) : (
            <ul className="request-list">
              {items.map((r) => {
                const counterpart = tab === 'incoming' ? r.requesterUsername : r.debtorUsername;
                const isPending = r.status === 'PENDING';
                return (
                  <li key={r.id} className="request-item">
                    <div className="request-main">
                      <div className="request-amount">{formatMoney(r.amount, r.currency)}</div>
                      <div className="muted">
                        {tab === 'incoming' ? 'From' : 'To'}: <strong>{counterpart}</strong>
                      </div>
                      {r.note && <div className="request-note">“{r.note}”</div>}
                      <div className="muted request-date">{formatDate(r.createdAt)}</div>
                    </div>
                    <div className="request-side">
                      <span className={statusBadgeClass(r.status)}>{r.status}</span>
                      {tab === 'incoming' && isPending && (
                        <div className="request-actions">
                          <button
                            className="btn btn-primary btn-sm"
                            disabled={busyId === r.id}
                            onClick={() => handleAccept(r.id)}
                          >
                            Pay
                          </button>
                          <button
                            className="btn btn-ghost btn-sm"
                            disabled={busyId === r.id}
                            onClick={() => handleDecline(r.id)}
                          >
                            Decline
                          </button>
                        </div>
                      )}
                    </div>
                  </li>
                );
              })}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}

function CreateRequestForm({ onDone }) {
  const [fromUsername, setFromUsername] = useState('');
  const [amount, setAmount] = useState('');
  const [note, setNote] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();
    setError('');
    const amt = Number(amount);
    if (!fromUsername.trim()) return setError('Debtor username is required');
    if (!amt || amt <= 0) return setError('Amount must be greater than 0');
    setSubmitting(true);
    try {
      await requestApi.createRequest({
        fromUsername: fromUsername.trim(),
        amount: amt.toFixed(2),
        note: note.trim(),
      });
      onDone();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to create request');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form className="card" onSubmit={handleSubmit}>
      <h3 className="card-title">Request money</h3>
      {error && <div className="alert alert-danger">{error}</div>}
      <label className="field">
        <span>From username</span>
        <input
          value={fromUsername}
          onChange={(e) => setFromUsername(e.target.value)}
          placeholder="Who should pay you?"
          required
        />
      </label>
      <label className="field">
        <span>Amount</span>
        <input
          type="number"
          step="0.01"
          min="0.01"
          value={amount}
          onChange={(e) => setAmount(e.target.value)}
          placeholder="0.00"
          required
        />
      </label>
      <label className="field">
        <span>Note (optional)</span>
        <input value={note} onChange={(e) => setNote(e.target.value)} maxLength={255} placeholder="What for?" />
      </label>
      <button className="btn btn-primary btn-block" type="submit" disabled={submitting}>
        {submitting ? 'Sending request…' : 'Send request'}
      </button>
    </form>
  );
}
