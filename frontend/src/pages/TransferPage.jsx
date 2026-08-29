import { useState } from 'react';
import { useAuth } from '../context/useAuth';
import * as transferApi from '../api/transfer';
import * as accountApi from '../api/account';
import { ApiError } from '../api/client';
import { formatMoney } from '../utils/format';

export default function TransferPage() {
  const { user, updateBalance } = useAuth();

  const [toUsername, setToUsername] = useState('');
  const [amount, setAmount] = useState('');
  const [description, setDescription] = useState('');
  const [idempotencyKey, setIdempotencyKey] = useState('');
  const [search, setSearch] = useState('');
  const [results, setResults] = useState([]);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [submitting, setSubmitting] = useState(false);

  async function handleSearch(q) {
    setSearch(q);
    if (q.trim().length < 2) {
      setResults([]);
      return;
    }
    try {
      const users = await accountApi.searchUsers(q.trim());
      setResults(users.filter((u) => u.username !== user?.username));
    } catch {
      setResults([]);
    }
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setError('');
    setSuccess('');
    const amt = Number(amount);
    if (!toUsername.trim()) return setError('Recipient is required');
    if (!amt || amt <= 0) return setError('Amount must be greater than 0');

    setSubmitting(true);
    try {
      const tx = await transferApi.transfer({
        toUsername: toUsername.trim(),
        amount: amt.toFixed(2),
        description: description.trim(),
        idempotencyKey: idempotencyKey.trim() || undefined,
      });
      setSuccess(`Sent ${formatMoney(tx.amount, tx.currency)} to ${tx.receiverUsername}. Ref: ${tx.referenceId}`);
      setAmount('');
      setDescription('');
      setIdempotencyKey('');
      setToUsername('');
      setResults([]);
      setSearch('');
      if (user) {
        const bal = await accountApi.getMyBalance();
        updateBalance(bal.balance, bal.currency);
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Transfer failed');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="page-narrow">
      <h1 className="page-title">Send money</h1>

      {error && <div className="alert alert-danger">{error}</div>}
      {success && <div className="alert alert-success">{success}</div>}

      <form className="card" onSubmit={handleSubmit}>
        <label className="field">
          <span>Recipient username</span>
          <input
            value={toUsername}
            onChange={(e) => {
              setToUsername(e.target.value);
              handleSearch(e.target.value);
            }}
            placeholder="Search or type username"
            required
          />
        </label>

        {search.trim().length >= 2 && results.length > 0 && (
          <ul className="suggestions">
            {results.map((u) => (
              <li key={u.username}>
                <button
                  type="button"
                  className="suggestion"
                  onClick={() => {
                    setToUsername(u.username);
                    setResults([]);
                    setSearch('');
                  }}
                >
                  <strong>{u.username}</strong>
                  <span className="muted">{u.fullName}</span>
                </button>
              </li>
            ))}
          </ul>
        )}

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
          <span>Description (optional)</span>
          <input
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="What's it for?"
            maxLength={255}
          />
        </label>

        <label className="field">
          <span>Idempotency key (optional)</span>
          <input
            value={idempotencyKey}
            onChange={(e) => setIdempotencyKey(e.target.value)}
            placeholder="prevents duplicate transfers"
          />
        </label>

        <button className="btn btn-primary btn-block" type="submit" disabled={submitting}>
          {submitting ? <span className="spinner" /> : 'Send'}
        </button>
      </form>

      <div className="card">
        <h3 className="card-title">Look up a transfer</h3>
        <LookupTransfer />
      </div>
    </div>
  );
}

function LookupTransfer() {
  const [ref, setRef] = useState('');
  const [tx, setTx] = useState(null);
  const [error, setError] = useState('');

  async function lookup(e) {
    e.preventDefault();
    setError('');
    setTx(null);
    if (!ref.trim()) return;
    try {
      const data = await transferApi.getTransferByReference(ref.trim());
      setTx(data);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Transfer not found');
    }
  }

  return (
    <>
      <form className="inline-form" onSubmit={lookup}>
        <input
          value={ref}
          onChange={(e) => setRef(e.target.value)}
          placeholder="Transaction reference id"
        />
        <button className="btn btn-secondary" type="submit">
          Find
        </button>
      </form>
      {error && <div className="alert alert-danger">{error}</div>}
      {tx && (
        <div className="lookup-result">
          <div><span className="muted">Reference:</span> {tx.referenceId}</div>
          <div><span className="muted">From:</span> {tx.senderUsername} → <span className="muted">To:</span> {tx.receiverUsername}</div>
          <div><span className="muted">Amount:</span> {formatMoney(tx.amount, tx.currency)}</div>
          <div><span className="muted">Description:</span> {tx.description || '-'}</div>
          <div><span className="muted">Status:</span> {tx.status}</div>
        </div>
      )}
    </>
  );
}
