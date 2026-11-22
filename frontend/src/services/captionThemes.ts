import type { CaptionSettings } from './clips';

export interface CaptionTheme {
  id: string;
  name: string;
  captions: CaptionSettings;
}

export const captionThemesKey = ['caption-themes'] as const;

export function fetchCaptionThemes(signal: AbortSignal) {
  return request<CaptionTheme[]>({ signal });
}

export function saveCaptionTheme(name: string, captions: CaptionSettings) {
  return request<CaptionTheme>({
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name, captions }),
  });
}

export function updateCaptionTheme(id: string, name: string, captions: CaptionSettings) {
  return request<CaptionTheme>({
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name, captions }),
  }, `/${encodeURIComponent(id)}`);
}

async function request<T>(options: RequestInit, suffix = ''): Promise<T> {
  const response = await fetch(`/api/caption-themes${suffix}`, options);
  const data = await response.json().catch(() => null);
  if (!response.ok || data === null) {
    throw new Error(typeof data?.detail === 'string' ? data.detail : 'The caption theme request failed.');
  }
  return data;
}
