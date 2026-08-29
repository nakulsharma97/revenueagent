import { useState, useEffect, useCallback } from 'react';
import LedgerTape from './components/LedgerTape';
import StatCard from './components/StatCard';
import RecoveryChart from './components/RecoveryChart';
import FunnelChart from './components/FunnelChart';
import ActionBreakdownChart from './components/ActionBreakdownChart';
import AttemptTable from './components/AttemptTable';
import TransactionModal from './components/TransactionModal';
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

  const loadMetrics = useCallback(async () => {
    try {
      const m = await fetchMetrics();
      setMetrics(m);
      setError(null);
    } catch (e) {
      setError('Backend not reachable. Start the Spring Boot app on :8080, then reload.');
    }
  }, []);

  useEffect(() => { loadMetrics(); }, [loadMetrics, funnelRefresh]);

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
      // Calculate final recovered amount
      const finalRecovered = reversed.filter(a => a.outcome === 'SUCCESS').reduce((sum, a) => sum + (a.amountRecovered || 0), 0);
      setBatchProgress(prev => prev ? { ...prev, processed: result.length, recoveredAmount: finalRecovered } : null);
      await loadMetrics();
      setFunnelRefresh((n) => n + 1);
      setError(null);
    } catch (e) {
      setError('Batch run failed — check the backend logs.');
    } finally {
      setLoading(false);
      // Keep progress visible for 2 seconds after completion
      setTimeout(() => setBatchProgress(null), 2000);
    }
  }

  return (
    <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', background: 'var(--bg)' }}>

      {/* ── Header ── */}
      <header style={{
        padding: '0 0 0 0',
        borderBottom: '1px solid var(--border)',
        background: 'var(--surface)',
      }}>
        {/* Top bar */}
        <div style={{
          padding: '12px 40px',
          borderBottom: '1px solid var(--border)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          background: 'var(--navy)',
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            {/* Razorpay-style logo */}
            <div style={{
              width: 28,
              height: 28,
              background: '#2D89EF',
              borderRadius: 6,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}>
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                <path d="M12 2v20M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"/>
              </svg>
            </div>
            <span style={{
              fontSize: 13,
              fontWeight: 600,
              color: 'white',
              letterSpacing: '0.01em',
            }}>
              Revenue Recovery Agent
            </span>
          </div>
          <div style={{
            display: 'flex',
            alignItems: 'center',
            gap: 16,
            fontSize: 12,
            color: 'rgba(255,255,255,0.6)',
          }}>
            <span>Razorpay AI Buildathon</span>
            <span style={{
              padding: '3px 8px',
              background: 'rgba(255,255,255,0.1)',
              borderRadius: 4,
              fontSize: 11,
              fontWeight: 500,
              color: 'rgba(255,255,255,0.8)',
            }}>
              Track 03
            </span>
          </div>
        </div>

        {/* Hero area */}
        <div style={{
          padding: '28px 40px',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
        }}>
          <div>
            <h1 style={{
              fontFamily: 'var(--font-display)',
              fontSize: 26,
              margin: 0,
              fontWeight: 700,
              color: 'var(--text)',
              letterSpacing: '-0.02em',
            }}>
              AI Revenue Recovery
            </h1>
            <p style={{
              margin: '4px 0 0',
              fontSize: 14,
              color: 'var(--text-secondary)',
              maxWidth: 480,
              lineHeight: 1.5,
            }}>
              Detects at-risk payments, decides the right intervention, and executes within hard-coded bounds. Every action passes through the rules engine.
            </p>
          </div>

          <div style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
            {loading && (
              <div style={{
                display: 'flex',
                alignItems: 'center',
                gap: 8,
                fontSize: 13,
                color: 'var(--teal)',
                fontWeight: 500,
              }}>
                <div style={{
                  width: 8,
                  height: 8,
                  borderRadius: '50%',
                  background: 'var(--teal)',
                  animation: 'pulse 1.5s ease-in-out infinite',
                }} />
                Processing batch…
              </div>
            )}
            <button
              onClick={handleRunBatch}
              disabled={loading}
              style={{
                background: loading ? 'var(--surface-2)' : 'var(--navy)',
                color: loading ? 'var(--text-muted)' : 'white',
                border: loading ? '1px solid var(--border)' : 'none',
                borderRadius: 8,
                padding: '12px 24px',
                fontFamily: 'var(--font-body)',
                fontWeight: 600,
                fontSize: 14,
                cursor: loading ? 'not-allowed' : 'pointer',
                transition: 'all var(--transition-fast)',
                display: 'flex',
                alignItems: 'center',
                gap: 8,
              }}
              onMouseEnter={(e) => {
                if (!loading) {
                  e.currentTarget.style.background = 'var(--navy-light)';
                }
              }}
              onMouseLeave={(e) => {
                if (!loading) {
                  e.currentTarget.style.background = 'var(--navy)';
                }
              }}
            >
              {loading ? (
                <>
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" style={{ animation: 'pulse 1s ease infinite' }}>
                    <circle cx="12" cy="12" r="10" strokeDasharray="31" strokeDashoffset="10"/>
                  </svg>
                  Running…
                </>
              ) : (
                <>
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor">
                    <polygon points="5,3 19,12 5,21"/>
                  </svg>
                  Run Recovery Batch
                </>
              )}
            </button>
          </div>
        </div>
      </header>

      <LedgerTape attempts={attempts} />

      {/* ── Batch Progress Bar ── */}
      {batchProgress && (
        <div style={{
          background: 'var(--surface)',
          borderBottom: '1px solid var(--border)',
          padding: '0 40px',
        }}>
          <div style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            padding: '10px 0',
            fontSize: 13,
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
              <div style={{
                width: 8,
                height: 8,
                borderRadius: '50%',
                background: loading ? 'var(--teal)' : 'var(--green)',
                animation: loading ? 'pulse 1s ease infinite' : 'none',
              }} />
              <span style={{ color: 'var(--text-secondary)', fontWeight: 500 }}>
                {loading ? 'Processing batch…' : 'Batch complete'}
              </span>
              <span style={{ fontFamily: 'var(--font-mono)', fontSize: 12, color: 'var(--text-muted)' }}>
                {batchProgress.processed} / {batchProgress.total} transactions
              </span>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
              <span style={{ fontFamily: 'var(--font-mono)', fontSize: 13, color: 'var(--green)', fontWeight: 600 }}>
                +₹{batchProgress.recoveredAmount.toLocaleString('en-IN')} recovered
              </span>
            </div>
          </div>
          {/* Progress bar */}
          <div style={{
            height: 3,
            background: 'var(--surface-2)',
            borderRadius: 2,
            overflow: 'hidden',
            marginBottom: 2,
          }}>
            <div style={{
              height: '100%',
              width: `${(batchProgress.processed / batchProgress.total) * 100}%`,
              background: loading ? 'var(--teal)' : 'var(--green)',
              borderRadius: 2,
              transition: 'width 0.3s ease',
            }} />
          </div>
        </div>
      )}

      <main style={{ padding: '28px 40px', display: 'flex', flexDirection: 'column', gap: 24, flex: 1, maxWidth: 1400, margin: '0 auto', width: '100%' }}>

        {error && (
          <div className="animate-in" style={{
            background: 'var(--red-bg)',
            border: '1px solid #FECACA',
            color: 'var(--red)',
            padding: '12px 16px',
            borderRadius: 8,
            fontSize: 13,
            fontWeight: 500,
            display: 'flex',
            alignItems: 'center',
            gap: 8,
          }}>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/>
            </svg>
            {error}
          </div>
        )}

        {/* ── Stat Cards ── */}
        <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap' }}>
          <StatCard
            label="At-risk transactions"
            value={metrics?.totalAtRisk ?? '—'}
          />
          <StatCard
            label="Recovered"
            value={metrics?.recoveredCount ?? '—'}
            accent="green"
          />
          <StatCard
            label="Recovery rate"
            value={metrics ? `${metrics.recoveryRatePercent}%` : '—'}
            accent="blue"
          />
          <StatCard
            label="Net revenue recovered"
            value={metrics ? `₹${Number(metrics.netRecovered).toLocaleString('en-IN')}` : '—'}
            accent="gold"
            sub={metrics ? `₹${Number(metrics.revenueRecovered).toLocaleString('en-IN')} recovered − ₹${Number(metrics.interventionCost).toFixed(0)} cost` : undefined}
          />
        </div>

        {/* ── Main Chart ── */}
        {metrics && <RecoveryChart netRecovered={metrics.netRecovered} baseline={metrics.baselineNetRecovered} />}

        {/* ── Two-column charts ── */}
        <div style={{ display: 'flex', gap: 20 }}>
          <div style={{ flex: 1 }}><FunnelChart key={funnelRefresh} /></div>
          <div style={{ flex: 1 }}><ActionBreakdownChart key={funnelRefresh} /></div>
        </div>

        {/* ── Decision Table ── */}
        <div>
          <div style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            marginBottom: 12,
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
              <div style={{
                fontFamily: 'var(--font-display)',
                fontSize: 16,
                fontWeight: 600,
                color: 'var(--text)',
              }}>
                Agent decisions this batch
              </div>
              {streamCount !== null && (
                <span style={{
                  fontFamily: 'var(--font-mono)',
                  fontSize: 11,
                  color: 'var(--text-muted)',
                  background: 'var(--surface-2)',
                  padding: '2px 8px',
                  borderRadius: 4,
                }}>
                  {streamCount} processed
                </span>
              )}
            </div>
            <button
              onClick={exportCsv}
              style={{
                background: 'transparent',
                color: 'var(--text-secondary)',
                border: '1px solid var(--border)',
                borderRadius: 6,
                padding: '6px 12px',
                fontFamily: 'var(--font-body)',
                fontSize: 12,
                fontWeight: 500,
                cursor: 'pointer',
                transition: 'all var(--transition-fast)',
                display: 'flex',
                alignItems: 'center',
                gap: 6,
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.borderColor = 'var(--border-hover)';
                e.currentTarget.style.background = 'var(--surface-hover)';
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.borderColor = 'var(--border)';
                e.currentTarget.style.background = 'transparent';
              }}
            >
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/>
              </svg>
              Export CSV
            </button>
          </div>
          <AttemptTable attempts={attempts} onSelectAttempt={setSelectedAttempt} />
        </div>
      </main>

      {/* ── Transaction Detail Modal ── */}
      {selectedAttempt && (
        <TransactionModal attempt={selectedAttempt} onClose={() => setSelectedAttempt(null)} />
      )}

      {/* ── Footer ── */}
      <footer style={{
        padding: '16px 40px',
        borderTop: '1px solid var(--border)',
        background: 'var(--surface)',
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
      }}>
        <span style={{
          fontSize: 12,
          color: 'var(--text-muted)',
        }}>
          Every action passed through{' '}
          <span style={{ fontFamily: 'var(--font-mono)', color: 'var(--text-secondary)', fontSize: 11 }}>
            RulesEngine.enforceBounds()
          </span>
          {' '}before executing
        </span>
        <span style={{
          fontSize: 11,
          color: 'var(--text-muted)',
          fontFamily: 'var(--font-mono)',
        }}>
          The LLM proposes · The rules engine disposes
        </span>
      </footer>
    </div>
  );
}
