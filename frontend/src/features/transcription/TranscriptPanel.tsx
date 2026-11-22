import {
  Alert, Button, LinearProgress, Stack, TextField, Typography,
} from '@mui/material';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useRef, useState } from 'react';
import { highlightSource, highlightsKey } from '../../services/highlights';
import { projectsKey, type Project } from '../../services/projects';
import { formatTimecode } from '../../services/timecode';
import {
  cancelTranscription, fetchTranscription, isTranscriptionActive, startTranscription, transcriptionKey,
  type TranscriptionLanguage,
} from '../../services/transcription';

const statusLabels = {
  NOT_STARTED: 'Ready to transcribe',
  QUEUED: 'Queued for local transcription',
  EXTRACTING_AUDIO: 'Extracting audio',
  TRANSCRIBING: 'Transcribing audio with Whisper',
  COMPLETED: 'Transcript ready',
  FAILED: 'Transcription failed',
  CANCELLED: 'Transcription cancelled',
};

const languages: { value: TranscriptionLanguage; name: string }[] = [
  { value: 'AUTO', name: 'Detect automatically' },
  { value: 'ENGLISH', name: 'English' },
  { value: 'HINDI', name: 'Hindi / Hinglish' },
  { value: 'MARATHI', name: 'Marathi' },
];

const languageNames = new Intl.DisplayNames(undefined, { type: 'language' });

export default function TranscriptPanel({ project }: { project: Project }) {
  const source = useRef<HTMLVideoElement>(null);
  const [jumpedTo, setJumpedTo] = useState<number | null>(null);
  const [search, setSearch] = useState('');
  const [chosenLanguage, setLanguage] = useState<TranscriptionLanguage | null>(null);
  const client = useQueryClient();
  const key = transcriptionKey(project.id);
  const query = useQuery({
    queryKey: key,
    queryFn: ({ signal }) => fetchTranscription(project.id, signal),
    refetchInterval: ({ state }) => isTranscriptionActive(state.data?.status) ? 1500 : false,
    retry: 1,
  });
  const language = chosenLanguage ?? (query.data?.transcript?.language === 'hi' ? 'HINDI' : 'AUTO');
  const action = useMutation({
    mutationFn: (operation: 'start' | 'cancel') => operation === 'start'
      ? startTranscription(project.id, language,
        query.data?.status === 'COMPLETED' ? query.data.revision ?? undefined : undefined)
      : cancelTranscription(project.id),
    onSuccess: async (data) => {
      await client.cancelQueries({ queryKey: key });
      client.setQueryData(key, data);
      void client.invalidateQueries({ queryKey: projectsKey });
      void client.invalidateQueries({ queryKey: highlightsKey(project.id) });
    },
  });

  const state = query.data;
  const active = isTranscriptionActive(state?.status);
  const canStart = state && !active;
  const transcript = state?.transcript;

  const visible = transcript?.segments.filter((segment) =>
    segment.text.toLocaleLowerCase().includes(search.trim().toLocaleLowerCase())) ?? [];

  return (
    <div className="edit-transcript">
      <section className="edit-transcript__video" aria-label="Source video">
        <video controls playsInline preload="metadata" ref={source} src={highlightSource(project.id)}
          aria-label={`Source video for ${project.name}`} />
      </section>
      <section className="edit-transcript__content" aria-label="Transcript">
        <div className="edit-transcript__head">
          <div>
            <Typography component="h2">Transcript</Typography>
            {state && <span role="status">{statusLabels[state.status]}</span>}
          </div>
          {transcript?.language && <span>{languageNames.of(transcript.language)}</span>}
        </div>
        {query.isPending && <LinearProgress aria-label="Loading transcript" />}
        {query.isError && <Alert severity="error" action={
          <Button color="inherit" size="small" onClick={() => void query.refetch()}>Retry</Button>
        }>{query.error.message}</Alert>}
        {active && <LinearProgress aria-label="Transcription progress" />}
        {state?.error && <Alert severity="error">{state.error}</Alert>}
        {action.error && <Alert severity="error">{action.error.message}</Alert>}
        {transcript && transcript.segments.length > 0 && (
          <>
            <TextField size="small" fullWidth label="Search transcript" value={search}
              onChange={(event) => setSearch(event.target.value)} />
            {visible.length === 0 && <Typography color="text.secondary">No matching segments.</Typography>}
            <ol className="edit-transcript__segments" aria-label="Timestamped transcript segments">
              {visible.map((segment) => (
                <li key={`${segment.startMs}:${segment.endMs}:${segment.text}`}>
                  <button type="button" aria-pressed={jumpedTo === segment.startMs}
                    onClick={() => {
                      if (source.current) source.current.currentTime = segment.startMs / 1000;
                      setJumpedTo(segment.startMs);
                    }}>
                    <span className="transcript-time" dir="ltr">{formatTimecode(segment.startMs)}</span>
                    <span dir="auto">{segment.text}</span>
                  </button>
                </li>
              ))}
            </ol>
          </>
        )}
        {transcript?.segments.length === 0 && (
          <Typography color="text.secondary">No timestamped speech was found.</Typography>
        )}
        {canStart && (
          <details className="edit-exact" open={state.status !== 'COMPLETED'}>
            <summary>{state.status === 'COMPLETED' ? 'Transcription settings' : 'Spoken language'}</summary>
            <fieldset className="edit-fieldset" disabled={action.isPending}>
              <legend className="edit-sr-only">Spoken language</legend>
              <div className="edit-language">
                {languages.map(({ value, name }) => (
                  <label className="edit-choice" key={value}>
                    <input type="radio" name="transcription-language" checked={language === value}
                      onChange={() => setLanguage(value)} />
                    <span>{name}</span>
                  </label>
                ))}
              </div>
            </fieldset>
            <Button variant="contained" disabled={action.isPending} onClick={() => action.mutate('start')}>
              {state.status === 'COMPLETED' ? 'Transcribe again'
                : state.status === 'NOT_STARTED' ? 'Start transcription' : 'Retry transcription'}
            </Button>
          </details>
        )}
        {active && (
          <Stack direction="row" justifyContent="flex-end">
            <Button color="warning" disabled={action.isPending} onClick={() => action.mutate('cancel')}>
              Cancel transcription
            </Button>
          </Stack>
        )}
      </section>
    </div>
  );
}
