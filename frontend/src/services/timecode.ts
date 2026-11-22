export function formatTimecode(milliseconds: number, includeMilliseconds = false): string {
  const wholeSeconds = Math.floor(milliseconds / 1000);
  const hours = String(Math.floor(wholeSeconds / 3600)).padStart(2, '0');
  const minutes = String(Math.floor(wholeSeconds / 60) % 60).padStart(2, '0');
  const seconds = String(wholeSeconds % 60).padStart(2, '0');
  const timecode = `${hours}:${minutes}:${seconds}`;
  if (!includeMilliseconds) return timecode;

  const fraction = String(milliseconds % 1000).padStart(3, '0');
  return `${timecode}.${fraction}`;
}

export function parseTimecode(value: string): number | null {
  const match = /^(\d+):([0-5]\d):([0-5]\d)(?:\.(\d{1,3}))?$/.exec(value.trim());
  if (!match) return null;

  const [, hours, minutes, seconds, fraction = ''] = match;
  const total = (Number(hours) * 3600 + Number(minutes) * 60 + Number(seconds)) * 1000
    + Number(fraction.padEnd(3, '0'));
  return Number.isSafeInteger(total) ? total : null;
}
