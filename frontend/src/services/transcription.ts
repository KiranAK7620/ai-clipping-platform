export type TranscriptionLanguage = 'AUTO' | 'ENGLISH' | 'HINDI' | 'MARATHI';

type Status = 'NOT_STARTED' | 'QUEUED' | 'EXTRACTING_AUDIO' | 'TRANSCRIBING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';

interface Segment {
  startMs: number;
  endMs: number;
  text: string;
}

export interface TranscriptionState {
  status: Status;
  error: string | null;
  revision: number | null;
  transcript: { language: string | null; segments: Segment[] } | null;
}

export const transcriptionKey = (projectId: string) => ['transcription', projectId] as const;

export function isTranscriptionActive(status?: Status) {
  return status === 'QUEUED' || status === 'EXTRACTING_AUDIO' || status === 'TRANSCRIBING';
}

export async function fetchTranscription(projectId: string, signal: AbortSignal) {
  return request(endpoint(projectId), { signal });
}

export async function startTranscription(projectId: string, language: TranscriptionLanguage, revision?: number) {
  const params = new URLSearchParams({ language });
  if (revision !== undefined) params.set('revision', String(revision));
  return request(`${endpoint(projectId)}?${params}`, { method: 'POST' });
}

export async function cancelTranscription(projectId: string) {
  return request(endpoint(projectId), { method: 'DELETE' });
}

function endpoint(projectId: string) {
  return `/api/projects/${encodeURIComponent(projectId)}/transcription`;
}

async function request(url: string, options: RequestInit): Promise<TranscriptionState> {
  const response = await fetch(url, options);
  const data = await response.json().catch(() => null);
  if (!response.ok || !data?.status) {
    throw new Error(typeof data?.detail === 'string' ? data.detail : 'The transcription request failed.');
  }
  return data;
}
