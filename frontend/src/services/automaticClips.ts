import { readClipResponse, type CaptionSettings, type Clip, type ClipDuration } from './clips';
import type { TranscriptionLanguage } from './transcription';

export type AutomaticClipDuration = Exclude<ClipDuration, 'SAVED_RANGE'>;
type RunStatus = 'QUEUED' | 'TRANSCRIBING' | 'ANALYZING' | 'RENDERING' | 'COMPLETED' | 'PARTIAL' | 'FAILED';

export interface AutomaticClipSettings {
  clipCount: number;
  duration: AutomaticClipDuration;
  language: TranscriptionLanguage;
  captions: CaptionSettings;
}

interface AutomaticClipRun {
  id: string;
  settings: AutomaticClipSettings;
  status: RunStatus;
  message: string | null;
  clips: Clip[];
}

export const automaticClipsKey = (projectId: string) => ['automatic-clips', projectId] as const;
const endpoint = (projectId: string) => `/api/projects/${encodeURIComponent(projectId)}/automatic-clips`;

export function isAutomaticClipRunActive(status?: RunStatus) {
  return status === 'QUEUED' || status === 'TRANSCRIBING' || status === 'ANALYZING' || status === 'RENDERING';
}

export async function fetchAutomaticClips(projectId: string, signal: AbortSignal) {
  const response = await fetch(endpoint(projectId), { signal });
  return response.status === 204 ? null : readClipResponse<AutomaticClipRun>(response);
}

export async function startAutomaticClips(projectId: string, requestId: string, settings: AutomaticClipSettings) {
  const response = await fetch(endpoint(projectId), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ requestId, ...settings }),
  });
  return readClipResponse<AutomaticClipRun>(response);
}
