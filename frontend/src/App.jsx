import { useState, useEffect, useCallback } from 'react';
import BoundsRegister from './components/BoundsRegister';
import LedgerTape from './components/LedgerTape';
import StatCard from './components/StatCard';
import RecoveryChart from './components/RecoveryChart';
import FunnelChart from './components/FunnelChart';
import ActionBreakdownChart from './components/ActionBreakdownChart';
import AttemptTable from './components/AttemptTable';
import TransactionModal from './components/TransactionModal';
import PendingReview from './components/PendingReview';
import { fetchMetrics, runBatch, exportCsv } from './api';

export default function App() {
  const [metrics, setMetrics] = useState(null);
  const [attempts, setAttempts] = useState([]);
  const [loading, setLoading] = useState(false);
  const [streamCount, setStreamCount] = useState(null);
  const [error, setError] = useState(null);
  const [funnelRefresh, setFunnelRefresh] = useState(0);
  const [batchProgress, setBatchProgress] = useState(null);
  const [selectedAttempt, setSelectedAttempt] = useState(null);
  const [reviewCount, setReviewCount] = useState(null);

  const loadMetrics = useCallback(async () => {
    try {
      const m = await fetchMetrics();
      setMetrics(m);
      setError(null);
    } catch (e) {
      setError('Backend not reachable. Start the Spring Boot app on :8080, then reload.');
    }
  }, []);

  const API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8080';

  const loadReviewCount = useCallback(async () => {
    try {
      const res = await fetch(`${API_BASE}/api/recovery/pending-review`);
      if (res.ok) {
        const data = await res.json();
        setReviewCount(data.length);
      }
    } catch (e) { /* ignore */ }
  }, [API_BASE]);

  useEffect(() => { loadMetrics(); loadReviewCount(); }, [loadMetrics, loadReviewCount, funnelRefresh]);

  async function handleRunBatch() {
    setLoading(true);
    setAttempts([]);
    setStreamCount(null);
    setBatchProgress({ processed: 0, total: metrics?.totalAtRisk || 300, recoveredAmount: 0, startTime: Date.now() });
    try {
      const result = await runBatch();
      const reversed = result.reverse();
      setAttempts(reversed);
      setStreamCount(result.length);
      const finalRecovered = reversed.filter(a => a.outcome === 'SUCCESS').reduce((sum, a) => sum + (a.amountRecovered || 0), 0);
      setBatchProgress(prev => prev ? { ...prev, processed: result.length, recoveredAmount: finalRecovered } : null);
      await loadMetrics();
      await loadReviewCount();
      setFunnelRefresh((n) => n + 1);
      setError(null);
    } catch (e) {
      if (e.message?.includes('409') || e.message?.includes('already')) {
        setError('Batch already running — wait for the current batch to complete.');
      } else {
        setError('Batch run failed — check the backend logs.');
      }
    } finally {
      setLoading(false);
      setTimeout(() => setBatchProgress(null), 2000);
    }
  }

  return (
    <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', background: 'var(--bg)' }}>

      {/* ── Statement Header / Letterhead ── */}
      <header style={{ borderBottom: '2px solid var(--text)', background: 'var(--surface)' }}>
        {/* Thin ruled line at top */}
        <div style={{ height: 1, background: 'var(--border)' }} />

        <div style={{ padding: '24px 40px 20px', display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end' }}>
          <div>
            <div style={{
              fontFamily: 'var(--font-display)',
              fontSize: 11,
              fontWeight: 600,
              letterSpacing: '0.12em',
              textTransform: 'uppercase',
              color: 'var(--text-muted)',
              marginBottom: 6,
            }}>
              Recovery Ledger — Batch Operations
            </div>
            <h1 style={{
              fontFamily: 'var(--font-display)',
              fontSize: 28,
              fontWeight: 700,
              color: 'var(--text)',
              letterSpacing: '-0.01em',
              lineHeight: 1.1,
            }}>
              Revenue Recovery Agent
            </h1>
            <div style={{
              fontFamily: 'var(--font-mono)',
              fontSize: 12,
              color: 'var(--text-muted)',
              marginTop: 6,
            }}>
              Razorpay AI Buildathon · Track 03 · AI Revenue Recovery
            </div>
          </div>

          <div style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
            {reviewCount > 0 && (
              <span style={{
                fontFamily: 'var(--font-mono)',
                fontSize: 11,
                color: 'var(--ink-red)',
                border: '1px solid var(--ink-red)',
                padding: '3px 8px',
              }}>
                {reviewCount} PENDING REVIEW
              </span>
            )}
            {loading && (
              <span style={{
                fontFamily: 'var(--font-mono)',
                fontSize: 12,
                color: 'var(--ink-amber)',
                display: 'flex',
                alignItems: 'center',
                gap: 6,
              }}>
                <span style={{ display: 'inline-block', width: 6, height: 6, background: 'var(--ink-amber)', animation: 'blink 1s step-end infinite' }} />
                PROCESSING
              </span>
            )}
            <button
              onClick={handleRunBatch}
              disabled={loading}
              style={{
                background: loading ? 'var(--surface-2)' : 'var(--text)',
                color: loading ? 'var(--text-muted)' : 'var(--bg)',
                border: 'none',
                borderRadius: 0,
                padding: '10px 20px',
                fontFamily: 'var(--font-display)',
                fontWeight: 600,
                fontSize: 13,
                letterSpacing: '0.04em',
                textTransform: 'uppercase',
                cursor: loading ? 'not-allowed' : 'pointer',
                transition: 'all var(--transition-fast)',
              }}
            >
              {loading ? '⏳ RUNNING…' : '→ RUN BATCH'}
            </button>
          </div>
        </div>
        {/* Thin ruled line at bottom */}
        <div style={{ height: 1, background: 'var(--border)' }} />
      </header>

      <LedgerTape attempts={attempts} />

      {/* ── Batch Progress ── */}
      {batchProgress && (
        <div style={{ borderBottom: 'var(--rule)', background: 'var(--surface)', padding: '0 40px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 0', fontSize: 12 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
              <span style={{
                fontFamily: 'var(--font-mono)',
                color: loading ? 'var(--ink-amber)' : 'var(--ink-green)',
                display: 'flex', alignItems: 'center', gap: 5,
              }}>
                <span style={{ width: 5, height: 5, background: loading ? 'var(--ink-amber)' : 'var(--ink-green)', animation: loading ? 'blink 0.8s step-end infinite' : 'none' }} />
                {loading ? 'PROCESSING' : 'COMPLETE'}
              </span>
              <span style={{ fontFamily: 'var(--font-mono)', color: 'var(--text-muted)' }}>
                {batchProgress.processed}/{batchProgress.total}
              </span>
            </div>
            <span style={{ fontFamily: 'var(--font-mono)', fontWeight: 600, color: 'var(--ink-green)' }}>
              +₹{batchProgress.recoveredAmount.toLocaleString('en-IN')}
            </span>
          </div>
          <div style={{ height: 2, background: 'var(--surface-2)', marginBottom: 1 }}>
            <div style={{ height: '100%', width: `${(batchProgress.processed / batchProgress.total) * 100}%`, background: loading ? 'var(--ink-amber)' : 'var(--ink-green)', transition: 'width 0.2s linear' }} />
          </div>
        </div>
      )}

      <main style={{ padding: '20px 40px', display: 'flex', flexDirection: 'column', gap: 20, flex: 1, maxWidth: 1400, margin: '0 auto', width: '100%' }}>

        {/* ══ SECTION 1: BOUNDS REGISTER ══ — must be above the fold, before any chart */}
        <BoundsRegister />

        {error && (
          <div className="animate-in" style={{
            background: 'var(--red-bg)',
            border: '1px solid var(--ink-red)',
            color: 'var(--ink-red)',
            padding: '10px 14px',
            fontSize: 12,
            fontFamily: 'var(--font-mono)',
            fontWeight: 500,
          }}>
            ✕ {error}
          </div>
        )}

        {/* ══ SECTION 2: SUMMARY STATEMENT ══ — ledger line-items */}
        <div style={{ display: 'flex', gap: 0, flexWrap: 'wrap' }}>
          <StatCard label="Transactions at risk" value={metrics?.totalAtRisk ?? '—'} />
          <StatCard label="Recovered" value={metrics?.recoveredCount ?? '—'} accent="green" />
          <StatCard label="Recovery rate" value={metrics ? `${metrics.recoveryRatePercent}%` : '—'} accent="blue" />
          <StatCard
            label="Net revenue"
            value={metrics ? `₹${Number(metrics.netRecovered).toLocaleString('en-IN')}` : '—'}
            accent="gold"
            sub={metrics ? `₹${Number(metrics.revenueRecovered).toLocaleString('en-IN')} recovered − ₹${Number(metrics.interventionCost).toFixed(0)} cost` : undefined}
          />
        </div>

        {/* ══ SECTION 3: NET RECOVERED VS BASELINE ══ — the brief's headline chart */}
        {metrics && <RecoveryChart netRecovered={metrics.netRecovered} baseline={metrics.baselineNetRecovered} />}

        {/* ══ SECTION 4: FUNNEL + ACTION BREAKDOWN ══ */}
        <div style={{ display: 'flex', gap: 0 }}>
          <div style={{ flex: 1, borderRight: 'var(--rule)' }}><FunnelChart key={funnelRefresh} /></div>
          <div style={{ flex: 1 }}><ActionBreakdownChart key={funnelRefresh} /></div>
        </div>

        {/* ══ SECTION 5: PENDING HUMAN REVIEW ══ — proof of bounded workflow */}
        <PendingReview key={funnelRefresh} />

        {/* ══ SECTION 6: DECISION LEDGER ══ */}
        <div>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
              <span style={{
                fontFamily: 'var(--font-display)',
                fontSize: 13,
                fontWeight: 600,
                letterSpacing: '0.06em',
                textTransform: 'uppercase',
                color: 'var(--text)',
              }}>
                Decision Ledger
              </span>
              {streamCount !== null && (
                <span style={{
                  fontFamily: 'var(--font-mono)',
                  fontSize: 11,
                  color: 'var(--text-muted)',
                  border: 'var(--rule)',
                  padding: '1px 6px',
                }}>
                  {streamCount} entries
                </span>
              )}
            </div>
            <button
              onClick={exportCsv}
              style={{
                background: 'transparent',
                color: 'var(--text-muted)',
                border: 'var(--rule)',
                borderRadius: 0,
                padding: '5px 12px',
                fontFamily: 'var(--font-display)',
                fontSize: 11,
                fontWeight: 500,
                letterSpacing: '0.04em',
                textTransform: 'uppercase',
                cursor: 'pointer',
                transition: 'all var(--transition-fast)',
              }}
              onMouseEnter={(e) => { e.currentTarget.style.borderColor = 'var(--text)'; e.currentTarget.style.color = 'var(--text)'; }}
              onMouseLeave={(e) => { e.currentTarget.style.borderColor = 'var(--border)'; e.currentTarget.style.color = 'var(--text-muted)'; }}
            >
              ↓ EXPORT CSV
            </button>
          </div>
          <AttemptTable attempts={attempts} onSelectAttempt={setSelectedAttempt} />
        </div>
      </main>

      {/* ── Transaction Detail Modal ── */}
      {selectedAttempt && (
        <TransactionModal attempt={selectedAttempt} onClose={() => setSelectedAttempt(null)} />
      )}

      {/* ══ FOOTER / COLOPHON ══ */}
      <footer style={{ borderTop: '2px solid var(--text)', background: 'var(--surface)', padding: '12px 40px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <span style={{ fontFamily: 'var(--font-mono)', fontSize: 11, color: 'var(--text-muted)' }}>
            RulesEngine.enforceBounds() — every action validated before execution
          </span>
          <span style={{ fontFamily: 'var(--font-mono)', fontSize: 11, color: 'var(--text-muted)' }}>
            <a href="#" style={{ color: 'var(--ink-blue)', textDecoration: 'none' }} onClick={(e) => { e.preventDefault(); window.scrollTo({ top: 0, behavior: 'smooth' }); }}>↑ BACK TO BOUNDS REGISTER</a>
            {' · '}
            LLM proposes · Rules engine disposes
          </span>
        </div>
      </footer>
    </div>
  );
}
