const API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8080';

/** Single round-trip: returns { metrics, funnel, actions, efficiency } together. */
export async function fetchDashboardSummary() {
  const res = await fetch(`${API_BASE}/api/metrics/dashboard`);
  if (!res.ok) throw new Error('Failed to load dashboard metrics from the recovery engine');
  return res.json();
}

export async function fetchHeldOutMetrics() {
  const res = await fetch(`${API_BASE}/api/metrics?scope=held-out`);
  if (!res.ok) throw new Error('Failed to fetch held-out evaluation metrics');
  return res.json();
}

export async function runBatch() {
  const res = await fetch(`${API_BASE}/api/recovery/run-batch`, { method: 'POST' });
  if (!res.ok) {
    if (res.status === 409) throw new Error('409 Batch already running');
    throw new Error('Recovery batch request was rejected by the backend');
  }
  return res.json();
}

/**
 * Streaming batch: yields total count first, then each attempt as it completes via SSE.
 * The final 'done' event carries { processed, skipped, failed } counts.
 */
export function runBatchStream(onAttempt, onDone, onTotal) {
  const es = new EventSource(`${API_BASE}/api/recovery/run-batch/stream`);
  es.addEventListener('total', (e) => {
    if (onTotal) onTotal(parseInt(e.data));
  });
  es.addEventListener('attempt', (e) => {
    onAttempt(JSON.parse(e.data));
  });
  es.addEventListener('done', (e) => {
    // Newer backends send JSON counts; older numeric payloads are treated as processed-only.
    let counts = { processed: -1, skipped: 0, failed: 0 };
    try { counts = JSON.parse(e.data); } catch (_) { counts = { processed: parseInt(e.data), skipped: 0, failed: 0 }; }
    onDone(counts);
    es.close();
  });
  es.onerror = () => {
    es.close();
    onDone({ processed: -1, skipped: 0, failed: 0 });
  };
  return es;
}

/** Persisted recovery attempts, newest first — survives page refresh. */
export async function fetchAttempts() {
  const res = await fetch(`${API_BASE}/api/recovery/attempts`);
  if (!res.ok) throw new Error('Failed to fetch persisted recovery attempts');
  return res.json();
}

export async function fetchUplift() {
  const res = await fetch(`${API_BASE}/api/metrics/uplift`);
  if (!res.ok) throw new Error('uplift fetch failed');
  return res.json();
}

export function exportCsv() {
  window.open(`${API_BASE}/api/recovery/export`, '_blank');
}

export async function fetchCommandCenter() {
  const res = await fetch(`${API_BASE}/api/intelligence/command-center`);
  if (!res.ok) throw new Error('command center fetch failed');
  return res.json();
}

export async function simulateRecovery(payload) {
  const res = await fetch(`${API_BASE}/api/intelligence/simulate`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload),
  });
  if (!res.ok) throw new Error(`simulation failed (${res.status})`);
  return res.json();
}

export async function fetchReviewQueue() {
  const res = await fetch(`${API_BASE}/api/intelligence/review`);
  if (!res.ok) throw new Error('review queue fetch failed');
  return res.json();
}

export async function resolveReview(id, payload) {
  const res = await fetch(`${API_BASE}/api/intelligence/review/${id}/resolve`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload),
  });
  if (!res.ok) throw new Error(`review resolve failed (${res.status})`);
  return res.json();
}

export async function fetchActionPerformance() {
  const res = await fetch(`${API_BASE}/api/intelligence/action-performance`);
  if (!res.ok) throw new Error('action performance fetch failed');
  return res.json();
}

export async function fetchAnomalies(status) {
  const res = await fetch(`${API_BASE}/api/intelligence/anomalies?status=${status || 'OPEN'}`);
  if (!res.ok) throw new Error('anomalies fetch failed');
  return res.json();
}

export async function fetchExperiments() {
  const res = await fetch(`${API_BASE}/api/intelligence/experiments`);
  if (!res.ok) throw new Error('experiments fetch failed');
  return res.json();
}

export async function createExperiment(payload) {
  const res = await fetch(`${API_BASE}/api/intelligence/experiments`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload),
  });
  if (!res.ok) throw new Error(`create experiment failed (${res.status})`);
  return res.json();
}

export async function fetchCounterfactuals(sourceType, id) {
  const res = await fetch(`${API_BASE}/api/intelligence/counterfactuals?sourceType=${encodeURIComponent(sourceType || '')}&id=${id || ''}`);
  if (!res.ok) throw new Error('counterfactuals fetch failed');
  return res.json();
}

export async function fetchTimeline(sourceType, id) {
  const res = await fetch(`${API_BASE}/api/intelligence/timeline?sourceType=${encodeURIComponent(sourceType || '')}&id=${id || ''}`);
  if (!res.ok) throw new Error('timeline fetch failed');
  return res.json();
}
