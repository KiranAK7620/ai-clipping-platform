import Forward5Rounded from '@mui/icons-material/Forward5Rounded';
import FullscreenExitRounded from '@mui/icons-material/FullscreenExitRounded';
import FullscreenRounded from '@mui/icons-material/FullscreenRounded';
import PauseRounded from '@mui/icons-material/PauseRounded';
import PlayArrowRounded from '@mui/icons-material/PlayArrowRounded';
import Replay5Rounded from '@mui/icons-material/Replay5Rounded';
import ReplayRounded from '@mui/icons-material/ReplayRounded';
import VolumeOffRounded from '@mui/icons-material/VolumeOffRounded';
import VolumeUpRounded from '@mui/icons-material/VolumeUpRounded';
import { Alert, CircularProgress, IconButton, Slider } from '@mui/material';
import { useCallback, useEffect, useRef, useState } from 'react';
import { formatTimecode } from '../../services/timecode';
import './highlightPreview.css';

interface Props {
  src: string;
  title: string;
  startMs: number;
  endMs: number;
}

function play(video: HTMLVideoElement) {
  void video.play().catch(() => {
    // The Play button is available if the browser blocks autoplay.
  });
}

export default function HighlightPreview({ src, title, startMs, endMs }: Props) {
  const playerRef = useRef<HTMLDivElement>(null);
  const videoRef = useRef<HTMLVideoElement>(null);
  const initialSeekComplete = useRef(false);
  const [ready, setReady] = useState(false);
  const [playing, setPlaying] = useState(false);
  const [muted, setMuted] = useState(false);
  const [failed, setFailed] = useState(false);
  const [fullscreen, setFullscreen] = useState(false);
  const [elapsedMs, setElapsedMs] = useState(0);
  const start = startMs / 1000;
  const end = endMs / 1000;
  const durationMs = endMs - startMs;

  const finish = useCallback((video: HTMLVideoElement) => {
    video.pause();
    if (video.currentTime > end + 0.001) video.currentTime = end;
    setElapsedMs(durationMs);
  }, [end, durationMs]);

  useEffect(() => {
    if (!playing) return;
    let frame: number;
    function checkEnd() {
      const video = videoRef.current;
      if (!video || video.paused) return;
      if (video.currentTime >= end) {
        finish(video);
        return;
      }
      frame = requestAnimationFrame(checkEnd);
    }
    frame = requestAnimationFrame(checkEnd);
    return () => cancelAnimationFrame(frame);
  }, [playing, end, finish]);

  function startPreview(video: HTMLVideoElement) {
    if (initialSeekComplete.current) return;
    initialSeekComplete.current = true;
    setReady(true);
    play(video);
  }

  useEffect(() => {
    function syncFullscreen() {
      setFullscreen(document.fullscreenElement === playerRef.current);
    }
    document.addEventListener('fullscreenchange', syncFullscreen);
    return () => document.removeEventListener('fullscreenchange', syncFullscreen);
  }, []);

  function seek(clipMs: number) {
    const video = videoRef.current;
    if (!video) return;
    const nextMs = Math.max(0, Math.min(durationMs, clipMs));
    if (nextMs === durationMs) video.pause();
    video.currentTime = (startMs + nextMs) / 1000;
    setElapsedMs(nextMs);
  }

  function jump(deltaMs: number) {
    const video = videoRef.current;
    if (video) seek(Math.round(video.currentTime * 1000) - startMs + deltaMs);
  }

  function toggleFullscreen() {
    const player = playerRef.current;
    if (!player) return;
    const request = fullscreen ? document.exitFullscreen() : player.requestFullscreen();
    void request.catch(() => {
      // Some browsers deny fullscreen for embedded players.
    });
  }

  function togglePlayback() {
    const video = videoRef.current;
    if (!video) return;
    if (!video.paused) {
      video.pause();
      return;
    }
    if (video.currentTime < start || video.currentTime >= end || video.ended) video.currentTime = start;
    play(video);
  }

  const showFraction = durationMs < 1000;
  const elapsed = formatTimecode(elapsedMs, showFraction);
  const length = formatTimecode(durationMs, showFraction);
  const finished = elapsedMs >= durationMs;
  const playLabel = playing ? 'Pause clip' : finished ? 'Replay clip' : 'Play clip';
  const PlayIcon = playing ? PauseRounded : finished ? ReplayRounded : PlayArrowRounded;
  const muteLabel = muted ? 'Unmute clip' : 'Mute clip';
  const fullscreenLabel = fullscreen ? 'Exit full screen' : 'Full screen';

  if (failed) return <Alert severity="warning">The browser could not play the original video.</Alert>;

  return (
    <div className="clip-player" ref={playerRef}>
      <video
        className={ready ? undefined : 'clip-player__video--loading'}
        ref={videoRef}
        src={src}
        playsInline
        preload="metadata"
        muted={muted}
        aria-label={`Preview of ${title}`}
        onLoadedMetadata={({ currentTarget: video }) => {
          if (start > 0) video.currentTime = start;
          else startPreview(video);
        }}
        onSeeked={({ currentTarget: video }) => startPreview(video)}
        onPlaying={() => setPlaying(true)}
        onPause={() => setPlaying(false)}
        onEnded={({ currentTarget: video }) => finish(video)}
        onTimeUpdate={({ currentTarget: video }) => {
          if (!initialSeekComplete.current || video.seeking) return;
          if (video.ended || video.currentTime >= end) finish(video);
          else setElapsedMs(Math.max(0, Math.round(video.currentTime * 1000) - startMs));
        }}
        onError={() => setFailed(true)}
      />
      <button type="button" className="clip-player__surface" tabIndex={-1} disabled={!ready}
        aria-label={playLabel} onClick={togglePlayback}>
        {!playing && (ready ? <span><PlayIcon /></span> : <CircularProgress size={28} aria-label="Loading clip" />)}
      </button>
      <div className="clip-player__controls">
        <Slider
          aria-label="Seek within clip"
          getAriaValueText={() => `${elapsed} of ${length}`}
          value={Math.round(100 * elapsedMs / durationMs)}
          step={1}
          disabled={!ready}
          valueLabelDisplay="auto"
          valueLabelFormat={(value) => formatTimecode(Math.floor(value * durationMs / 100), showFraction)}
          onChange={(_, value) => seek(Math.round(Number(value) * durationMs / 100))}
          className="clip-player__timeline"
        />
        <div className="clip-player__toolbar">
          <IconButton aria-label={playLabel} title={playLabel} disabled={!ready} onClick={togglePlayback}>
            <PlayIcon />
          </IconButton>
          <IconButton aria-label="Back 5 seconds" title="Back 5 seconds" disabled={!ready}
            onClick={() => jump(-5000)}>
            <Replay5Rounded />
          </IconButton>
          <IconButton aria-label="Forward 5 seconds" title="Forward 5 seconds" disabled={!ready}
            onClick={() => jump(5000)}>
            <Forward5Rounded />
          </IconButton>
          <span className="clip-player__time" dir="ltr">{elapsed}<span> / {length}</span></span>
          <IconButton aria-label={muteLabel} title={muteLabel} disabled={!ready}
            onClick={() => setMuted(!muted)}>
            {muted ? <VolumeOffRounded /> : <VolumeUpRounded />}
          </IconButton>
          {document.fullscreenEnabled && (
            <IconButton aria-label={fullscreenLabel} title={fullscreenLabel} disabled={!ready}
              onClick={toggleFullscreen}>
              {fullscreen ? <FullscreenExitRounded /> : <FullscreenRounded />}
            </IconButton>
          )}
        </div>
      </div>
    </div>
  );
}
