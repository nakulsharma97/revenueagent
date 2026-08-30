const API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8080';

/** Single round-trip: returns { metrics, funnel, actions, efficiency } together. */
export async function fetchDashboardSummary() {
  const res = await fetch(`${API_BASE}/api/metrics/dashboard`);
  if (!res.ok) throw new Error('dashboard summary fetch failed');
  return res.json();
}

export async function fetchMetrics() {
  const res = await fetch(`${API_BASE}/api/metrics`);
  if (!res.ok) throw new Error('metrics fetch failed');
  return res.json();
}

export async function fetchFunnel() {
  const res = await fetch(`${API_BASE}/api/metrics/funnel`);
  if (!res.ok) throw new Error('funnel fetch failed');
  return res.json();
}

export async function fetchActionBreakdown() {
  const res = await fetch(`${API_BASE}/api/metrics/actions`);
  if (!res.ok) throw new Error('action breakdown fetch failed');
  return res.json();
}

export async function fetchTransactions() {
  const res = await fetch(`${API_BASE}/api/recovery/transactions`);
  if (!res.ok) throw new Error('transactions fetch failed');
  return res.json();
}

export async function runBatch() {
  const res = await fetch(`${API_BASE}/api/recovery/run-batch`, { method: 'POST' });
  if (!res.ok) {
    if (res.status === 409) throw new Error('409 Batch already running');
    throw new Error('run-batch failed');
  }
  return res.json();
}

/** Streaming batch: yields each attempt as it completes via SSE. */
export function runBatchStream(onAttempt, onDone) {
  const es = new EventSource(`${API_BASE}/api/recovery/run-batch/stream`);
  es.addEventListener('attempt', (e) => {
    onAttempt(JSON.parse(e.data));
  });
  es.addEventListener('done', (e) => {
    onDone(parseInt(e.data));
    es.close();
  });
  es.onerror = () => {
    es.close();
    onDone(-1);
  };
  return es;
}

export async function fetchPendingReview() {
  const res = await fetch(`${API_BASE}/api/recovery/pending-review`);
  if (!res.ok) throw new Error('pending-review fetch failed');
  return res.json();
}

export function exportCsv() {
  window.open(`${API_BASE}/api/recovery/export`, '_blank');
}
