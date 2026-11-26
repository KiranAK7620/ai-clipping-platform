export type ClipDuration = 'SAVED_RANGE' | 'SECONDS_30' | 'SECONDS_45' | 'SECONDS_60';
export interface CaptionSettings {
  enabled: boolean;
  textColor: string;
  backgroundColor: string;
  backgroundOpacity: number;
  fontFamily: 'SANS_SERIF' | 'SERIF' | 'MONOSPACE';
  fontSize: number;
  bold: boolean;
  position: 'TOP' | 'MIDDLE' | 'BOTTOM';
  alignment: 'LEFT' | 'CENTER' | 'RIGHT';
  outlineColor: string;
  outlineWidth: number;
}

export const defaultCaptions: CaptionSettings = {
  enabled: true,
  textColor: '#ffffff',
  backgroundColor: '#070a14',
  backgroundOpacity: 0,
  fontFamily: 'SANS_SERIF',
  fontSize: 38,
  bold: true,
  position: 'BOTTOM',
  alignment: 'CENTER',
  outlineColor: '#070a14',
  outlineWidth: 7,
};

export const captionFontNames: Record<CaptionSettings['fontFamily'], string> = {
  SANS_SERIF: 'Sans',
  SERIF: 'Serif',
  MONOSPACE: 'Mono',
};

export interface Clip {
  id: string;
  highlightId: string;
  title: string;
  startMs: number;
  endMs: number;
  captions: CaptionSettings;
  status: 'QUEUED' | 'RENDERING' | 'READY' | 'FAILED';
  error: string | null;
}

export const clipsKey = (projectId: string) => ['clips', projectId] as const;
const endpoint = (projectId: string) => `/api/projects/${encodeURIComponent(projectId)}/clips`;

export function fetchClips(projectId: string, signal: AbortSignal) {
  return request<Clip[]>(endpoint(projectId), { signal });
}

export function queueClip(projectId: string, highlightId: string, duration: ClipDuration, captions: CaptionSettings) {
  return request<Clip>(endpoint(projectId), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ highlightId, duration, captions }),
  });
}

export function clipMedia(projectId: string, clipId: string, action: 'preview' | 'download') {
  return `${endpoint(projectId)}/${encodeURIComponent(clipId)}/${action}`;
}

async function request<T>(url: string, options: RequestInit): Promise<T> {
  return readClipResponse<T>(await fetch(url, options));
}

export async function readClipResponse<T>(response: Response): Promise<T> {
  const data = await response.json().catch(() => null);
  if (!response.ok || data === null) {
    throw new Error(typeof data?.detail === 'string' ? data.detail : 'The clip request failed.');
  }
  return data;
}
