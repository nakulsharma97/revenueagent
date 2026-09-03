import { useState, useEffect, useCallback } from 'react';
import BoundsRegister from './components/BoundsRegister';
import StatCard from './components/StatCard';
import RecoveryChart from './components/RecoveryChart';
import FunnelChart from './components/FunnelChart';
import ActionBreakdownChart from './components/ActionBreakdownChart';
import AttemptTable from './components/AttemptTable';
import TransactionModal from './components/TransactionModal';
import PendingReview from './components/PendingReview';
import LedgerTape from './components/LedgerTape';
import { fetchDashboardSummary, fetchHeldOutMetrics, runBatch, runBatchStream, exportCsv, fetchUplift, fetchAttempts } from './api';

const API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8080';

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

const FW = { width: '100%', minWidth: 0 };

/* Reusable page header */
function PageHeader({ title, subtitle, right }) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', marginBottom: 24, ...FW }}>
      <div>
        <h2 style={{ fontFamily: 'var(--font-body)', fontSize: 20, fontWeight: 700, color: 'var(--text)', lineHeight: 1.3 }}>{title}</h2>
        {subtitle && <p style={{ fontFamily: 'var(--font-body)', fontSize: 13, color: 'var(--text-muted)', marginTop: 4 }}>{subtitle}</p>}
      </div>
      {right && <div style={{ flexShrink: 0 }}>{right}</div>}
    </div>
  );
}

/* Reusable summary stat block */
function SummaryStat({ label, value, color }) {
  return (
    <div style={{ flex: 1, minWidth: 0, padding: '14px 18px', background: 'var(--bg-secondary)', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border)' }}>
      <div style={{ fontFamily: 'var(--font-body)', fontSize: 10, fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.05em', color: 'var(--text-muted)', marginBottom: 6 }}>{label}</div>
      <div style={{ fontFamily: 'var(--font-mono)', fontSize: 22, fontWeight: 700, color: color || 'var(--text)', lineHeight: 1.2 }}>{value}</div>
    </div>
  );
}

/* Reusable empty state */
function EmptyState({ icon, title, description, action, onAction }) {
  return (
    <div className="card" style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', padding: '64px 40px', ...FW }}>
      <div style={{ fontSize: 44, marginBottom: 16, opacity: 0.15 }}>{icon}</div>
      <div style={{ fontFamily: 'var(--font-body)', fontSize: 17, fontWeight: 600, color: 'var(--text)', marginBottom: 8 }}>{title}</div>
      <div style={{ fontFamily: 'var(--font-body)', fontSize: 13, color: 'var(--text-muted)', textAlign: 'center', maxWidth: 440, lineHeight: 1.6, marginBottom: action ? 20 : 0 }}>{description}</div>
      {action && (
        <button onClick={onAction} style={{ background: 'var(--gold)', color: 'var(--text-inverse)', border: 'none', borderRadius: 'var(--radius-sm)', padding: '10px 24px', fontFamily: 'var(--font-body)', fontWeight: 600, fontSize: 13, cursor: 'pointer', transition: 'all var(--transition-fast)' }}
          onMouseEnter={e => e.currentTarget.style.background = 'var(--gold-bright)'}
          onMouseLeave={e => e.currentTarget.style.background = 'var(--gold)'}
        >{action}</button>
      )}
    </div>
  );
}

/* Skeleton loader for reports */
function SkeletonCard({ lines = 3, height }) {
  return (
    <div className="card" style={{ ...FW, minHeight: height }}>
      <div className="skeleton skeleton-line short" style={{ marginBottom: 12 }} />
      <div className="skeleton skeleton-line long" style={{ marginBottom: 8 }} />
      <div className="skeleton skeleton-line medium" style={{ marginBottom: 8 }} />
      {lines > 2 && <div className="skeleton skeleton-line long" />}
    </div>
  );
}

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
  const [retryCount, setRetryCount] = useState(0);
  const [actionLog, setActionLog] = useState([]);
  const [allTransactions, setAllTransactions] = useState([]);
  const [txLoadError, setTxLoadError] = useState(null);
  const [rcvLoadError, setRcvLoadError] = useState(null);
  const [attemptsLoadError, setAttemptsLoadError] = useState(null);
  const [alerts, setAlerts] = useState([]);
  const [boundsConfig, setBoundsConfig] = useState(null);
  const [settingsLocal, setSettingsLocal] = useState({});
  const [settingsSaving, setSettingsSaving] = useState(false);
  const [funnelData, setFunnelData] = useState(null);
  const [actionData, setActionData] = useState([]);
  const [efficiencyData, setEfficiencyData] = useState([]);
  const [allReceivables, setAllReceivables] = useState([]);
  const [simResult, setSimResult] = useState(null);
  const [simLoading, setSimLoading] = useState(false);
  const [heldOutMetrics, setHeldOutMetrics] = useState(null);
  const [upliftData, setUpliftData] = useState(null);

  useEffect(() => { if (boundsConfig) setSettingsLocal(boundsConfig); }, [boundsConfig]);

  async function saveSettings() {
    setSettingsSaving(true);
    try {
      const res = await fetch(`${API_BASE}/api/config/bounds`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(settingsLocal) });
      if (res.ok) { const u = await res.json(); setBoundsConfig(u); setSettingsLocal(u); }
    } finally { setSettingsSaving(false); }
  }

  /** Single round-trip: loads metrics + funnel + actions + efficiency. */
  const loadDashboard = useCallback(async () => {
    try {
      const [d, h, u] = await Promise.all([fetchDashboardSummary(), fetchHeldOutMetrics(), fetchUplift().catch(() => null)]);
      setMetrics(d.metrics); setFunnelData(d.funnel); setActionData(d.actions); setEfficiencyData(d.efficiency);
      setHeldOutMetrics(h); setUpliftData(u);
      setLastUpdated(new Date()); setError(null); setRetryCount(0);
    } catch (e) {
      if (retryCount < 3) setTimeout(() => { setRetryCount(c => c + 1); loadDashboard(); }, 2000);
      else setError('Cannot reach the recovery engine on :8080 — make sure the Spring Boot backend is running, then refresh.');
    }
  }, [retryCount]);

  const loadReviewCount = useCallback(async () => {
    try { const res = await fetch(`${API_BASE}/api/recovery/pending-review`); if (res.ok) { const data = await res.json(); setReviewCount(data.length); setAlerts(data); } } catch (e) {}
  }, []);

  const loadActionLog = useCallback(async () => {
    try {
      const res = await fetch(`${API_BASE}/api/recovery/transactions`);
      if (!res.ok) throw new Error(`transactions request failed (${res.status})`);
      setAllTransactions(await res.json());
      setTxLoadError(null);
    } catch (e) {
      setTxLoadError('Could not load transactions — the recovery engine may be unreachable or returned an error.');
    }
  }, []);

  const loadAttempts = useCallback(async () => {
    try {
      const data = await fetchAttempts();
      setAttempts(data);
      setAttemptsLoadError(null);
    } catch (e) {
      setAttemptsLoadError('Could not load recovery attempts — the recovery engine may be unreachable.');
    }
  }, []);

  const loadBoundsConfig = useCallback(async () => {
    try { const res = await fetch(`${API_BASE}/api/config/bounds`); if (res.ok) setBoundsConfig(await res.json()); } catch (e) {}
  }, []);

  const loadReceivables = useCallback(async () => {
    try {
      const res = await fetch(`${API_BASE}/api/recovery/receivables`);
      if (!res.ok) throw new Error(`receivables request failed (${res.status})`);
      setAllReceivables(await res.json());
      setRcvLoadError(null);
    } catch (e) {
      setRcvLoadError('Could not load receivables — the recovery engine may be unreachable or returned an error.');
    }
  }, []);

  async function simulateBounds() {
    setSimLoading(true);
    setSimResult(null);
    try {
      const params = new URLSearchParams({
        maxRetries: settingsLocal.maxRetries ?? 3,
        maxDiscountPercent: settingsLocal.maxDiscountPercent ?? 15,
        minAmountForDiscount: settingsLocal.minAmountForDiscount ?? 500,
        retryCooldownMinutes: settingsLocal.retryCooldownMinutes ?? 60,
      });
      const res = await fetch(`${API_BASE}/api/metrics/simulate?${params}`);
      if (res.ok) setSimResult(await res.json());
    } catch (e) { console.error('Simulation failed:', e); }
    finally { setSimLoading(false); }
  }

  useEffect(() => { loadDashboard(); loadReviewCount(); loadActionLog(); loadAttempts(); loadBoundsConfig(); loadReceivables(); }, []);
  useEffect(() => { if (funnelRefresh > 0) { loadDashboard(); loadReviewCount(); loadActionLog(); loadAttempts(); } }, [funnelRefresh]);

  async function handleRunBatch() {
    setLoading(true); setAttempts([]); setStreamCount(null);
    setBatchProgress({ processed: 0, total: 0, recoveredAmount: 0, startTime: Date.now() });
    try {
      // Use SSE streaming for real-time progress updates instead of blocking POST
      const allAttempts = [];
      const finalRecovered = { value: 0 };
      await new Promise((resolve, reject) => {
        const es = runBatchStream(
          // onAttempt: called for each recovery attempt as it completes
          (attempt) => {
            allAttempts.push(attempt);
            if (attempt.outcome === 'SUCCESS') finalRecovered.value += (attempt.amountRecovered || 0);
            setAttempts([...allAttempts].reverse());
            setStreamCount(allAttempts.length);
            setActionLog([...allAttempts]);
            setBatchProgress(prev => prev ? {
              ...prev,
              processed: allAttempts.length,
              recoveredAmount: finalRecovered.value,
            } : null);
          },
          // onDone: called when all attempts are processed — carries {processed, skipped, failed}
          (counts) => {
            if (!counts || counts.processed === -1) reject(new Error('SSE connection failed'));
            else resolve(counts);
          },
          // onTotal: called first with the total eligible item count
          (total) => {
            setBatchProgress(prev => prev ? { ...prev, total } : { processed: 0, total, recoveredAmount: 0, startTime: Date.now() });
          }
        );
        // Store EventSource ref for potential cleanup
        window.__batchES = es;
      });
      // All data already streamed — refresh dashboard metrics and persisted attempts
      await Promise.all([loadDashboard(), loadReviewCount(), loadActionLog(), loadAttempts()]);
      setError(null);
    } catch (e) {
      if (e.message?.includes('409') || e.message?.includes('already running')) {
        setError('A recovery batch is already in progress — wait for it to finish before starting another.');
      } else if (e.message?.includes('SSE') || e.message?.includes('Failed to fetch')) {
        // SSE failed, fall back to blocking POST
        try {
          const result = await runBatch();
          const reversed = result.reverse();
          setAttempts(reversed); setStreamCount(result.length); setActionLog(result);
          const finalRecovered = reversed.filter(a => a.outcome === 'SUCCESS').reduce((sum, a) => sum + (a.amountRecovered || 0), 0);
          setBatchProgress(prev => prev ? { ...prev, processed: result.length, recoveredAmount: finalRecovered } : null);
          await Promise.all([loadDashboard(), loadReviewCount(), loadActionLog(), loadAttempts()]);
          setError(null);
        } catch (fallbackErr) {
          setError(fallbackErr.message?.includes('409') ? 'Batch already running — wait for the current batch to complete.' : 'Batch run failed — check the backend logs.');
        }
      } else {
        setError('Recovery batch failed — check the backend console for details.');
      }
    } finally {
      if (window.__batchES) { try { window.__batchES.close(); } catch (_) {} window.__batchES = null; }
      setLoading(false);
      setTimeout(() => setBatchProgress(null), 5000);
    }
  }

  function fmt(v) { return v === null || v === undefined ? '—' : `₹${Number(v).toLocaleString('en-IN')}`; }
  function pct(v) { return v === null || v === undefined ? '—' : `${Number(v).toFixed(1)}%`; }

  // ═══ 1. OVERVIEW ═══
  function renderOverview() {
    return (<>
      {/* ROW 1: 4 stat cards */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 14, marginBottom: 16 }}>
        <StatCard label="TRANSACTIONS AT RISK" value={metrics?.totalAtRisk ?? '—'} sub="Total flagged" icon="⚠" iconBg="rgba(216,155,50,0.12)" iconColor="var(--amber)" />
        <StatCard label="RECOVERED" value={metrics?.recoveredCount ?? '—'} sub="Successful recoveries" icon="✓" iconBg="var(--green-bg)" iconColor="var(--green)" />
        <StatCard label="RECOVERY RATE" value={metrics ? pct(metrics.recoveryRatePercent) : '—'} sub="Recovery success rate" icon="▮" iconBg="var(--gold-bg)" iconColor="var(--gold)" />
        <StatCard label="NET REVENUE" value={metrics ? fmt(metrics.netRecovered) : '—'} sub={metrics ? `${fmt(metrics.revenueRecovered)} recovered · ${fmt(metrics.interventionCost)} cost` : ''} icon="₹" iconBg="var(--gold-bg)" iconColor="var(--gold-bright)" valueColor="var(--gold-bright)" />
      </div>

      {/* Ledger tape — live feed of decisions as batches stream in */}
      <div style={{ marginBottom: 16, ...FW }}><LedgerTape attempts={attempts} /></div>

      {/* ROW 2: Net Revenue chart + Bounds Register */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(340px, 1fr))', gap: 14, marginBottom: 16, alignItems: 'start' }}>
        <div style={{ width: '100%', minWidth: 0 }}>{metrics && <RecoveryChart netRecovered={metrics.netRecovered} baseline={metrics.baselineNetRecovered} />}</div>
        <div style={{ width: '100%', minWidth: 0 }}><BoundsRegister /></div>
      </div>

      {/* ROW 3: Recovery Pipeline + Success Rate by Action */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(340px, 1fr))', gap: 14, marginBottom: 16 }}>
        <div style={{ width: '100%', minWidth: 0 }}><FunnelChart data={funnelData} /></div>
        <div style={{ width: '100%', minWidth: 0 }}><ActionBreakdownChart data={actionData} /></div>
      </div>

      {/* ROW 4: Allowed Actions — full width */}
      <div className="card" style={{ marginBottom: 16, ...FW }}>
        <div className="section-title">ALLOWED ACTIONS — LLM MAY ONLY PICK FROM THIS LIST</div>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))', gap: 8 }}>
          {[ { action: 'Retry Now', desc: 'Immediate retry', icon: '↻', bg: 'var(--green-bg)' }, { action: 'Retry Scheduled', desc: 'After cooldown', icon: '⏱', bg: 'var(--gold-bg)' }, { action: 'Send Payment Link', desc: 'Update payment', icon: '🔗', bg: 'var(--amber-bg)' }, { action: 'Offer Discount', desc: 'Max 15%', icon: '%', bg: 'var(--gold-bg)' }, { action: 'Escalate to Human', desc: 'Collections team', icon: '👤', bg: 'var(--red-bg)' }, { action: 'Checkout Reminder', desc: 'Cart recovery', icon: '🛒', bg: 'var(--amber-bg)' }, { action: 'Send Reminder', desc: 'B2B invoice', icon: '📧', bg: 'var(--gold-bg)' }, { action: 'Offer Payment Plan', desc: 'Installments', icon: '📋', bg: 'var(--green-bg)' },
          ].map(a => (<div key={a.action} style={{ padding: '10px 12px', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border)', display: 'flex', alignItems: 'center', gap: 10, background: 'var(--surface)' }}><div style={{ width: 30, height: 30, borderRadius: 'var(--radius-sm)', background: a.bg, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 14, flexShrink: 0 }}>{a.icon}</div><div><div style={{ fontFamily: 'var(--font-body)', fontSize: 12, fontWeight: 600, color: 'var(--text)' }}>{a.action}</div><div style={{ fontFamily: 'var(--font-body)', fontSize: 10, color: 'var(--text-muted)' }}>{a.desc}</div></div></div>))}
        </div>
      </div>

      {/* ROW 5: Pending Human Review + Revenue by Source — balanced heights */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(400px, 1fr))', gap: 14, alignItems: 'start' }}>
        <div style={{ width: '100%', minWidth: 0, overflow: 'hidden' }}><PendingReview key={funnelRefresh} onResolved={() => { loadReviewCount(); setFunnelRefresh(n => n + 1); }} /></div>
        {metrics?.bySource && (<div className="card" style={{ width: '100%', minWidth: 0 }}>
          <div className="section-title" style={{ marginBottom: 10 }}>REVENUE BY SOURCE</div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 10 }}>
            {Object.entries(metrics.bySource).map(([key, src]) => (<div key={key} style={{ padding: '14px', background: 'var(--bg-secondary)', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border)' }}>
              <div style={{ fontFamily: 'var(--font-body)', fontSize: 10, fontWeight: 600, textTransform: 'uppercase', color: 'var(--text-muted)', letterSpacing: '0.04em', marginBottom: 8 }}>{src.label}</div>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 2 }}><span style={{ fontFamily: 'var(--font-mono)', fontSize: 22, fontWeight: 700, color: 'var(--text)' }}>{src.atRisk}</span><span style={{ fontSize: 11, color: 'var(--text-muted)' }}>at risk</span></div>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}><span style={{ fontFamily: 'var(--font-mono)', fontSize: 14, fontWeight: 600, color: 'var(--green)' }}>{src.recovered}</span><span style={{ fontSize: 11, color: 'var(--text-muted)' }}>recovered</span></div>
            </div>))}
          </div>
        </div>)}
      </div>
    </>);
  }

  // ═══ 2. BOUND REGISTER ═══
  function renderBoundRegister() {
    return (<>
      <PageHeader title="Bound Register" subtitle="Non-negotiable constraints enforced by the RulesEngine before any LLM output executes." />
      <div style={{ marginBottom: 16 }}><BoundsRegister expanded /></div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 16 }}>
        <SummaryStat label="MAX RETRIES" value={boundsConfig?.maxRetries ?? 3} color="var(--gold)" />
        <SummaryStat label="MAX DISCOUNT" value={`${boundsConfig?.maxDiscountPercent ?? 15}%`} color="var(--gold-bright)" />
        <SummaryStat label="COOLDOWN" value={`${boundsConfig?.retryCooldownMinutes ?? 60} min`} color="var(--amber)" />
      </div>
    </>);
  }

  // ═══ 3. TRANSACTIONS ═══
  function renderTransactions() {
    const txCount = allTransactions.length;
    const recovered = allTransactions.filter(t => t.status === 'RECOVERED').length;
    const atRisk = allTransactions.filter(t => t.status === 'AT_RISK').length;
    const inRecovery = allTransactions.filter(t => t.status === 'IN_RECOVERY').length;
    const lost = allTransactions.filter(t => t.status === 'LOST').length;
    return (<>
      <PageHeader title="Transactions" subtitle="Monitor all transactions at risk and their recovery progress." />
      {txLoadError ? (
        <div className="card" style={{ ...FW, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', padding: '48px 40px' }}>
          <div style={{ fontSize: 40, marginBottom: 12, opacity: 0.2 }}>⚠</div>
          <div style={{ fontFamily: 'var(--font-body)', fontSize: 16, fontWeight: 600, color: 'var(--red)', marginBottom: 8 }}>Failed to load transactions</div>
          <div style={{ fontFamily: 'var(--font-body)', fontSize: 13, color: 'var(--text-muted)', textAlign: 'center', maxWidth: 440, lineHeight: 1.6 }}>{txLoadError} Make sure the Spring Boot backend is running on :8080, then refresh.</div>
          <button onClick={() => { setTxLoadError(null); loadActionLog(); }} style={{ marginTop: 18, background: 'var(--gold)', color: 'var(--text-inverse)', border: 'none', borderRadius: 'var(--radius-sm)', padding: '9px 22px', fontFamily: 'var(--font-body)', fontWeight: 600, fontSize: 13, cursor: 'pointer' }}>Retry</button>
        </div>
      ) : txCount === 0 ? (
        <EmptyState icon="⇄" title="No transactions loaded" description="Run a recovery batch to populate the transaction ledger with payment failures, checkout abandonments, and receivables." action="Run Batch ▶" onAction={handleRunBatch} />
      ) : (<>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: 16, marginBottom: 16 }}>
          <SummaryStat label="TOTAL" value={txCount} color="var(--text)" />
          <SummaryStat label="RECOVERED" value={recovered} color="var(--green)" />
          <SummaryStat label="IN RECOVERY" value={inRecovery} color="var(--gold)" />
          <SummaryStat label="LOST" value={lost} color="var(--red)" />
        </div>
        <div className="card" style={FW}>
          <div className="table-scroll">
            <table className="main-table">
              <thead><tr>
                {['ID', 'Amount', 'Failure Reason', 'Retries', 'Status', 'Created'].map(h => (
                  <th key={h}>{h}</th>
                ))}
              </tr></thead>
              <tbody>{allTransactions.slice(0, 200).map(tx => (
                <tr key={tx.id}>
                  <td style={{ fontFamily: 'var(--font-mono)', color: 'var(--gold)', fontWeight: 600 }}>#{tx.id}</td>
                  <td style={{ fontFamily: 'var(--font-mono)', fontWeight: 600, color: 'var(--text)' }}>{fmt(tx.amount)}</td>
                  <td style={{ color: 'var(--text-secondary)', textTransform: 'capitalize' }}>{tx.failureReason?.replaceAll('_', ' ').toLowerCase()}</td>
                  <td style={{ fontFamily: 'var(--font-mono)', textAlign: 'center', color: 'var(--text-secondary)' }}>{tx.retryCount}</td>
                  <td>
                    <span style={{ display: 'inline-block', padding: '3px 10px', borderRadius: 'var(--radius-full)', background: tx.status === 'RECOVERED' ? 'var(--green-bg)' : tx.status === 'LOST' ? 'var(--red-bg)' : tx.status === 'IN_RECOVERY' ? 'var(--gold-bg)' : 'var(--amber-bg)', color: tx.status === 'RECOVERED' ? 'var(--green)' : tx.status === 'LOST' ? 'var(--red)' : tx.status === 'IN_RECOVERY' ? 'var(--gold)' : 'var(--amber)', fontWeight: 600, fontSize: 11 }}>{tx.status?.replaceAll('_', ' ')}</span>
                  </td>
                  <td style={{ fontFamily: 'var(--font-mono)', fontSize: 11, color: 'var(--text-muted)' }}>{tx.createdAt ? new Date(tx.createdAt).toLocaleDateString() : '—'}</td>
                </tr>
              ))}</tbody>
            </table>
          </div>
          {allTransactions.length > 200 && <div style={{ padding: '10px 14px', textAlign: 'center', fontSize: 12, color: 'var(--text-muted)', borderTop: '1px solid var(--border)' }}>Showing 200 of {allTransactions.length}</div>}
        </div>
      </>)})

      {/* Receivables section with promise-to-pay status */}
      {rcvLoadError && (
        <div className="card" style={{ ...FW, marginTop: 16, padding: '16px 20px' }}>
          <div style={{ fontFamily: 'var(--font-body)', fontSize: 13, color: 'var(--amber)', fontWeight: 600 }}>⚠ {rcvLoadError}</div>
        </div>
      )}
      {allReceivables.length > 0 && (<>
        <div style={{ marginTop: 24, marginBottom: 12 }}>
          <h3 style={{ fontFamily: 'var(--font-body)', fontSize: 16, fontWeight: 700, color: 'var(--text)' }}>Overdue Receivables</h3>
          <div style={{ fontFamily: 'var(--font-body)', fontSize: 12, color: 'var(--text-muted)', marginTop: 2 }}>B2B invoices with promise-to-pay tracking</div>
        </div>
        <div className="card" style={FW}>
          <div className="table-scroll">
            <table className="main-table">
              <thead><tr>
                {['ID', 'Business', 'Amount', 'Days Overdue', 'Promise Status', 'Promise Date', 'Status'].map(h => (
                  <th key={h}>{h}</th>
                ))}
              </tr></thead>
              <tbody>{allReceivables.slice(0, 100).map(r => {
                const promiseColors = { NONE: 'var(--text-muted)', PROMISED: 'var(--gold)', KEPT: 'var(--green)', BROKEN: 'var(--red)' };
                const promiseLabels = { NONE: 'No Promise', PROMISED: 'Promised', KEPT: 'Promise Kept', BROKEN: 'Promise Broken' };
                const pColor = promiseColors[r.promiseStatus] || 'var(--text-muted)';
                return (
                  <tr key={r.id}>
                    <td style={{ fontFamily: 'var(--font-mono)', color: 'var(--gold)', fontWeight: 600 }}>#{r.id}</td>
                    <td style={{ color: 'var(--text-secondary)' }}>{r.businessName || r.businessCustomerId}</td>
                    <td style={{ fontFamily: 'var(--font-mono)', fontWeight: 600, color: 'var(--text)' }}>{fmt(r.invoiceAmount)}</td>
                    <td style={{ fontFamily: 'var(--font-mono)', textAlign: 'center', color: r.daysOverdue > 30 ? 'var(--red)' : 'var(--text-secondary)' }}>{r.daysOverdue}</td>
                    <td>
                      <span style={{ display: 'inline-block', padding: '3px 10px', borderRadius: 'var(--radius-full)', background: pColor + '18', color: pColor, fontWeight: 600, fontSize: 11, whiteSpace: 'nowrap' }}>
                        {r.promiseStatus === 'BROKEN' ? '✕ ' : r.promiseStatus === 'KEPT' ? '✓ ' : r.promiseStatus === 'PROMISED' ? '● ' : ''}{promiseLabels[r.promiseStatus]}
                      </span>
                    </td>
                    <td style={{ fontFamily: 'var(--font-mono)', fontSize: 11, color: 'var(--text-muted)' }}>{r.promisedPaymentDate || '—'}</td>
                    <td>
                      <span style={{ display: 'inline-block', padding: '3px 10px', borderRadius: 'var(--radius-full)', background: r.status === 'RECOVERED' ? 'var(--green-bg)' : r.status === 'WRITTEN_OFF' ? 'var(--red-bg)' : 'var(--amber-bg)', color: r.status === 'RECOVERED' ? 'var(--green)' : r.status === 'WRITTEN_OFF' ? 'var(--red)' : 'var(--amber)', fontWeight: 600, fontSize: 11 }}>{r.status?.replaceAll('_', ' ')}</span>
                    </td>
                  </tr>
                );
              })}</tbody>
            </table>
          </div>
        </div>
      </>)})
    </>);
  }

  // ═══ 4. ACTIONS ═══
  function renderActions() {
    const sorted = [...(actionLog.length > 0 ? actionLog : attempts)].sort((a, b) => (b.id || 0) - (a.id || 0));
    const successCount = sorted.filter(a => a.outcome === 'SUCCESS').length;
    const failCount = sorted.filter(a => a.outcome === 'FAILED').length;
    const skippedCount = sorted.filter(a => a.outcome === 'SKIPPED').length;
    const totalCost = sorted.reduce((s, a) => s + (a.interventionCost || 0), 0);
    const totalRecovered = sorted.filter(a => a.outcome === 'SUCCESS').reduce((s, a) => s + (a.amountRecovered || 0), 0);
    return (<>
      <PageHeader title="Actions" subtitle="Every intervention executed by the recovery agent across all sources." right={sorted.length > 0 && <span style={{ fontFamily: 'var(--font-mono)', fontSize: 12, color: 'var(--text-muted)' }}>{sorted.length} total actions</span>} />
      {attemptsLoadError && sorted.length === 0 ? (
        <div className="card" style={{ ...FW, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', padding: '48px 40px' }}>
          <div style={{ fontSize: 40, marginBottom: 12, opacity: 0.2 }}>⚠</div>
          <div style={{ fontFamily: 'var(--font-body)', fontSize: 16, fontWeight: 600, color: 'var(--red)', marginBottom: 8 }}>Failed to load actions</div>
          <div style={{ fontFamily: 'var(--font-body)', fontSize: 13, color: 'var(--text-muted)', textAlign: 'center', maxWidth: 440, lineHeight: 1.6 }}>{attemptsLoadError} Make sure the Spring Boot backend is running on :8080, then refresh.</div>
          <button onClick={loadAttempts} style={{ marginTop: 18, background: 'var(--gold)', color: 'var(--text-inverse)', border: 'none', borderRadius: 'var(--radius-sm)', padding: '9px 22px', fontFamily: 'var(--font-body)', fontWeight: 600, fontSize: 13, cursor: 'pointer' }}>Retry</button>
        </div>
      ) : sorted.length === 0 ? (
        <EmptyState icon="⚡" title="No actions executed yet" description="Run a recovery batch to see agent decisions, actions taken, outcomes, and recovery details across payment failures, checkout abandonments, and receivables." action="Run Batch ▶" onAction={handleRunBatch} />
      ) : (<>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: 16, marginBottom: 16 }}>
          <SummaryStat label="TOTAL ACTIONS" value={sorted.length} />
          <SummaryStat label="SUCCEEDED" value={successCount} color="var(--green)" />
          <SummaryStat label="FAILED" value={failCount} color="var(--red)" />
          <SummaryStat label="REVENUE RECOVERED" value={fmt(totalRecovered)} color="var(--green)" />
          <SummaryStat label="INTERVENTION COST" value={fmt(totalCost)} color="var(--amber)" />
        </div>
        <div className="card" style={FW}>
          <div className="table-scroll">
            <table className="main-table">
              <thead><tr>
                {['TXN', 'SOURCE', 'ACTION', 'OUTCOME', 'AMOUNT', 'COST', 'REASONING', 'TIME'].map(h => (
                  <th key={h}>{h}</th>
                ))}
              </tr></thead>
              <tbody>{sorted.slice(0, 300).map(a => (
                <tr key={a.id} style={{ cursor: 'pointer' }} onClick={() => setSelectedAttempt(a)}>
                  <td style={{ fontFamily: 'var(--font-mono)', color: 'var(--gold)', fontWeight: 600 }}>#{a.transaction?.id || a.checkoutSession?.id || a.receivable?.id}</td>
                  <td><span style={{ padding: '2px 8px', borderRadius: 'var(--radius-full)', background: a.sourceType === 'PAYMENT' ? 'var(--gold-bg)' : a.sourceType === 'CHECKOUT' ? 'var(--amber-bg)' : 'var(--green-bg)', color: a.sourceType === 'PAYMENT' ? 'var(--gold)' : a.sourceType === 'CHECKOUT' ? 'var(--amber)' : 'var(--green)', fontWeight: 600, fontSize: 10, whiteSpace: 'nowrap' }}>{a.sourceType}</span></td>
                  <td style={{ color: 'var(--text-secondary)', whiteSpace: 'nowrap' }}>{a.actionTaken?.replaceAll('_', ' ')}</td>
                  <td><span style={{ padding: '3px 10px', borderRadius: 'var(--radius-full)', background: a.outcome === 'SUCCESS' ? 'var(--green-bg)' : a.outcome === 'FAILED' ? 'var(--red-bg)' : a.outcome === 'SKIPPED' ? 'var(--surface-hover)' : 'var(--amber-bg)', color: a.outcome === 'SUCCESS' ? 'var(--green)' : a.outcome === 'FAILED' ? 'var(--red)' : a.outcome === 'SKIPPED' ? 'var(--text-muted)' : 'var(--amber)', fontWeight: 600, fontSize: 11 }}>{a.outcome === 'SKIPPED' ? 'SKIPPED' : a.outcome}</span></td>
                  <td style={{ fontFamily: 'var(--font-mono)', textAlign: 'right', color: a.amountRecovered > 0 ? 'var(--green)' : 'var(--text-muted)', whiteSpace: 'nowrap' }}>{a.amountRecovered > 0 ? fmt(a.amountRecovered) : '—'}</td>
                  <td style={{ fontFamily: 'var(--font-mono)', textAlign: 'right', color: 'var(--text-muted)', whiteSpace: 'nowrap' }}>{a.interventionCost > 0 ? fmt(a.interventionCost) : '—'}</td>
                  <td style={{ color: 'var(--text-muted)', maxWidth: 300, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{a.reasoning}</td>
                  <td style={{ fontFamily: 'var(--font-mono)', fontSize: 11, color: 'var(--text-muted)', whiteSpace: 'nowrap' }}>{a.executedAt ? new Date(a.executedAt).toLocaleTimeString() : '—'}</td>
                </tr>
              ))}</tbody>
            </table>
          </div>
          {sorted.length > 300 && <div style={{ padding: '10px 14px', textAlign: 'center', fontSize: 12, color: 'var(--text-muted)', borderTop: '1px solid var(--border)' }}>Showing 300 of {sorted.length}</div>}
        </div>
      </>)}
    </>);
  }

  // ═══ 5. DECISION LEDGER ═══
  function renderDecisionLedger() {
    const successCount = attempts.filter(a => a.outcome === 'SUCCESS').length;
    const failCount = attempts.filter(a => a.outcome === 'FAILED').length;
    const signoffCount = attempts.filter(a => a.requiresHumanSignoff).length;
    return (<>
      <PageHeader title="Decision Ledger" subtitle="AI decisions, rule validation results, and recovery outcomes."
        right={<div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          {streamCount !== null && <span style={{ fontFamily: 'var(--font-mono)', fontSize: 11, color: 'var(--text-muted)', background: 'var(--bg-secondary)', borderRadius: 'var(--radius-full)', padding: '4px 12px', border: '1px solid var(--border)' }}>{streamCount} entries</span>}
          <button onClick={exportCsv} style={{ background: 'transparent', color: 'var(--text-secondary)', border: '1px solid var(--border)', borderRadius: 'var(--radius-sm)', padding: '7px 14px', fontFamily: 'var(--font-body)', fontSize: 12, fontWeight: 500, cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 6, transition: 'all var(--transition-fast)' }}
            onMouseEnter={e => { e.currentTarget.style.borderColor = 'var(--gold)'; e.currentTarget.style.color = 'var(--gold)'; }}
            onMouseLeave={e => { e.currentTarget.style.borderColor = 'var(--border)'; e.currentTarget.style.color = 'var(--text-secondary)'; }}
          >↓ Export CSV</button>
        </div>}
      />
      {attemptsLoadError && attempts.length === 0 ? (
        <div className="card" style={{ ...FW, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', padding: '48px 40px' }}>
          <div style={{ fontSize: 40, marginBottom: 12, opacity: 0.2 }}>⚠</div>
          <div style={{ fontFamily: 'var(--font-body)', fontSize: 16, fontWeight: 600, color: 'var(--red)', marginBottom: 8 }}>Failed to load the decision ledger</div>
          <div style={{ fontFamily: 'var(--font-body)', fontSize: 13, color: 'var(--text-muted)', textAlign: 'center', maxWidth: 440, lineHeight: 1.6 }}>{attemptsLoadError} Make sure the Spring Boot backend is running on :8080, then refresh.</div>
          <button onClick={loadAttempts} style={{ marginTop: 18, background: 'var(--gold)', color: 'var(--text-inverse)', border: 'none', borderRadius: 'var(--radius-sm)', padding: '9px 22px', fontFamily: 'var(--font-body)', fontWeight: 600, fontSize: 13, cursor: 'pointer' }}>Retry</button>
        </div>
      ) : attempts.length === 0 ? (
        <EmptyState icon="☰" title="No decisions recorded yet" description="The Decision Ledger populates after the Recovery Agent processes a batch. Each row shows the AI recommendation, which bounded action the RulesEngine approved, and the outcome." action="Run Batch ▶" onAction={handleRunBatch} />
      ) : (<>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: 16, marginBottom: 16 }}>
          <SummaryStat label="TOTAL DECISIONS" value={attempts.length} />
          <SummaryStat label="APPROVED" value={successCount} color="var(--green)" />
          <SummaryStat label="REJECTED" value={failCount} color="var(--red)" />
          <SummaryStat label="PENDING REVIEW" value={signoffCount} color="var(--amber)" />
        </div>
        <div className="card" style={FW}>
          <AttemptTable attempts={attempts} onSelectAttempt={setSelectedAttempt} />
        </div>
      </>)}
    </>);
  }

  // ═══ 6. REPORTS ═══
  function renderReports() {
    const bySource = metrics?.bySource;
    const chartsLoaded = funnelData && actionData.length > 0;
    return (<>
      <PageHeader title="Reports" subtitle="Recovery performance and financial insights across all revenue sources." />
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: 16, marginBottom: 16 }}>
        <SummaryStat label="TOTAL RECOVERED" value={fmt(metrics?.revenueRecovered)} color="var(--green)" />
        <SummaryStat label="RECOVERY RATE" value={metrics ? pct(metrics.recoveryRatePercent) : '—'} color="var(--gold)" />
        <SummaryStat label="NET REVENUE" value={fmt(metrics?.netRecovered)} color="var(--gold-bright)" />
        <SummaryStat label="BASELINE" value={fmt(metrics?.baselineNetRecovered)} />
        <SummaryStat label="SILENT RECOVERY" value={metrics ? `${metrics.silentRecoveryRate ?? 0}%` : '—'} color="var(--text-secondary)" tooltip="% of recovered revenue from silent (no-customer-contact) attempts" />
        <SummaryStat label="DSO" value={metrics ? `${metrics.dso ?? 0}d` : '—'} color="var(--amber)" tooltip="Days Sales Outstanding — lower is better, healthy B2B is <45 days" />
        <SummaryStat label="AVG DAYS OVERDUE" value={metrics ? `${metrics.avgDaysOverdue ?? 0}d` : '—'} color="var(--amber)" />
        <SummaryStat label="PROMISE KEEP RATE" value={metrics ? `${metrics.promiseKeepRate ?? 0}%` : '—'} color="var(--green)" />
      </div>

      {/* Held-out evaluation split */}
      {heldOutMetrics && (
        <div className="card" style={{ marginBottom: 16, ...FW }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
            <div className="section-title" style={{ marginBottom: 0 }}>HELD-OUT EVALUATION — UNSEEN DATA (20% split, never used to tune the agent)</div>
          </div>
          <div style={{ fontFamily: 'var(--font-body)', fontSize: 12, color: 'var(--text-muted)', marginBottom: 14 }}>These metrics are computed over the held-out subset only — data the agent has never seen. This is the credible claim of real-world performance.</div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: 12 }}>
            <SummaryStat label="HELD-OUT ITEMS" value={heldOutMetrics.totalAtRisk ?? '—'} color="var(--text)" />
            <SummaryStat label="HELD-OUT RECOVERED" value={heldOutMetrics.recoveredCount ?? '—'} color="var(--green)" />
            <SummaryStat label="HELD-OUT RECOVERY RATE" value={heldOutMetrics ? pct(heldOutMetrics.recoveryRatePercent) : '—'} color="var(--gold)" />
            <SummaryStat label="HELD-OUT NET REVENUE" value={fmt(heldOutMetrics?.netRecovered)} color="var(--gold-bright)" />
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 10, marginTop: 12 }}>
            {heldOutMetrics?.bySource && Object.entries(heldOutMetrics.bySource).map(([key, src]) => (
              <div key={key} style={{ padding: '10px 12px', background: 'var(--bg-secondary)', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border)' }}>
                <div style={{ fontFamily: 'var(--font-body)', fontSize: 10, fontWeight: 600, textTransform: 'uppercase', color: 'var(--text-muted)', letterSpacing: '0.04em', marginBottom: 6 }}>{src.label}</div>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}><span style={{ fontFamily: 'var(--font-mono)', fontSize: 18, fontWeight: 700, color: 'var(--text)' }}>{src.atRisk}</span><span style={{ fontSize: 11, color: 'var(--text-muted)' }}>at risk</span></div>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}><span style={{ fontFamily: 'var(--font-mono)', fontSize: 13, fontWeight: 600, color: 'var(--green)' }}>{src.recovered}</span><span style={{ fontSize: 11, color: 'var(--text-muted)' }}>recovered</span></div>
              </div>
            ))}
          </div>
        </div>
      )}
      <div className="card" style={{ marginBottom: 16, ...FW }}>
        <div className="section-title" style={{ marginBottom: 12 }}>RECOVERY BY SOURCE</div>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 16 }}>
          {bySource && Object.entries(bySource).map(([key, src]) => (<div key={key} style={{ padding: '18px', background: 'var(--bg-secondary)', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border)' }}>
            <div style={{ fontFamily: 'var(--font-body)', fontSize: 10, fontWeight: 600, textTransform: 'uppercase', color: 'var(--text-muted)', letterSpacing: '0.04em', marginBottom: 10 }}>{src.label}</div>
            <div style={{ fontFamily: 'var(--font-mono)', fontSize: 28, fontWeight: 700, color: 'var(--text)' }}>{src.atRisk} <span style={{ fontSize: 12, color: 'var(--text-muted)', fontWeight: 400 }}>at risk</span></div>
            <div style={{ fontFamily: 'var(--font-mono)', fontSize: 18, fontWeight: 600, color: 'var(--green)', marginTop: 6 }}>{src.recovered} <span style={{ fontSize: 12, color: 'var(--text-muted)', fontWeight: 400 }}>({src.atRisk > 0 ? ((src.recovered / src.atRisk) * 100).toFixed(1) : 0}%)</span></div>
          </div>))}
        </div>
      </div>

      {/* Segment breakdown */}
      {metrics?.bySegment && (
        <div className="card" style={{ marginBottom: 16, ...FW }}>
          <div className="section-title" style={{ marginBottom: 4 }}>RECOVERY BY CUSTOMER SEGMENT</div>
          <div style={{ fontFamily: 'var(--font-body)', fontSize: 12, color: 'var(--text-muted)', marginBottom: 12 }}>HIGH_VALUE customers (top 20% by transaction amount) get wider recovery bounds: more retries and a higher discount ceiling.</div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 16 }}>
            {Object.entries(metrics.bySegment).map(([key, seg]) => (
              <div key={key} style={{ padding: '18px', background: 'var(--bg-secondary)', borderRadius: 'var(--radius-sm)', border: '1px solid', borderColor: key === 'HIGH_VALUE' ? 'var(--gold)' : 'var(--border)' }}>
                <div style={{ fontFamily: 'var(--font-body)', fontSize: 10, fontWeight: 600, textTransform: 'uppercase', color: key === 'HIGH_VALUE' ? 'var(--gold)' : 'var(--text-muted)', letterSpacing: '0.04em', marginBottom: 10 }}>{key === 'HIGH_VALUE' ? '◆ HIGH VALUE' : '○ STANDARD'}</div>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 8 }}>
                  <span style={{ fontFamily: 'var(--font-mono)', fontSize: 24, fontWeight: 700, color: 'var(--text)' }}>{seg.atRisk}</span>
                  <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>at risk</span>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 4 }}>
                  <span style={{ fontFamily: 'var(--font-mono)', fontSize: 16, fontWeight: 600, color: 'var(--green)' }}>{seg.recovered}</span>
                  <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>recovered</span>
                </div>
                <div style={{ fontFamily: 'var(--font-mono)', fontSize: 13, color: 'var(--gold)', marginTop: 4 }}>{seg.recoveryRate}% recovery rate</div>
                <div style={{ fontFamily: 'var(--font-mono)', fontSize: 12, color: 'var(--text-secondary)', marginTop: 2 }}>{fmt(seg.revenue)} recovered</div>
              </div>
            ))}
          </div>
        </div>
      )}

      <div style={{ marginBottom: 16 }}>
        {metrics ? <RecoveryChart netRecovered={metrics.netRecovered} baseline={metrics.baselineNetRecovered} /> : <SkeletonCard lines={2} height={280} />}
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1.2fr', gap: 16, marginBottom: 16 }}>
        {chartsLoaded ? <div style={FW}><FunnelChart data={funnelData} /></div> : <SkeletonCard height={320} />}
        {chartsLoaded ? <div style={FW}><ActionBreakdownChart data={actionData} /></div> : <SkeletonCard height={320} />}
      </div>

      {/* Action Efficiency / ROI table */}
      {efficiencyData.length > 0 && (
        <div className="card" style={FW}>
          <div className="section-title" style={{ marginBottom: 12 }}>ACTION EFFICIENCY — ROI RANKING</div>
          <div style={{ fontFamily: 'var(--font-body)', fontSize: 12, color: 'var(--text-muted)', marginBottom: 14 }}>Recovered per rupee spent on each intervention type. Sorted by ROI descending.</div>
          <div className="table-scroll">
            <table className="main-table">
              <thead>
                <tr>
                  <th style={{ width: 40 }}>#</th>
                  <th>ACTION</th>
                  <th style={{ textAlign: 'right' }}>ATTEMPTS</th>
                  <th style={{ textAlign: 'right' }}>SUCCESSES</th>
                  <th style={{ textAlign: 'right' }}>RECOVERED</th>
                  <th style={{ textAlign: 'right' }}>COST</th>
                  <th style={{ textAlign: 'right' }}>ROI (₹/₹)</th>
                </tr>
              </thead>
              <tbody>
                {efficiencyData.map((row, i) => (
                  <tr key={row.action}>
                    <td style={{ fontFamily: 'var(--font-mono)', fontSize: 12, color: 'var(--text-muted)', textAlign: 'center' }}>{i + 1}</td>
                    <td style={{ fontWeight: 600, color: 'var(--text)', whiteSpace: 'nowrap' }}>{row.action.replaceAll('_', ' ')}</td>
                    <td style={{ fontFamily: 'var(--font-mono)', textAlign: 'right', color: 'var(--text-secondary)' }}>{row.totalAttempts}</td>
                    <td style={{ fontFamily: 'var(--font-mono)', textAlign: 'right', color: row.successCount > 0 ? 'var(--green)' : 'var(--text-muted)' }}>{row.successCount}</td>
                    <td style={{ fontFamily: 'var(--font-mono)', textAlign: 'right', color: row.totalRecovered > 0 ? 'var(--green)' : 'var(--text-muted)', fontWeight: 600 }}>{fmt(row.totalRecovered)}</td>
                    <td style={{ fontFamily: 'var(--font-mono)', textAlign: 'right', color: row.totalCost > 0 ? 'var(--amber)' : 'var(--text-muted)' }}>{row.totalCost > 0 ? fmt(row.totalCost) : '—'}</td>
                    <td style={{ textAlign: 'right' }}>
                      {row.recoveredPerRupeeSpent != null ? (
                        <span style={{ fontFamily: 'var(--font-mono)', fontWeight: 700, color: 'var(--gold-bright)', fontSize: 14 }}>{Number(row.recoveredPerRupeeSpent).toFixed(1)}x</span>
                      ) : (
                        <span style={{ fontFamily: 'var(--font-body)', fontSize: 11, color: 'var(--text-muted)', fontStyle: 'italic' }}>{row.costNote}</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Uplift Analysis */}
      {upliftData && (
        <div className="card" style={{ marginBottom: 16, ...FW }}>
          <div className="section-title" style={{ marginBottom: 4 }}>UPLIFT ANALYSIS — CONTROL vs TREATMENT</div>
          <div style={{ fontFamily: 'var(--font-body)', fontSize: 12, color: 'var(--text-muted)', marginBottom: 14 }}>
            A held-out control group (no agent intervention) establishes the natural-recovery baseline.
            The delta shows how much each segment improves with agent intervention.
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: 12, marginBottom: 16 }}>
            <SummaryStat label="CONTROL RECOVERY RATE" value={upliftData.controlRecoveryRate != null ? `${upliftData.controlRecoveryRate.toFixed(1)}%` : '—'} color="var(--text-secondary)" tooltip="What recovery looks like with zero agent involvement" />
            <SummaryStat label="CONTROL RECOVERED" value={upliftData.controlRecovered ?? '—'} color="var(--text-secondary)" />
            <SummaryStat label="CONTROL TOTAL" value={upliftData.controlTotal ?? '—'} color="var(--text-secondary)" />
          </div>
          {upliftData.bySegment && (
            <div className="table-scroll">
              <table className="main-table">
                <thead>
                  <tr>
                    <th>SEGMENT</th>
                    <th style={{ textAlign: 'right' }}>CONTROL RECOVERY</th>
                    <th style={{ textAlign: 'right' }}>TREATMENT RECOVERY</th>
                    <th style={{ textAlign: 'right' }}>UPLIFT (Δ)</th>
                    <th style={{ textAlign: 'right' }}>CONTROL TOTAL</th>
                    <th style={{ textAlign: 'right' }}>TREATMENT TOTAL</th>
                  </tr>
                </thead>
                <tbody>
                  {Object.values(upliftData.bySegment).map(seg => (
                    <tr key={seg.segment}>
                      <td style={{ fontWeight: 600, color: 'var(--text)', whiteSpace: 'nowrap' }}>{seg.segment}</td>
                      <td style={{ fontFamily: 'var(--font-mono)', textAlign: 'right', color: 'var(--text-secondary)' }}>{seg.controlRate != null ? `${seg.controlRate.toFixed(1)}%` : '—'}</td>
                      <td style={{ fontFamily: 'var(--font-mono)', textAlign: 'right', color: 'var(--green)', fontWeight: 600 }}>{seg.treatmentRate != null ? `${seg.treatmentRate.toFixed(1)}%` : '—'}</td>
                      <td style={{ textAlign: 'right' }}>
                        <span style={{ fontFamily: 'var(--font-mono)', fontWeight: 700, fontSize: 14, color: seg.delta > 0 ? 'var(--green)' : seg.delta < 0 ? 'var(--red)' : 'var(--text-muted)' }}>
                          {seg.delta != null ? `${seg.delta > 0 ? '+' : ''}${seg.delta.toFixed(1)}pp` : '—'}
                        </span>
                      </td>
                      <td style={{ fontFamily: 'var(--font-mono)', textAlign: 'right', color: 'var(--text-muted)' }}>{seg.controlTotal ?? '—'}</td>
                      <td style={{ fontFamily: 'var(--font-mono)', textAlign: 'right', color: 'var(--text-muted)' }}>{seg.treatmentTotal ?? '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </>);
  }

  // ═══ 7. ALERTS ═══
  function renderAlerts() {
    const criticalCount = alerts.filter(a => a.signoffReason?.includes('3rd')).length;
    const warningCount = alerts.filter(a => a.signoffReason?.includes('discount')).length;
    const infoCount = alerts.length - criticalCount - warningCount;
    return (<>
      <PageHeader title="Alerts" subtitle="Items requiring human review — escalated per the bounded-workflow rules." />
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: 16, marginBottom: 16 }}>
        <SummaryStat label="CRITICAL" value={criticalCount} color="var(--red)" />
        <SummaryStat label="WARNING" value={warningCount} color="var(--amber)" />
        <SummaryStat label="INFO" value={Math.max(0, infoCount)} />
      </div>
      <PendingReview key={`alert-${funnelRefresh}`} forceShow onResolved={() => { loadReviewCount(); setFunnelRefresh(n => n + 1); }} />
    </>);
  }

  // ═══ 8. SETTINGS ═══
  function renderSettings() {
    return (<>
      <PageHeader title="Settings" subtitle="Manage application configuration and agent bounds." />
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(400px, 1fr))', gap: 20 }}>
        {/* Left: Configuration */}
        <div className="card" style={FW}>
          <div style={{ fontFamily: 'var(--font-body)', fontSize: 15, fontWeight: 700, color: 'var(--text)', marginBottom: 4 }}>AGENT CONFIGURATION</div>
          <div style={{ fontFamily: 'var(--font-body)', fontSize: 12, color: 'var(--text-muted)', marginBottom: 16 }}>These values control the RulesEngine's hard bounds. Changes take effect on the next batch run.</div>
          <div style={{ fontFamily: 'var(--font-body)', fontSize: 12, fontWeight: 600, color: 'var(--text-secondary)', marginBottom: 8, textTransform: 'uppercase', letterSpacing: '0.04em' }}>Standard Customer Bounds</div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 16 }}>
            {[ { key: 'maxRetries', label: 'Max Retry Attempts', type: 'number' }, { key: 'maxDiscountPercent', label: 'Max Discount %', type: 'number' }, { key: 'retryCooldownMinutes', label: 'Cooldown (minutes)', type: 'number' }, { key: 'minAmountForDiscount', label: 'Min Amount for Discount (₹)', type: 'number' },
            ].map(f => (<div key={f.key}>
              <label style={{ display: 'block', fontFamily: 'var(--font-body)', fontSize: 11, fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.04em', color: 'var(--text-muted)', marginBottom: 6 }}>{f.label}</label>
              <input type={f.type} value={settingsLocal[f.key] ?? ''} onChange={e => setSettingsLocal(p => ({ ...p, [f.key]: e.target.type === 'number' ? Number(e.target.value) : e.target.value }))}
                style={{ width: '100%', padding: '10px 14px', border: '1px solid var(--border)', borderRadius: 'var(--radius-sm)', fontFamily: 'var(--font-mono)', fontSize: 14, color: 'var(--text)', background: 'var(--bg-secondary)', transition: 'border-color var(--transition-fast)' }}
                onFocus={e => e.target.style.borderColor = 'var(--gold)'} onBlur={e => e.target.style.borderColor = 'var(--border)'} />
            </div>))}
          </div>

          <div style={{ marginTop: 14, padding: '10px 14px', background: 'var(--bg-secondary)', border: '1px solid var(--gold)', borderRadius: 'var(--radius-sm)' }}>
            <div style={{ fontFamily: 'var(--font-body)', fontSize: 12, fontWeight: 600, color: 'var(--gold)', marginBottom: 8, textTransform: 'uppercase', letterSpacing: '0.04em' }}>◆ High-Value Customer Bounds</div>
            <div style={{ fontFamily: 'var(--font-body)', fontSize: 11, color: 'var(--text-muted)', marginBottom: 10 }}>Wider limits for top 20% customers by transaction value — more retries, higher discount ceiling.</div>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 16 }}>
              {[ { key: 'hvMaxRetries', label: 'HV Max Retries', type: 'number' }, { key: 'hvMaxDiscountPercent', label: 'HV Max Discount %', type: 'number' }, { key: 'hvMinAmountForDiscount', label: 'HV Min Amount for Discount (₹)', type: 'number' },
              ].map(f => (<div key={f.key}>
                <label style={{ display: 'block', fontFamily: 'var(--font-body)', fontSize: 11, fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.04em', color: 'var(--text-muted)', marginBottom: 6 }}>{f.label}</label>
                <input type={f.type} value={settingsLocal[f.key] ?? ''} onChange={e => setSettingsLocal(p => ({ ...p, [f.key]: e.target.type === 'number' ? Number(e.target.value) : e.target.value }))}
                  style={{ width: '100%', padding: '10px 14px', border: '1px solid var(--border)', borderRadius: 'var(--radius-sm)', fontFamily: 'var(--font-mono)', fontSize: 14, color: 'var(--text)', background: 'var(--surface)', transition: 'border-color var(--transition-fast)' }}
                  onFocus={e => e.target.style.borderColor = 'var(--gold)'} onBlur={e => e.target.style.borderColor = 'var(--border)'} />
              </div>))}
            </div>
          </div>

          {/* Preview Impact button */}
          <div style={{ marginTop: 16, display: 'flex', alignItems: 'center', gap: 10 }}>
            <button onClick={simulateBounds} disabled={simLoading}
              style={{ background: 'transparent', color: 'var(--text-secondary)', border: '1px solid var(--border)', borderRadius: 'var(--radius-sm)', padding: '8px 18px', fontFamily: 'var(--font-body)', fontWeight: 500, fontSize: 12, cursor: simLoading ? 'not-allowed' : 'pointer', transition: 'all var(--transition-fast)', opacity: simLoading ? 0.5 : 1 }}
              onMouseEnter={e => { if (!simLoading) { e.currentTarget.style.borderColor = 'var(--gold)'; e.currentTarget.style.color = 'var(--gold)'; } }}
              onMouseLeave={e => { e.currentTarget.style.borderColor = 'var(--border)'; e.currentTarget.style.color = 'var(--text-secondary)'; }}
            >{simLoading ? 'Simulating…' : '◈ Preview Impact'}</button>
            <span style={{ fontFamily: 'var(--font-body)', fontSize: 11, color: 'var(--text-muted)', fontStyle: 'italic' }}>Estimated on current batch — not a live re-run</span>
          </div>

          {/* Simulation Result */}
          {simResult && (
            <div style={{ marginTop: 14, padding: '14px 16px', background: 'var(--bg-secondary)', border: '1px solid var(--border)', borderRadius: 'var(--radius-sm)' }}>
              <div style={{ fontFamily: 'var(--font-body)', fontSize: 11, fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.04em', color: 'var(--text-muted)', marginBottom: 10 }}>Simulation Result — Projected Impact</div>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))', gap: 10 }}>
                {[
                  { label: 'Projected Net', value: fmt(simResult.simulated?.netRecovered), color: 'var(--gold-bright)' },
                  { label: 'Δ Net Revenue', value: `${simResult.deltaNet >= 0 ? '+' : ''}${fmt(simResult.deltaNet)}`, color: simResult.deltaNet >= 0 ? 'var(--green)' : 'var(--red)' },
                  { label: 'Δ Revenue', value: `${simResult.deltaRevenue >= 0 ? '+' : ''}${fmt(simResult.deltaRevenue)}`, color: simResult.deltaRevenue >= 0 ? 'var(--green)' : 'var(--red)' },
                  { label: 'Δ Cost', value: `${simResult.deltaCost >= 0 ? '+' : ''}${fmt(simResult.deltaCost)}`, color: simResult.deltaCost > 0 ? 'var(--amber)' : 'var(--text-muted)' },
                  { label: 'Recovery Rate', value: `${simResult.simulated?.recoveryRatePercent ?? 0}%`, color: 'var(--gold)' },
                  { label: 'Δ Recoveries', value: `${simResult.deltaRecoveredCount >= 0 ? '+' : ''}${simResult.deltaRecoveredCount}`, color: simResult.deltaRecoveredCount >= 0 ? 'var(--green)' : 'var(--red)' },
                ].map(f => (
                  <div key={f.label} style={{ padding: '8px 10px', background: 'var(--surface)', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-subtle)' }}>
                    <div style={{ fontFamily: 'var(--font-body)', fontSize: 9, fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.04em', color: 'var(--text-muted)', marginBottom: 4 }}>{f.label}</div>
                    <div style={{ fontFamily: 'var(--font-mono)', fontSize: 16, fontWeight: 700, color: f.color, lineHeight: 1.2 }}>{f.value}</div>
                  </div>
                ))}
              </div>
              <div style={{ marginTop: 10, fontFamily: 'var(--font-body)', fontSize: 10, color: 'var(--text-muted)', fontStyle: 'italic' }}>
                Bounds: maxRetries={simResult.assumptions?.maxRetries} · maxDiscount={simResult.assumptions?.maxDiscountPercent}% · minAmount=₹{simResult.assumptions?.minAmountForDiscount} · cooldown={simResult.assumptions?.retryCooldownMinutes}min
              </div>
            </div>
          )}

          {/* Language toggle */}
          <div style={{ marginTop: 16, padding: '14px 16px', background: 'var(--bg-secondary)', border: '1px solid var(--border)', borderRadius: 'var(--radius-sm)' }}>
            <div style={{ fontFamily: 'var(--font-body)', fontSize: 11, fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.04em', color: 'var(--text-muted)', marginBottom: 8 }}>Customer Message Language</div>
            <div style={{ display: 'flex', gap: 8 }}>
              {[{ val: 'en', label: 'English' }, { val: 'hinglish', label: 'Hinglish' }].map(opt => (
                <button key={opt.val} onClick={() => setSettingsLocal(p => ({ ...p, language: opt.val }))}
                  style={{ flex: 1, padding: '10px 16px', borderRadius: 'var(--radius-sm)', border: '1px solid', borderColor: settingsLocal.language === opt.val ? 'var(--gold)' : 'var(--border)', background: settingsLocal.language === opt.val ? 'var(--gold-bg)' : 'var(--surface)', color: settingsLocal.language === opt.val ? 'var(--gold)' : 'var(--text-secondary)', fontFamily: 'var(--font-body)', fontWeight: settingsLocal.language === opt.val ? 600 : 400, fontSize: 13, cursor: 'pointer', transition: 'all var(--transition-fast)' }}
                >{opt.label}</button>
              ))}
            </div>
            <div style={{ fontFamily: 'var(--font-body)', fontSize: 11, color: 'var(--text-muted)', marginTop: 8 }}>When set to Hinglish, the agent generates natural Hindi-English mix SMS/email messages for customers (e.g. "Aapka payment fail ho gaya tha, yahan se dubara try kar sakte hain").</div>
          </div>
          <button onClick={saveSettings} disabled={settingsSaving} style={{ marginTop: 16, background: 'var(--gold)', color: 'var(--text-inverse)', border: 'none', borderRadius: 'var(--radius-sm)', padding: '10px 28px', fontFamily: 'var(--font-body)', fontWeight: 600, fontSize: 13, cursor: 'pointer', transition: 'all var(--transition-fast)' }}
            onMouseEnter={e => e.currentTarget.style.background = 'var(--gold-bright)'} onMouseLeave={e => e.currentTarget.style.background = 'var(--gold)'}
          >{settingsSaving ? 'Saving…' : 'Save Configuration'}</button>
        </div>
        {/* Right: System Status + Batch History */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          <div className="card" style={FW}>
            <div className="section-title">SYSTEM STATUS</div>
            {[
              { label: 'Rules Engine', value: 'Active', color: 'var(--green)', icon: '✓' },
              { label: 'AI Model', value: 'Model v2.1.4', color: 'var(--gold)', icon: '●' },
              { label: 'API Connection', value: 'Connected', color: 'var(--green)', icon: '✓' },
              { label: 'Data Store', value: 'H2 In-Memory', color: 'var(--text-secondary)', icon: '●' },
            ].map(s => (
              <div key={s.label} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '10px 0', borderBottom: '1px solid var(--border-subtle)' }}>
                <span style={{ fontFamily: 'var(--font-body)', fontSize: 13, color: 'var(--text-secondary)' }}>{s.label}</span>
                <span style={{ display: 'flex', alignItems: 'center', gap: 6, fontFamily: 'var(--font-mono)', fontSize: 12, color: s.color }}><span style={{ fontSize: 8 }}>{s.icon}</span>{s.value}</span>
              </div>
            ))}
          </div>
          <div className="card" style={FW}>
            <div className="section-title">BATCH HISTORY</div>
            <div style={{ fontFamily: 'var(--font-body)', fontSize: 12, color: 'var(--text-muted)' }}>Last batch: {lastUpdated ? lastUpdated.toLocaleString() : 'Never'}</div>
            <div style={{ fontFamily: 'var(--font-body)', fontSize: 12, color: 'var(--text-muted)', marginTop: 8 }}>Transactions seeded: {metrics?.totalAtRisk ?? '—'}</div>
            <div style={{ fontFamily: 'var(--font-body)', fontSize: 12, color: 'var(--text-muted)', marginTop: 8 }}>Recovery rate: {metrics ? pct(metrics.recoveryRatePercent) : '—'}</div>
          </div>
        </div>
      </div>
    </>);
  }

  function renderSection() {
    switch (activeNav) {
      case 'overview': return renderOverview();
      case 'bounds': return renderBoundRegister();
      case 'transactions': return renderTransactions();
      case 'actions': return renderActions();
      case 'ledger': return renderDecisionLedger();
      case 'reports': return renderReports();
      case 'alerts': return renderAlerts();
      case 'settings': return renderSettings();
      default: return renderOverview();
    }
  }

  return (
    <div style={{ display: 'flex', minHeight: '100vh', background: 'var(--bg)' }}>
      <aside style={{ width: 240, background: 'var(--sidebar-bg)', display: 'flex', flexDirection: 'column', flexShrink: 0, position: 'fixed', top: 0, left: 0, bottom: 0, zIndex: 100, borderRight: '1px solid var(--border-subtle)' }}>
        <div style={{ padding: '24px 20px', borderBottom: '1px solid var(--border-subtle)', display: 'flex', alignItems: 'center', gap: 12 }}>
          <div style={{ width: 36, height: 36, borderRadius: 'var(--radius-sm)', background: 'var(--gold-bg)', border: '1px solid var(--gold-border)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--gold)', fontWeight: 700, fontSize: 16 }}>₹</div>
          <div>
            <div style={{ fontFamily: 'var(--font-body)', fontWeight: 700, fontSize: 15, color: 'var(--text)', lineHeight: 1.2 }}>Recovery Ledger</div>
            <div style={{ fontFamily: 'var(--font-body)', fontSize: 11, color: 'var(--sidebar-text)' }}>Batch Operations</div>
          </div>
        </div>
        <nav style={{ flex: 1, padding: '12px 10px', display: 'flex', flexDirection: 'column', gap: 2 }}>
          {NAV_ITEMS.map(item => (
            <button key={item.id} onClick={() => setActiveNav(item.id)}
              style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '10px 14px', borderRadius: 'var(--radius-sm)', border: 'none', background: activeNav === item.id ? 'var(--sidebar-active-bg)' : 'transparent', color: activeNav === item.id ? 'var(--sidebar-active)' : 'var(--sidebar-text)', fontFamily: 'var(--font-body)', fontSize: 13, fontWeight: activeNav === item.id ? 600 : 400, cursor: 'pointer', textAlign: 'left', width: '100%', transition: 'all var(--transition-fast)' }}
              onMouseEnter={e => { if (activeNav !== item.id) { e.currentTarget.style.background = 'rgba(255,255,255,0.03)'; e.currentTarget.style.color = 'var(--text-secondary)'; } }}
              onMouseLeave={e => { if (activeNav !== item.id) { e.currentTarget.style.background = 'transparent'; e.currentTarget.style.color = 'var(--sidebar-text)'; } }}>
              <span style={{ fontSize: 15, width: 20, textAlign: 'center' }}>{item.icon}</span>
              {item.label}
              {item.id === 'alerts' && alerts.length > 0 && <span style={{ marginLeft: 'auto', background: 'var(--red)', color: 'white', borderRadius: 'var(--radius-full)', padding: '1px 7px', fontSize: 10, fontWeight: 700, lineHeight: '16px' }}>{alerts.length}</span>}
            </button>
          ))}
        </nav>
        <div style={{ padding: '16px 20px', borderTop: '1px solid var(--border-subtle)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div>
            <div style={{ fontFamily: 'var(--font-body)', fontSize: 11, color: 'var(--sidebar-text)' }}>AI Model Status</div>
            <div style={{ fontFamily: 'var(--font-mono)', fontSize: 10, color: 'var(--text-muted)', opacity: 0.6 }}>Model v2.1.4</div>
          </div>
          <span style={{ display: 'flex', alignItems: 'center', gap: 5, fontFamily: 'var(--font-body)', fontSize: 11, fontWeight: 600, color: 'var(--green)' }}>
            <span style={{ width: 7, height: 7, borderRadius: '50%', background: 'var(--green)', display: 'inline-block' }} /> Active
          </span>
        </div>
      </aside>

      <div style={{ width: 'calc(100vw - 240px)', marginLeft: 240, display: 'flex', flexDirection: 'column', minHeight: '100vh', minWidth: 0 }}>
        <header style={{ background: 'var(--surface)', borderBottom: '1px solid var(--border)', padding: '18px 28px', display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexShrink: 0 }}>
          <div>
            <h1 style={{ fontFamily: 'var(--font-body)', fontSize: 22, fontWeight: 700, color: 'var(--text)', lineHeight: 1.2 }}>Revenue Recovery Agent</h1>
            <div style={{ fontFamily: 'var(--font-body)', fontSize: 12, color: 'var(--text-muted)', marginTop: 2 }}>Razorpay AI Buildathon · Track 03 · AI Revenue Recovery</div>
          </div>
          <div style={{ display: 'flex', gap: 10, alignItems: 'center', flexShrink: 0 }}>
            {reviewCount > 0 && <span style={{ fontFamily: 'var(--font-body)', fontSize: 12, fontWeight: 600, color: 'var(--amber)', background: 'var(--amber-bg)', border: '1px solid var(--amber-border)', borderRadius: 'var(--radius-full)', padding: '6px 14px', display: 'flex', alignItems: 'center', gap: 6 }}><span style={{ fontSize: 10 }}>⚠</span> {reviewCount} PENDING REVIEW</span>}
            <button onClick={handleRunBatch} disabled={loading}
              style={{ background: loading ? 'var(--text-muted)' : 'var(--gold)', color: 'var(--text-inverse)', border: 'none', borderRadius: 'var(--radius-sm)', padding: '10px 22px', fontFamily: 'var(--font-body)', fontWeight: 600, fontSize: 13, cursor: loading ? 'not-allowed' : 'pointer', display: 'flex', alignItems: 'center', gap: 8, transition: 'all var(--transition-fast)', boxShadow: loading ? 'none' : 'var(--shadow-gold)' }}
              onMouseEnter={e => { if (!loading) e.currentTarget.style.background = 'var(--gold-bright)'; }}
              onMouseLeave={e => { if (!loading) e.currentTarget.style.background = 'var(--gold)'; }}
            >{loading ? '⏳ Running…' : 'Run Batch'} {!loading && <span style={{ fontSize: 12 }}>▶</span>}</button>
          </div>
        </header>

        <div style={{ background: 'var(--bg-secondary)', borderBottom: '1px solid var(--border)', padding: '10px 28px', display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: 13, flexShrink: 0 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span style={{ width: 8, height: 8, borderRadius: '50%', background: attempts.length > 0 ? 'var(--green)' : metrics ? 'var(--gold)' : 'var(--text-muted)' }} />
            <span style={{ color: 'var(--text-secondary)' }}>{attempts.length > 0 ? `${streamCount || attempts.length} transactions processed` : metrics ? `Loaded ${metrics.totalAtRisk} items — ready to process` : 'Connecting to backend…'}</span>
          </div>
          {lastUpdated && <span style={{ color: 'var(--text-muted)', fontSize: 12 }}>Last updated: {lastUpdated.toLocaleTimeString()} · 🔄</span>}
        </div>

        {/* Batch progress bar */}
        {batchProgress && (
          <div style={{ background: 'var(--surface)', borderBottom: '1px solid var(--border)', padding: '12px 28px', flexShrink: 0 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <span style={{ fontFamily: 'var(--font-body)', fontSize: 12, fontWeight: 600, color: 'var(--text)' }}>{loading ? 'Processing batch...' : 'Batch complete'}</span>
                <span style={{ fontFamily: 'var(--font-mono)', fontSize: 11, color: 'var(--text-muted)' }}>{batchProgress.processed}/{batchProgress.total} items</span>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                {batchProgress.recoveredAmount > 0 && (
                  <span style={{ fontFamily: 'var(--font-mono)', fontSize: 12, fontWeight: 600, color: 'var(--green)' }}>Recovered {fmt(batchProgress.recoveredAmount)}</span>
                )}
                <span style={{ fontFamily: 'var(--font-mono)', fontSize: 11, color: 'var(--text-muted)' }}>{batchProgress.total > 0 ? Math.round((batchProgress.processed / batchProgress.total) * 100) : 0}%</span>
              </div>
            </div>
            <div style={{ width: '100%', height: 4, background: 'var(--border)', borderRadius: 2, overflow: 'hidden' }}>
              <div style={{ width: `${batchProgress.total > 0 ? (batchProgress.processed / batchProgress.total) * 100 : 0}%`, height: '100%', background: loading ? 'var(--gold)' : 'var(--green)', borderRadius: 2, transition: 'width 0.3s ease' }} />
            </div>
          </div>
        )}

        <main style={{ padding: '24px 28px', flex: 1, minWidth: 0, width: '100%' }}>
          {error && (<div className="animate-in" style={{ background: 'var(--red-bg)', border: '1px solid var(--red-border)', borderRadius: 'var(--radius-sm)', color: 'var(--red)', padding: '12px 16px', fontSize: 13, fontWeight: 500, marginBottom: 16, display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>✕</span> {error}
            <button onClick={() => setError(null)} style={{ marginLeft: 'auto', background: 'transparent', border: 'none', color: 'var(--red)', cursor: 'pointer', fontWeight: 700, fontSize: 14 }}>✕</button>
          </div>)}
          {renderSection()}
        </main>

        <footer style={{ borderTop: '1px solid var(--border)', background: 'var(--surface)', padding: '12px 28px', display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexShrink: 0 }}>
          <span style={{ fontFamily: 'var(--font-mono)', fontSize: 11, color: 'var(--text-muted)' }}>RulesEngine.enforceBounds() — every action validated before execution</span>
          <span style={{ fontFamily: 'var(--font-mono)', fontSize: 11, color: 'var(--text-muted)' }}><span style={{ color: 'var(--gold)' }}>●</span> LLM proposes · <span style={{ color: 'var(--red)' }}>●</span> Rules engine disposes</span>
        </footer>
      </div>

      {selectedAttempt && <TransactionModal attempt={selectedAttempt} onClose={() => setSelectedAttempt(null)} />}
    </div>
  );
}
