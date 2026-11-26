import DownloadRounded from '@mui/icons-material/DownloadRounded';
import { Alert, Button, Chip, LinearProgress, MenuItem, Stack, TextField, Typography } from '@mui/material';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef, useState, type FormEvent } from 'react';
import {
  automaticClipsKey, fetchAutomaticClips, isAutomaticClipRunActive, startAutomaticClips,
  type AutomaticClipDuration, type AutomaticClipSettings,
} from '../../services/automaticClips';
import { clipMedia, clipsKey, type CaptionSettings } from '../../services/clips';
import { highlightsKey } from '../../services/highlights';
import { projectsKey, type Project } from '../../services/projects';
import { formatTimecode } from '../../services/timecode';
import { transcriptionKey, transcriptionLanguages, type TranscriptionLanguage } from '../../services/transcription';
import CaptionEditor from '../captions/CaptionEditor';
import { workspaceHref } from '../workspace/workspaceRoute';

const durations = { SECONDS_30: '30 seconds', SECONDS_45: '45 seconds', SECONDS_60: '60 seconds' };
const statusLabels = {
  QUEUED: 'Queued', TRANSCRIBING: 'Transcribing source audio', ANALYZING: 'Finding and ranking highlights',
  RENDERING: 'Rendering vertical clips', COMPLETED: 'Batch complete', PARTIAL: 'Batch partially complete', FAILED: 'Batch failed',
};
const clipLabels = { QUEUED: 'Queued', RENDERING: 'Rendering', READY: 'Ready', FAILED: 'Failed' };

export default function AutomaticClipsPanel({ project, captions, onCaptionsChange }: {
  project: Project;
  captions: CaptionSettings;
  onCaptionsChange: (settings: CaptionSettings) => void;
}) {
  const [clipCount, setClipCount] = useState(3);
  const [duration, setDuration] = useState<AutomaticClipDuration>('SECONDS_30');
  const [language, setLanguage] = useState<TranscriptionLanguage>('AUTO');
  const [preview, setPreview] = useState<string | null>(null);
  const attempt = useRef<{ id: string; settings: string } | null>(null);
  const client = useQueryClient();
  const key = automaticClipsKey(project.id);
  const query = useQuery({
    queryKey: key,
    queryFn: ({ signal }) => fetchAutomaticClips(project.id, signal),
    refetchInterval: ({ state }) => isAutomaticClipRunActive(state.data?.status) ? 1500 : false,
    retry: 1,
  });
  const action = useMutation({
    mutationFn: ({ id, settings }: { id: string; settings: AutomaticClipSettings }) =>
      startAutomaticClips(project.id, id, settings),
    onSuccess: async (run) => {
      attempt.current = null;
      setPreview(null);
      await client.cancelQueries({ queryKey: key });
      client.setQueryData(key, run);
      void client.invalidateQueries({ queryKey: key });
    },
    onError: () => { void client.invalidateQueries({ queryKey: key }); },
  });
  const run = query.data;
  const status = run?.status;
  const active = isAutomaticClipRunActive(status);
  const runningSettings = active ? run?.settings : undefined;
  const hasAudio = !!project.video?.audioCodec;
  const disabled = query.isPending || query.isError || action.isPending || active || !hasAudio;
  const ready = run?.clips.filter((clip) => clip.status === 'READY').length ?? 0;

  useEffect(() => {
    if (!status) return;
    void client.invalidateQueries({ queryKey: projectsKey });
    void client.invalidateQueries({ queryKey: transcriptionKey(project.id) });
    void client.invalidateQueries({ queryKey: highlightsKey(project.id) });
    void client.invalidateQueries({ queryKey: clipsKey(project.id) });
  }, [client, project.id, run?.id, status]);

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (disabled) return;
    const settings = { clipCount, duration, language, captions };
    const serialized = JSON.stringify(settings);
    if (attempt.current?.settings !== serialized || attempt.current.id === run?.id) {
      attempt.current = { id: crypto.randomUUID(), settings: serialized };
    }
    action.mutate({ id: attempt.current.id, settings });
  }

  return (
    <div className="edit-automatic" data-active={active}>
      <Stack component="form" onSubmit={submit} className="edit-inspector edit-automatic__card">
        <div>
          <Typography component="h2">Automatic clips</Typography>
          <Typography variant="body2" color="text.secondary">Find highlights and export them as vertical clips.</Typography>
        </div>
        {!hasAudio && <Alert severity="info">This video needs an audio track for automatic clipping.</Alert>}
        <Stack direction={{ xs: 'column', sm: 'row' }} gap={1}>
          <TextField select label="Clip count" size="small" fullWidth value={runningSettings?.clipCount ?? clipCount} disabled={disabled}
            onChange={(event) => setClipCount(Number(event.target.value))}>
            {[1, 2, 3, 4, 5].map((count) => <MenuItem key={count} value={count}>{count}</MenuItem>)}
          </TextField>
          <TextField select label="Clip duration" size="small" fullWidth value={runningSettings?.duration ?? duration} disabled={disabled}
            onChange={(event) => setDuration(event.target.value as AutomaticClipDuration)}>
            {Object.entries(durations).map(([value, label]) => <MenuItem key={value} value={value}>{label}</MenuItem>)}
          </TextField>
        </Stack>
        <TextField select label="Language for new transcription" size="small" value={runningSettings?.language ?? language} disabled={disabled}
          onChange={(event) => setLanguage(event.target.value as TranscriptionLanguage)}>
          {transcriptionLanguages.map(({ value, name }) => <MenuItem key={value} value={value}>{name}</MenuItem>)}
        </TextField>
        <details className="edit-exact edit-automatic__captions">
          <summary>{(runningSettings?.captions ?? captions).enabled ? 'Captions on' : 'Captions off'} · Customize</summary>
          <CaptionEditor settings={runningSettings?.captions ?? captions} disabled={disabled} onChange={onCaptionsChange} />
        </details>
        {action.error && !active && <Alert severity="error">{action.error.message}</Alert>}
        <Button type="submit" variant="contained" disabled={disabled}>
          {action.isPending ? 'Starting batch…' : active ? 'Batch running' : 'Generate automatic clips'}
        </Button>
      </Stack>
      <Stack component="section" className="edit-inspector edit-automatic__card" aria-label="Batch progress and results">
        <Typography component="h2">Batch results</Typography>
        {query.isPending && <LinearProgress aria-label="Loading batch status" />}
        {query.isError && <Alert severity="error" action={
          <Button color="inherit" size="small" onClick={() => void query.refetch()}>Retry</Button>
        }>{query.error.message}</Alert>}
        {run === null && <Typography variant="body2" color="text.secondary">No automatic batch yet.</Typography>}
        {run && (
          <>
            <Stack direction="row" gap={1} flexWrap="wrap" justifyContent="space-between" role="status">
              <Typography fontWeight={650}>{statusLabels[run.status]}</Typography>
              <Typography variant="body2" color="text.secondary">{ready} of {run.settings.clipCount} clips ready</Typography>
            </Stack>
            {active && <LinearProgress aria-label={statusLabels[run.status]} />}
            {run.message && <Alert severity={run.status === 'FAILED' ? 'error' : run.status === 'PARTIAL' ? 'warning' : 'info'}>
              {run.message}
            </Alert>}
            {run.clips.length > 0 && (
              <ol className="edit-automatic__clips">
                {run.clips.map((clip) => (
                  <li key={clip.id} className="edit-automatic__clip">
                    <Stack direction="row" justifyContent="space-between" alignItems="flex-start" gap={1}>
                      <div>
                        <Typography dir="auto" fontWeight={650}>{clip.title}</Typography>
                        <Typography variant="body2" color="text.secondary">
                          {formatTimecode(clip.startMs)}–{formatTimecode(clip.endMs)}
                        </Typography>
                      </div>
                      <Chip size="small" label={clipLabels[clip.status]} color={clip.status === 'READY' ? 'success' : 'default'} />
                    </Stack>
                    {clip.error && <Typography variant="body2" color="error">{clip.error}</Typography>}
                    {clip.status === 'READY' && (
                      <Stack direction="row" gap={1} flexWrap="wrap">
                        <Button size="small" onClick={() => setPreview(preview === clip.id ? null : clip.id)}>
                          {preview === clip.id ? 'Close MP4 preview' : 'Preview MP4'}
                        </Button>
                        <Button size="small" startIcon={<DownloadRounded />} href={clipMedia(project.id, clip.id, 'download')} download>
                          Download MP4
                        </Button>
                      </Stack>
                    )}
                    {preview === clip.id && clip.status === 'READY' && (
                      <video controls playsInline preload="metadata" src={clipMedia(project.id, clip.id, 'preview')}
                        aria-label={`Rendered clip: ${clip.title}`} style={{ width: '100%', maxHeight: '54vh', background: '#090d1a' }} />
                    )}
                  </li>
                ))}
              </ol>
            )}
            {ready > 0 && <Button href={workspaceHref(project.id, 'exports')}>View all exports</Button>}
          </>
        )}
      </Stack>
    </div>
  );
}
