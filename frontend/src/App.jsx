import { useState, useEffect, useCallback } from 'react';
import BoundsRegister from './components/BoundsRegister';
import StatCard from './components/StatCard';
import RecoveryChart from './components/RecoveryChart';
import FunnelChart from './components/FunnelChart';
import ActionBreakdownChart from './components/ActionBreakdownChart';
import AttemptTable from './components/AttemptTable';
import TransactionModal from './components/TransactionModal';
import PendingReview from './components/PendingReview';
import { fetchMetrics, runBatch, exportCsv } from './api';

const NAV_ITEMS = [
  { id: 'overview', label: 'Overview', icon: '⌂' },
  { id: 'bounds', label: 'Bound Register', icon: '⚙' },
  { id: 'transactions', label: 'Transactions', icon: '⇄' },
  { id: 'actions', label: 'Actions', icon: '⚡' },
  { id: 'ledger', label: 'Decision Ledger', icon: '☰' },
  { id: 'reports', label: 'Reports', icon: '▮' },
  { id: 'alerts', label: 'Alerts', icon: '🔔' },
  { id: 'settings', label: 'Settings', icon: '⚙' },
];

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
  const [activeNav, setActiveNav] = useState('overview');
  const [lastUpdated, setLastUpdated] = useState(null);

  const loadMetrics = useCallback(async () => {
    try {
      const m = await fetchMetrics();
      setMetrics(m);
      setLastUpdated(new Date());
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
    <div style={{ display: 'flex', minHeight: '100vh', background: 'var(--bg)' }}>

      {/* ══════ SIDEBAR ══════ */}
      <aside style={{
        width: 240,
        background: 'var(--sidebar-bg)',
        display: 'flex',
        flexDirection: 'column',
        flexShrink: 0,
        position: 'fixed',
        top: 0,
        left: 0,
        bottom: 0,
        zIndex: 100,
      }}>
        {/* Logo */}
        <div style={{
          padding: '24px 20px',
          borderBottom: '1px solid rgba(255,255,255,0.08)',
          display: 'flex',
          alignItems: 'center',
          gap: 12,
        }}>
          <div style={{
            width: 36,
            height: 36,
            borderRadius: 'var(--radius-sm)',
            background: 'var(--green)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: 'white',
            fontWeight: 700,
            fontSize: 16,
            fontFamily: 'var(--font-display)',
          }}>
            ₹
          </div>
          <div>
            <div style={{
              fontFamily: 'var(--font-display)',
              fontWeight: 700,
              fontSize: 15,
              color: '#FFFFFF',
              lineHeight: 1.2,
            }}>
              Recovery Ledger
            </div>
            <div style={{
              fontFamily: 'var(--font-body)',
              fontSize: 11,
              color: 'var(--sidebar-text)',
            }}>
              Batch Operations
            </div>
          </div>
        </div>

        {/* Nav */}
        <nav style={{ flex: 1, padding: '12px 8px', display: 'flex', flexDirection: 'column', gap: 2 }}>
          {NAV_ITEMS.map((item) => (
            <button
              key={item.id}
              onClick={() => setActiveNav(item.id)}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 10,
                padding: '10px 12px',
                borderRadius: 'var(--radius-sm)',
                border: 'none',
                background: activeNav === item.id ? 'var(--sidebar-active-bg)' : 'transparent',
                color: activeNav === item.id ? 'var(--sidebar-active)' : 'var(--sidebar-text)',
                fontFamily: 'var(--font-body)',
                fontSize: 13,
                fontWeight: activeNav === item.id ? 600 : 400,
                cursor: 'pointer',
                textAlign: 'left',
                width: '100%',
                transition: 'all var(--transition-fast)',
              }}
              onMouseEnter={(e) => {
                if (activeNav !== item.id) {
                  e.currentTarget.style.background = 'rgba(255,255,255,0.05)';
                  e.currentTarget.style.color = '#FFFFFF';
                }
              }}
              onMouseLeave={(e) => {
                if (activeNav !== item.id) {
                  e.currentTarget.style.background = 'transparent';
                  e.currentTarget.style.color = 'var(--sidebar-text)';
                }
              }}
            >
              <span style={{ fontSize: 16, width: 20, textAlign: 'center' }}>{item.icon}</span>
              {item.label}
            </button>
          ))}
        </nav>

        {/* AI Model Status */}
        <div style={{
          padding: '16px 20px',
          borderTop: '1px solid rgba(255,255,255,0.08)',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
        }}>
          <div>
            <div style={{ fontFamily: 'var(--font-body)', fontSize: 11, color: 'var(--sidebar-text)' }}>AI Model Status</div>
            <div style={{ fontFamily: 'var(--font-mono)', fontSize: 10, color: 'var(--sidebar-text)', opacity: 0.6 }}>Model v2.1.4</div>
          </div>
          <span style={{
            display: 'flex',
            alignItems: 'center',
            gap: 5,
            fontFamily: 'var(--font-body)',
            fontSize: 11,
            fontWeight: 600,
            color: 'var(--green)',
          }}>
            <span style={{ width: 7, height: 7, borderRadius: '50%', background: 'var(--green)', display: 'inline-block' }} />
            Active
          </span>
        </div>
      </aside>

      {/* ══════ MAIN CONTENT ══════ */}
      <div style={{ flex: 1, marginLeft: 240, display: 'flex', flexDirection: 'column', height: '100vh', overflow: 'hidden' }}>

        {/* ── Top Header ── */}
        <header style={{
          background: 'var(--surface)',
          borderBottom: '1px solid var(--border)',
          padding: '20px 32px',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
        }}>
          <div>
            <h1 style={{
              fontFamily: 'var(--font-display)',
              fontSize: 24,
              fontWeight: 700,
              color: 'var(--text)',
              lineHeight: 1.2,
            }}>
              Revenue Recovery Agent
            </h1>
            <div style={{
              fontFamily: 'var(--font-body)',
              fontSize: 13,
              color: 'var(--text-muted)',
              marginTop: 2,
            }}>
              Razorpay AI Buildathon · Track 03 · AI Revenue Recovery
            </div>
          </div>
          <div style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
            {reviewCount > 0 && (
              <span style={{
                fontFamily: 'var(--font-body)',
                fontSize: 12,
                fontWeight: 600,
                color: 'var(--ink-amber)',
                background: 'var(--amber-bg)',
                border: '1px solid #FDBA74',
                borderRadius: 'var(--radius-full)',
                padding: '6px 14px',
                display: 'flex',
                alignItems: 'center',
                gap: 6,
              }}>
                <span style={{ fontSize: 10 }}>⚠</span>
                {reviewCount} PENDING REVIEW
              </span>
            )}
            <button
              onClick={handleRunBatch}
              disabled={loading}
              style={{
                background: loading ? 'var(--text-muted)' : 'var(--green)',
                color: 'white',
                border: 'none',
                borderRadius: 'var(--radius-sm)',
                padding: '10px 20px',
                fontFamily: 'var(--font-display)',
                fontWeight: 600,
                fontSize: 14,
                cursor: loading ? 'not-allowed' : 'pointer',
                display: 'flex',
                alignItems: 'center',
                gap: 8,
                transition: 'all var(--transition-fast)',
                boxShadow: loading ? 'none' : '0 2px 8px rgba(34, 197, 94, 0.3)',
              }}
              onMouseEnter={(e) => { if (!loading) e.currentTarget.style.background = 'var(--green-dark)'; }}
              onMouseLeave={(e) => { if (!loading) e.currentTarget.style.background = 'var(--green)'; }}
            >
              {loading ? '⏳ Running…' : 'Run Batch'}
              {!loading && <span style={{ fontSize: 12 }}>▶</span>}
            </button>
          </div>
        </header>

        {/* ── Status Bar ── */}
        <div style={{
          background: 'var(--surface)',
          borderBottom: '1px solid var(--border)',
          padding: '10px 32px',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          fontSize: 13,
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span style={{ width: 8, height: 8, borderRadius: '50%', background: attempts.length > 0 ? 'var(--green)' : 'var(--text-muted)' }} />
            <span style={{ color: 'var(--text-secondary)' }}>
              {attempts.length > 0
                ? `${streamCount || attempts.length} transactions processed`
                : 'Awaiting batch run — data will appear here'}
            </span>
          </div>
          {lastUpdated && (
            <span style={{ color: 'var(--text-muted)', fontSize: 12 }}>
              Last updated: {lastUpdated.toLocaleTimeString()} · 🔄
            </span>
          )}
        </div>

        {/* ── Scrollable Content ── */}
        <main style={{ padding: '24px 32px', flex: 1, overflowY: 'auto', minWidth: 0 }}>

          {/* Error Banner */}
          {error && (
            <div className="animate-in" style={{
              background: 'var(--red-bg)',
              border: '1px solid var(--ink-red)',
              borderRadius: 'var(--radius-sm)',
              color: 'var(--ink-red)',
              padding: '12px 16px',
              fontSize: 13,
              fontWeight: 500,
              marginBottom: 16,
              display: 'flex',
              alignItems: 'center',
              gap: 8,
            }}>
              <span>✕</span> {error}
            </div>
          )}

          {/* ══ SECTION 1: STAT CARDS ══ */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 16, marginBottom: 24 }}>
            <StatCard
              label="TRANSACTIONS AT RISK"
              value={metrics?.totalAtRisk ?? '—'}
              sub="Total flagged"
              icon="⚠"
              iconBg="#FEF3C7"
              iconColor="#F59E0B"
            />
            <StatCard
              label="RECOVERED"
              value={metrics?.recoveredCount ?? '—'}
              sub="Successful recoveries"
              icon="↻"
              iconBg={metrics?.recoveredCount > 0 ? 'var(--green-bg)' : '#F1F5F9'}
              iconColor={metrics?.recoveredCount > 0 ? 'var(--green)' : 'var(--text-muted)'}
            />
            <StatCard
              label="RECOVERY RATE"
              value={metrics ? `${metrics.recoveryRatePercent}%` : '—'}
              sub="Recovery success rate"
              icon="▮"
              iconBg="var(--blue-bg)"
              iconColor="var(--ink-blue)"
            />
            <StatCard
              label="NET REVENUE"
              value={metrics ? `₹${Number(metrics.netRecovered).toLocaleString('en-IN')}` : '—'}
              sub={metrics ? `₹${Number(metrics.revenueRecovered).toLocaleString('en-IN')} recovered · ₹${Number(metrics.interventionCost).toFixed(0)} cost` : ''}
              icon="₹"
              iconBg="var(--green-bg)"
              iconColor="var(--green)"
              valueColor="var(--green)"
            />
          </div>

          {/* ══ SECTION 2: CHARTS ROW ══ */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16, marginBottom: 24 }}>
            {/* Net Recovered vs Baseline */}
            {metrics && <RecoveryChart netRecovered={metrics.netRecovered} baseline={metrics.baselineNetRecovered} />}

            {/* Bounds Register */}
            <BoundsRegister />
          </div>

          {/* ══ SECTION 3: FUNNEL + ACTION ══ */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16, marginBottom: 24 }}>
            <FunnelChart key={funnelRefresh} />
            <ActionBreakdownChart key={funnelRefresh} />
          </div>

          {/* ══ SECTION 4: ALLOWED ACTIONS ══ */}
          <div className="card" style={{ marginBottom: 24 }}>
            <div style={{
              fontFamily: 'var(--font-display)',
              fontSize: 13,
              fontWeight: 700,
              letterSpacing: '0.04em',
              textTransform: 'uppercase',
              color: 'var(--text)',
              marginBottom: 16,
            }}>
              ALLOWED ACTIONS — LLM MAY ONLY PICK FROM THIS LIST
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(5, 1fr)', gap: 12 }}>
              {[
                { action: 'Retry Now', desc: 'Immediate retry', color: 'var(--green)', bg: 'var(--green-bg)', icon: '↻' },
                { action: 'Retry Scheduled', desc: 'After cooldown', color: 'var(--ink-blue)', bg: 'var(--blue-bg)', icon: '⏱' },
                { action: 'Send Payment Link', desc: 'Update payment', color: 'var(--ink-amber)', bg: 'var(--amber-bg)', icon: '🔗' },
                { action: 'Offer Discount', desc: 'Max 15%', color: 'var(--ink-purple)', bg: '#F5F3FF', icon: '%' },
                { action: 'Escalate to Human', desc: 'Collections team', color: 'var(--ink-red)', bg: 'var(--red-bg)', icon: '👤' },
              ].map((a) => (
                <div key={a.action} style={{
                  padding: '14px',
                  borderRadius: 'var(--radius-sm)',
                  border: '1px solid var(--border)',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 12,
                }}>
                  <div style={{
                    width: 36,
                    height: 36,
                    borderRadius: 'var(--radius-sm)',
                    background: a.bg,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: 16,
                    flexShrink: 0,
                  }}>
                    {a.icon}
                  </div>
                  <div>
                    <div style={{ fontFamily: 'var(--font-body)', fontSize: 13, fontWeight: 600, color: 'var(--text)' }}>{a.action}</div>
                    <div style={{ fontFamily: 'var(--font-body)', fontSize: 11, color: 'var(--text-muted)' }}>{a.desc}</div>
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* ══ SECTION 5: PENDING HUMAN REVIEW ══ */}
          <PendingReview key={funnelRefresh} />

          {/* ══ SECTION 6: DECISION LEDGER ══ */}
          <div className="card" style={{ marginTop: 24 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                <span style={{
                  fontFamily: 'var(--font-display)',
                  fontSize: 15,
                  fontWeight: 700,
                  color: 'var(--text)',
                }}>
                  Decision Ledger
                </span>
                {streamCount !== null && (
                  <span style={{
                    fontFamily: 'var(--font-mono)',
                    fontSize: 11,
                    color: 'var(--text-muted)',
                    background: 'var(--surface-2)',
                    borderRadius: 'var(--radius-full)',
                    padding: '3px 10px',
                  }}>
                    {streamCount} entries
                  </span>
                )}
              </div>
              <button
                onClick={exportCsv}
                style={{
                  background: 'transparent',
                  color: 'var(--text-secondary)',
                  border: '1px solid var(--border)',
                  borderRadius: 'var(--radius-sm)',
                  padding: '7px 14px',
                  fontFamily: 'var(--font-body)',
                  fontSize: 12,
                  fontWeight: 500,
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 6,
                  transition: 'all var(--transition-fast)',
                }}
                onMouseEnter={(e) => { e.currentTarget.style.borderColor = 'var(--green)'; e.currentTarget.style.color = 'var(--green)'; }}
                onMouseLeave={(e) => { e.currentTarget.style.borderColor = 'var(--border)'; e.currentTarget.style.color = 'var(--text-secondary)'; }}
              >
                ↓ Export CSV
              </button>
            </div>
            <AttemptTable attempts={attempts} onSelectAttempt={setSelectedAttempt} />
          </div>
        </main>

        {/* ── Footer ── */}
        <footer style={{
          borderTop: '1px solid var(--border)',
          background: 'var(--surface)',
          padding: '14px 32px',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
        }}>
          <span style={{ fontFamily: 'var(--font-mono)', fontSize: 12, color: 'var(--text-muted)' }}>
            RulesEngine.enforceBounds() — every action validated before execution
          </span>
          <span style={{ fontFamily: 'var(--font-mono)', fontSize: 12, color: 'var(--text-muted)' }}>
            <span style={{ color: 'var(--green)' }}>●</span> LLM proposes · <span style={{ color: 'var(--ink-red)' }}>●</span> Rules engine disposes
          </span>
        </footer>
      </div>

      {/* ── Transaction Detail Modal ── */}
      {selectedAttempt && (
        <TransactionModal attempt={selectedAttempt} onClose={() => setSelectedAttempt(null)} />
      )}
    </div>
  );
}
