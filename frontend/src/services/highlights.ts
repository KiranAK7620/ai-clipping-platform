export type HighlightDecision = 'PENDING' | 'SELECTED' | 'REJECTED';

export interface HighlightEdit {
  startMs: number;
  endMs: number;
  decision: HighlightDecision;
}

export interface Highlight extends HighlightEdit {
  id: string;
  rank: number;
  title: string;
  hook: string;
  description: string;
  category: string;
  reason: string;
  qualityScore: number;
}

export interface HighlightResult {
  transcriptRevision: number | null;
  highlights: Highlight[];
}

export const highlightsKey = (projectId: string) => ['highlights', projectId] as const;
const endpoint = (projectId: string) => `/api/projects/${encodeURIComponent(projectId)}/highlights`;
export const highlightSource = (projectId: string) => `${endpoint(projectId)}/source`;

export function fetchHighlights(projectId: string, signal: AbortSignal) {
  return request<HighlightResult>(endpoint(projectId), { signal });
}

export function generateHighlights(projectId: string) {
  return request<HighlightResult>(endpoint(projectId), { method: 'POST' });
}

export function updateHighlight(projectId: string, id: string, edit: HighlightEdit) {
  return request<Highlight>(`${endpoint(projectId)}/${encodeURIComponent(id)}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(edit),
  });
}

async function request<T>(url: string, options: RequestInit): Promise<T> {
  const response = await fetch(url, options);
  const data = await response.json().catch(() => null);
  if (!response.ok || data === null) {
    throw new Error(typeof data?.detail === 'string' ? data.detail : 'The highlights request failed.');
  }
  return data;
}
