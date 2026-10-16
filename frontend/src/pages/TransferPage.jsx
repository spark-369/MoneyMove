import { useEffect, useRef, useState } from 'react';
import { useAuth } from '../context/useAuth';
import * as transferApi from '../api/transfer';
import * as accountApi from '../api/account';
import { ApiError } from '../api/client';
import { formatMoney, generateIdempotencyKey } from '../utils/format';

export default function TransferPage() {
  const { user, updateBalance } = useAuth();

  const [toUsername, setToUsername] = useState('');
  const [amount, setAmount] = useState('');
  const [description, setDescription] = useState('');
  const [search, setSearch] = useState('');
  const [results, setResults] = useState([]);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [submitting, setSubmitting] = useState(false);

  // Monotonic id so a slow, out-of-order search response cannot overwrite the
  // results of a newer keystroke.
  const searchSeq = useRef(0);

  // Auto-generated per-transaction idempotency key. It is created once when the
  // form mounts and reused for every retry of the same submission, so a network
  // retry or double-click cannot move money twice. It is rotated only after a
  // successful transfer (when the form is reset for the next payment).
  const idempotencyKey = useRef(generateIdempotencyKey());

  useEffect(() => {
    const q = search.trim();
    if (q.length < 2) {
      setResults([]);
      return undefined;
    }
    const seq = ++searchSeq.current;
    const timer = setTimeout(async () => {
      try {
        const users = await accountApi.searchUsers(q);
        if (seq !== searchSeq.current) return;
        setResults(users.filter((u) => u.username !== user?.username));
      } catch {
        if (seq === searchSeq.current) setResults([]);
      }
    }, 200);
    return () => clearTimeout(timer);
  }, [search, user?.username]);

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
        idempotencyKey: idempotencyKey.current,
      });
      setSuccess(
        `Sent ${formatMoney(tx.amount, tx.currency)} to ${tx.receiverUsername || toUsername.trim()}. Ref: ${tx.referenceId}`
      );
      setAmount('');
      setDescription('');
      setToUsername('');
      setResults([]);
      setSearch('');
      // New payment next time: rotate to a fresh key so the value just used is
      // not accidentally replayed for a different transfer.
      idempotencyKey.current = generateIdempotencyKey();
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
              setSearch(e.target.value);
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
          <div><span className="muted">From:</span> {tx.senderUsername || '-'} → <span className="muted">To:</span> {tx.receiverUsername || '-'}</div>
          <div><span className="muted">Amount:</span> {formatMoney(tx.amount, tx.currency)}</div>
          <div><span className="muted">Description:</span> {tx.description || '-'}</div>
          <div><span className="muted">Status:</span> {tx.status}</div>
        </div>
      )}
    </>
  );
}
