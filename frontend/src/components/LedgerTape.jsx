import './LedgerTape.css';

/** Signature element: a live ticker of recovery outcomes, styled like an exchange tape. */
export default function LedgerTape({ attempts }) {
  const entries = attempts.length > 0 ? attempts : [{ placeholder: true }];
  const doubled = [...entries, ...entries];

  return (
    <div className="ledger-tape">
      <div className="ledger-tape__track">
        {doubled.map((a, i) => (
          <span key={i} className={`ledger-tape__item ${a.outcome === 'SUCCESS' ? 'is-win' : a.outcome === 'FAILED' ? 'is-loss' : ''}`}>
            {a.placeholder ? (
              'Run a batch to populate the ledger tape —'
            ) : (
              <>
                #{a.transaction?.id ?? '—'}{' '}
                <strong>{a.actionTaken?.replaceAll('_', ' ')}</strong>{' '}
                {a.outcome === 'SUCCESS' ? `+₹${Number(a.amountRecovered).toLocaleString('en-IN')}` : a.outcome}
              </>
            )}
            <span className="ledger-tape__dot">·</span>
          </span>
        ))}
      </div>
    </div>
  );
}
