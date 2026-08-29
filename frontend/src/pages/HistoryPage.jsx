import { useEffect, useState } from 'react';
import * as accountApi from '../api/account';
import { ApiError } from '../api/client';
import { formatMoney, formatDate, statusBadgeClass } from '../utils/format';

export default function HistoryPage() {
  const [txs, setTxs] = useState([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    async function load() {
      try {
        const data = await accountApi.getHistory();
        if (active) setTxs(Array.isArray(data) ? data : []);
      } catch (err) {
        if (active) setError(err instanceof ApiError ? err.message : 'Failed to load history');
      } finally {
        if (active) setLoading(false);
      }
    }
    load();
    return () => {
      active = false;
    };
  }, []);

  return (
    <div className="page-narrow">
      <h1 className="page-title">Transaction history</h1>
      {error && <div className="alert alert-danger">{error}</div>}

      <div className="card">
        {loading ? (
          <div>
            {[0, 1, 2, 3].map((i) => (
              <div key={i} className="skeleton skeleton-block" />
            ))}
          </div>
        ) : txs.length === 0 ? (
          <div className="empty">
            <div className="empty-icon">📭</div>
            <div className="empty-title">No transactions yet</div>
            <div className="empty-sub">Your past transfers and requests will appear here.</div>
          </div>
        ) : (
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Date</th>
                  <th>Type</th>
                  <th>From</th>
                  <th>To</th>
                  <th>Description</th>
                  <th>Amount</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {txs.map((tx) => (
                  <tr key={tx.referenceId}>
                    <td className="muted">{formatDate(tx.createdAt)}</td>
                    <td>{tx.type}</td>
                    <td>{tx.senderUsername || '-'}</td>
                    <td>{tx.receiverUsername || '-'}</td>
                    <td className="muted">{tx.description || '-'}</td>
                    <td className="tx-amount">{formatMoney(tx.amount, tx.currency)}</td>
                    <td>
                      <span className={statusBadgeClass(tx.status)}>{tx.status}</span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
