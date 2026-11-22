import { formatTimecode } from '../../services/timecode';

const number = new Intl.NumberFormat(undefined, { maximumFractionDigits: 1 });

export function formatBytes(size: number) {
  if (size >= 1024 ** 3) return `${number.format(size / 1024 ** 3)} GiB`;
  if (size >= 1024 ** 2) return `${number.format(size / 1024 ** 2)} MiB`;
  if (size >= 1024) return `${number.format(size / 1024)} KiB`;
  return `${size} B`;
}

export function formatDuration(seconds: number) {
  return formatTimecode(Math.floor(seconds * 1000), seconds < 1);
}
