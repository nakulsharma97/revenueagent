const API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8080';

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
  if (!res.ok) throw new Error('run-batch failed');
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

export function exportCsv() {
  window.open(`${API_BASE}/api/recovery/export`, '_blank');
}
