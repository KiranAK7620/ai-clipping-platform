import CheckRounded from '@mui/icons-material/CheckRounded';
import ContentCutRounded from '@mui/icons-material/ContentCutRounded';
import GraphicEqRounded from '@mui/icons-material/GraphicEqRounded';
import ZoomInRounded from '@mui/icons-material/ZoomInRounded';
import ZoomOutRounded from '@mui/icons-material/ZoomOutRounded';
import { Alert, Button, LinearProgress, Slider, TextField, Typography } from '@mui/material';
import { useIsMutating, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { clipsKey, queueClip, type CaptionSettings, type Clip, type ClipDuration } from '../../services/clips';
import {
  fetchHighlights, generateHighlights, highlightSource, highlightsKey, updateHighlight,
  type Highlight, type HighlightDecision, type HighlightResult,
} from '../../services/highlights';
import { projectsKey, type Project } from '../../services/projects';
import { formatTimecode, parseTimecode } from '../../services/timecode';
import { fetchTranscription, isTranscriptionActive, transcriptionKey, type Segment } from '../../services/transcription';
import { workspaceHref } from '../workspace/workspaceRoute';
import CaptionEditor from '../captions/CaptionEditor';
import HighlightPreview from './HighlightPreview';

type Cut = { startMs: number; endMs: number };
type EditAction = 'save' | 'export' | HighlightDecision;
const durations: { value: ClipDuration; label: string; milliseconds?: number }[] = [
  { value: 'SAVED_RANGE', label: 'Current cut' },
  { value: 'SECONDS_30', label: '30 s', milliseconds: 30_000 },
  { value: 'SECONDS_45', label: '45 s', milliseconds: 45_000 },
  { value: 'SECONDS_60', label: '60 s', milliseconds: 60_000 },
];
function presetRange(cut: Cut, duration: ClipDuration, maxMs: number): Cut {
  const requested = durations.find(({ value }) => value === duration)?.milliseconds;
  if (!requested) return cut;
  const length = Math.min(requested, maxMs);
  const startMs = Math.min(cut.startMs, maxMs - length);
  return { startMs, endMs: startMs + length };
}

function zoomWindow(cut: Cut, maxMs: number): Cut {
  const length = cut.endMs - cut.startMs;
  const span = Math.min(maxMs, Math.max(20_000, length * 2));
  const startMs = Math.max(0, Math.min(cut.startMs - Math.floor((span - length) / 2), maxMs - span));
  return { startMs, endMs: startMs + span };
}

function TimeField({ label, value, disabled, onChange }: {
  label: string;
  value: number;
  disabled: boolean;
  onChange: (time: number) => boolean;
}) {
  const [draft, setDraft] = useState<string | null>(null);
  const [invalid, setInvalid] = useState(false);
  const time = formatTimecode(value, value % 1000 !== 0);
  function commit() {
    if (draft === null || draft.trim() === time) {
      setDraft(null);
      return;
    }
    const parsed = parseTimecode(draft);
    if (parsed === null || !onChange(parsed)) {
      setInvalid(true);
      return;
    }
    setDraft(null);
  }
  return (
    <TextField label={label} size="small" value={draft ?? time} disabled={disabled}
      error={invalid} helperText={invalid ? 'Enter a valid time in the source range.' : undefined}
      onChange={(event) => { setDraft(event.target.value); setInvalid(false); }}
      onBlur={commit}
      onKeyDown={(event) => {
        if (event.key === 'Enter') {
          event.preventDefault();
          (event.target as HTMLInputElement).blur();
        }
        if (event.key === 'Escape') {
          setDraft(null);
          setInvalid(false);
        }
      }}
      slotProps={{ htmlInput: { dir: 'ltr', spellCheck: false } }} />
  );
}

function HighlightEditor({ project, highlight, segments, captions, onCaptionsChange }: {
  project: Project;
  highlight: Highlight;
  segments: Segment[];
  captions: CaptionSettings;
  onCaptionsChange: (settings: CaptionSettings) => void;
}) {
  const client = useQueryClient();
  const [draft, setDraft] = useState<Cut | null>(null);
  const [duration, setDuration] = useState<ClipDuration>('SAVED_RANGE');
  const [portrait, setPortrait] = useState(false);
  const [dragPreview, setDragPreview] = useState<Cut | null>(null);
  const [timelineWindow, setTimelineWindow] = useState<Cut | null>(null);
  const maxMs = Math.floor((project.video?.durationSeconds ?? 0) * 1000);
  const cut = draft ?? highlight;
  const active = presetRange(cut, duration, maxMs);
  const preview = dragPreview ?? active;
  const length = active.endMs - active.startMs;
  const timeline = timelineWindow ?? { startMs: 0, endMs: maxMs };
  const dirty = cut.startMs !== highlight.startMs || cut.endMs !== highlight.endMs;
  const key = highlightsKey(project.id);
  const edit = useMutation({
    mutationFn: async (action: EditAction) => {
      const decision = action === 'export' ? 'SELECTED' : action === 'save' ? highlight.decision : action;
      if (dirty || decision !== highlight.decision) {
        const saved = await updateHighlight(project.id, highlight.id, {
          startMs: cut.startMs, endMs: cut.endMs, decision,
        });
        await client.cancelQueries({ queryKey: key });
        client.setQueryData<HighlightResult>(key, (current) => current && ({
          ...current, highlights: current.highlights.map((item) => item.id === saved.id ? saved : item),
        }));
        setDraft(null);
      }
      return action === 'export' ? queueClip(project.id, highlight.id, duration, captions) : null;
    },
    onSuccess: async (clip) => {
      if (!clip) return;
      const exportsKey = clipsKey(project.id);
      await client.cancelQueries({ queryKey: exportsKey });
      client.setQueryData<Clip[]>(exportsKey, (clips = []) => [clip, ...clips.filter((item) => item.id !== clip.id)]);
      void client.invalidateQueries({ queryKey: exportsKey });
      void client.invalidateQueries({ queryKey: projectsKey });
      if (window.location.hash.startsWith(`#project/${project.id}/`)) {
        window.location.hash = workspaceHref(project.id, 'exports').slice(1);
      }
    },
  });

  function setCut(startMs: number, endMs: number) {
    if (startMs < 0 || endMs > maxMs || startMs >= endMs) return false;
    setDuration('SAVED_RANGE');
    const next = { startMs, endMs };
    setDraft(next);
    if (timelineWindow && (startMs < timelineWindow.startMs || endMs > timelineWindow.endMs)) {
      setTimelineWindow(zoomWindow(next, maxMs));
    }
    return true;
  }

  function pickDuration(next: ClipDuration) {
    setDuration(next);
    if (timelineWindow) setTimelineWindow(zoomWindow(presetRange(cut, next, maxMs), maxMs));
  }

  return (
    <>
      <section className="edit-canvas" aria-label="Cut editor">
        <div className="edit-canvas__head">
          <div>
            <span className="edit-label">Source preview</span>
            <Typography component="h2" dir="auto">{highlight.title}</Typography>
          </div>
          <div className="edit-canvas__view">
            <span className="edit-time-pill" title="Clip duration">{formatTimecode(length, length < 1000)}</span>
            <Button size="small" aria-pressed={portrait} onClick={() => setPortrait(!portrait)}>
              Portrait preview
            </Button>
          </div>
        </div>
        <HighlightPreview
          key={`${preview.startMs}:${preview.endMs}`}
          src={highlightSource(project.id)}
          title={highlight.title}
          startMs={preview.startMs}
          endMs={preview.endMs}
          autoPlay={false}
          captions={captions}
          portrait={portrait}
          captionSegments={segments}
        />
        <div className="edit-timeline">
          <div className="edit-timeline__head">
            <Typography component="h3">Cut range</Typography>
            <span dir="ltr">{formatTimecode(active.startMs)} – {formatTimecode(active.endMs)}</span>
          </div>
          <div className="edit-timeline__rail"
            onPointerDown={() => setDragPreview(active)} onPointerUp={() => setDragPreview(null)}
            onPointerCancel={() => setDragPreview(null)}>
            <Slider min={timeline.startMs} max={timeline.endMs} step={1} shiftStep={1000} disableSwap
              value={[active.startMs, active.endMs]}
              disabled={edit.isPending}
              getAriaLabel={(index) => index === 0 ? 'Clip start' : 'Clip end'}
              getAriaValueText={(value) => formatTimecode(value, true)}
              valueLabelDisplay="auto"
              valueLabelFormat={(value) => formatTimecode(value)}
              onChange={(_, values, thumb) => {
                if (!Array.isArray(values)) return;
                setCut(thumb === 0 ? Math.min(values[0], values[1] - 1) : values[0],
                  thumb === 1 ? Math.max(values[1], values[0] + 1) : values[1]);
              }}
              onChangeCommitted={() => setDragPreview(null)}
            />
            <div className="edit-timeline__ticks" aria-hidden="true">
              {[0, 0.25, 0.5, 0.75, 1].map((part) => (
                <span key={part}>{formatTimecode(Math.round(timeline.startMs + (timeline.endMs - timeline.startMs) * part))}</span>
              ))}
            </div>
          </div>
          <div className="edit-timeline__actions">
            <Button size="small" disabled={edit.isPending || (!timelineWindow && maxMs <= 20_000)}
              startIcon={timelineWindow ? <ZoomOutRounded /> : <ZoomInRounded />}
              onClick={() => setTimelineWindow(timelineWindow ? null : zoomWindow(active, maxMs))}>
              {timelineWindow ? 'Fit source' : 'Zoom to cut'}
            </Button>
          </div>
          <details className="edit-exact">
            <summary>Exact timecodes</summary>
            <div className="edit-exact__fields">
              <TimeField key={`start:${active.startMs}`} label="Start time" value={active.startMs}
                disabled={edit.isPending} onChange={(value) => setCut(value, active.endMs)} />
              <TimeField key={`end:${active.endMs}`} label="End time" value={active.endMs}
                disabled={edit.isPending} onChange={(value) => setCut(active.startMs, value)} />
            </div>
          </details>
        </div>
        {highlight.description && <p className="edit-canvas__description" dir="auto">{highlight.description}</p>}
        <details className="edit-exact">
          <summary>Why this highlight</summary>
          <dl className="edit-highlight-notes">
            <dt>Hook</dt><dd dir="auto">{highlight.hook}</dd>
            <dt>Reason</dt><dd dir="auto">{highlight.reason}</dd>
          </dl>
        </details>
      </section>

      <aside className="edit-inspector" aria-label="Export settings">
        <header className="edit-inspector__head">
          <div>
            <Typography component="h2">Export settings</Typography>
            <span>Vertical MP4 · 9:16</span>
          </div>
          <span className="edit-portrait" aria-hidden="true" />
        </header>
        <fieldset className="edit-fieldset" disabled={edit.isPending}>
          <legend>Duration</legend>
          <div className="edit-duration">
            {durations.map(({ value, label }) => (
              <label className="edit-choice" key={value}>
                <input type="radio" name="clip-duration" checked={duration === value}
                  onChange={() => pickDuration(value)} />
                <span>{label}</span>
              </label>
            ))}
          </div>
        </fieldset>
        <CaptionEditor settings={captions} disabled={edit.isPending} onChange={onCaptionsChange} />
        {length > 60_000 && <Alert severity="info">Trim to 60 seconds or choose a duration preset.</Alert>}
        {edit.error && <Alert severity="error">{edit.error.message}</Alert>}
        <Button variant="contained" startIcon={<ContentCutRounded />} fullWidth
          disabled={edit.isPending || length <= 0 || length > 60_000}
          onClick={() => edit.mutate('export')}>
          {edit.isPending && edit.variables === 'export' ? 'Queueing clip…' : 'Generate clip'}
        </Button>
        <div className="edit-inspector__actions">
          {dirty && (
            <Button size="small" disabled={edit.isPending} onClick={() => edit.mutate('save')}>Save cut</Button>
          )}
          {highlight.decision === 'SELECTED' ? (
            <Button size="small" disabled={edit.isPending} onClick={() => edit.mutate('PENDING')}>Clear selection</Button>
          ) : (
            <Button size="small" startIcon={<CheckRounded />} disabled={edit.isPending}
              onClick={() => edit.mutate('SELECTED')}>Select highlight</Button>
          )}
          {highlight.decision === 'REJECTED' ? (
            <Button size="small" disabled={edit.isPending} onClick={() => edit.mutate('PENDING')}>Restore</Button>
          ) : (
            <Button size="small" color="inherit" disabled={edit.isPending}
              onClick={() => edit.mutate('REJECTED')}>Reject</Button>
          )}
        </div>
      </aside>
    </>
  );
}

export default function HighlightWorkspace({ project, captions, onCaptionsChange }: {
  project: Project;
  captions: CaptionSettings;
  onCaptionsChange: (settings: CaptionSettings) => void;
}) {
  const [selected, setSelected] = useState<string | null>(null);
  const client = useQueryClient();
  const key = highlightsKey(project.id);
  const query = useQuery({
    queryKey: key,
    queryFn: ({ signal }) => fetchHighlights(project.id, signal),
    retry: 1,
  });
  const generationKey = ['generate-highlights', project.id];
  const generating = useIsMutating({ mutationKey: generationKey }) > 0;
  const generation = useMutation({
    mutationKey: generationKey,
    mutationFn: () => generateHighlights(project.id),
    onSuccess: async () => { await client.invalidateQueries({ queryKey: key }); },
  });
  const items = query.data?.highlights ?? [];
  const highlight = items.find((item) => item.id === selected) ?? items[0];
  const transcript = useQuery({
    queryKey: transcriptionKey(project.id),
    queryFn: ({ signal }) => fetchTranscription(project.id, signal),
    refetchInterval: ({ state }) => isTranscriptionActive(state.data?.status) ? 1500 : false,
  });
  const currentRevision = transcript.data?.revision;
  const highlightRevision = query.data?.transcriptRevision;
  useEffect(() => {
    if (currentRevision !== undefined && highlightRevision !== undefined && currentRevision !== highlightRevision) {
      void client.invalidateQueries({ queryKey: highlightsKey(project.id) });
    }
  }, [client, project.id, currentRevision, highlightRevision]);

  if (query.isPending) return <LinearProgress aria-label="Loading highlights" />;
  if (query.isError) return <Alert severity="error" action={
    <Button color="inherit" onClick={() => void query.refetch()}>Retry</Button>
  }>{query.error.message}</Alert>;

  if (!highlight) {
    const needsTranscript = query.data?.transcriptRevision == null;
    const transcribing = isTranscriptionActive(transcript.data?.status);
    return (
      <div className="edit-grid edit-grid--empty">
        <section className="edit-empty">
          <GraphicEqRounded />
          <Typography component="h2">{needsTranscript ? (transcribing ? 'Transcription in progress' : 'Transcribe your source video') : 'Find highlights'}</Typography>
          <Typography color="text.secondary">
            {needsTranscript ? (transcribing ? 'Highlights are available when transcription finishes.' :
                'A transcript gives ClipTova the timing and words it needs to suggest cuts.') :
              'Analyze the transcript with the local model to find moments you can edit and export.'}
          </Typography>
          {generation.error && <Alert severity="error">{generation.error.message}</Alert>}
          {generating && <LinearProgress aria-label="Generating highlights" />}
          {needsTranscript ? (
            <Button variant="contained" href={workspaceHref(project.id, 'transcript')}>Open transcript</Button>
          ) : (
            <Button variant="contained" disabled={generating} onClick={() => generation.mutate()}>
              {generating ? 'Generating highlights…' : 'Generate highlights'}
            </Button>
          )}
        </section>
        <aside className="edit-inspector" aria-label="Caption settings for this project">
          <CaptionEditor settings={captions} disabled={false} onChange={onCaptionsChange} />
        </aside>
      </div>
    );
  }

  return (
    <div className="edit-grid">
      <aside className="edit-highlights" aria-label="Highlight suggestions">
        <div className="edit-highlights__heading">
          <Typography component="h2">Highlights</Typography>
          <span>{items.length}</span>
        </div>
        <div className="edit-highlights__list">
          {items.map((item) => (
            <button type="button" key={item.id} className="edit-highlight"
              aria-pressed={item.id === highlight.id} onClick={() => setSelected(item.id)}>
              <span className="edit-highlight__time" dir="ltr">{formatTimecode(item.startMs)}–{formatTimecode(item.endMs)}</span>
              <span className="edit-highlight__title" dir="auto">{item.title}</span>
              {item.decision !== 'PENDING' && (
                <span className="edit-highlight__decision" data-decision={item.decision}>
                  {item.decision === 'SELECTED' ? 'Selected' : 'Rejected'}
                </span>
              )}
            </button>
          ))}
        </div>
      </aside>
      <HighlightEditor key={highlight.id} project={project} highlight={highlight}
        captions={captions} onCaptionsChange={onCaptionsChange} segments={transcript.data?.transcript?.segments ?? []} />
    </div>
  );
}
