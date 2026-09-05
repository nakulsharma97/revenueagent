import { useState, useRef, useEffect } from 'react';
import './LedgerTape.css';

/** Teleprinter-style ticker of recovery outcomes — monospace, fixed-width, scan-line feel. */
export default function LedgerTape({ attempts }) {
  const [hoveredId, setHoveredId] = useState(null);
  const trackRef = useRef(null);
  const [duration, setDuration] = useState(60);

  // Only the newest entries are shown: with the full ledger (hundreds of rows) the track
  // would be kilometres long and a fixed-duration animation would scroll far too fast.
  const entries = attempts.length > 0 ? attempts.slice(0, MAX_ENTRIES) : [{ placeholder: true }];
  const doubled = [...entries, ...entries];

  useEffect(() => {
    const el = trackRef.current;
    const isPlaceholder = entries.length === 1 && entries[0].placeholder;
    if (!el || isPlaceholder) return;

    const measure = () => {
      // The keyframes translate the track by exactly -50% (one copy of the doubled
      // content). Loop time = one copy's width ÷ scroll speed, so every entry crosses
      // the viewport at the same readable pace no matter how many rows are rendered.
      const copyWidth = el.scrollWidth / 2;
      setDuration(Math.max(MIN_LOOP_SECONDS, Math.round(copyWidth / SCROLL_PX_PER_SEC)));
    };
    measure();
    const ro = typeof ResizeObserver !== 'undefined' ? new ResizeObserver(measure) : null;
    if (ro) ro.observe(el);
    return () => { if (ro) ro.disconnect(); };
  }, [attempts]);

  return (
    <div className="ledger-tape">
      <div className="ledger-tape__track" ref={trackRef} style={{ animationDuration: `${duration}s` }}>
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

/** Newest entries rendered in the loop — keeps the tape lively without a kilometre-long track. */
const MAX_ENTRIES = 60;
/** Comfortable scroll speed: each entry (~200px) stays visible for ~2 seconds. */
const SCROLL_PX_PER_SEC = 100;
/** Never spin faster than this even with very few entries. */
const MIN_LOOP_SECONDS = 30;
