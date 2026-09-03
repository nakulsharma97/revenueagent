import { useState } from 'react';
import './LedgerTape.css';

/** Teleprinter-style ticker of recovery outcomes — monospace, fixed-width, scan-line feel. */
export default function LedgerTape({ attempts }) {
  const [hoveredId, setHoveredId] = useState(null);
  const entries = attempts.length > 0 ? attempts : [{ placeholder: true }];
  const doubled = [...entries, ...entries];

  return (
    <div className="ledger-tape">
      <div className="ledger-tape__track">
        {doubled.map((a, i) => (
          <span
            key={i}
            className={`ledger-tape__item ${a.outcome === 'SUCCESS' ? 'is-win' : a.outcome === 'FAILED' ? 'is-loss' : a.outcome === 'SKIPPED' || a.outcome === 'PENDING' ? 'is-pending' : ''}`}
            onMouseEnter={() => !a.placeholder && setHoveredId(a.id || i)}
            onMouseLeave={() => setHoveredId(null)}
            style={{ position: 'relative' }}
          >
            {a.placeholder ? (
              'Awaiting batch run — data will appear here'
            ) : (
              <>
                TXN#{String(a.transaction?.id ?? '—').padStart(4, '0')}{' '}
                <strong>{a.actionTaken === 'NO_ACTION' ? 'NO ACTION' : a.actionTaken?.replaceAll('_', ' ')}</strong>{' '}
                {a.outcome === 'SUCCESS'
                  ? `+₹${Number(a.amountRecovered).toLocaleString('en-IN')}`
                  : a.outcome === 'SKIPPED' || a.outcome === 'PENDING'
                    ? 'SKIPPED'
                    : a.outcome}
                {a.llmDriven ? ' [LLM]' : ''}
                {hoveredId === (a.id || i) && a.reasoning && (
                  <span className="ledger-tape__tooltip">
                    {a.reasoning}
                    {a.requiresHumanSignoff && (
                      <span className="ledger-tape__tooltip-signoff"> ⚠ REQUIRES SIGNOFF: {a.signoffReason}</span>
                    )}
                  </span>
                )}
              </>
            )}
            <span className="ledger-tape__dot">│</span>
          </span>
        ))}
        <span className="ledger-tape__cursor" />
      </div>
    </div>
  );
}
