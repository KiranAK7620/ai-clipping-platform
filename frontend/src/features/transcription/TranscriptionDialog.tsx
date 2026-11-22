import {
  Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, LinearProgress,
  MenuItem, Stack, TextField, Typography,
} from '@mui/material';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
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

export default function TranscriptionDialog({ project, onClose }: { project: Project; onClose: () => void }) {
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
    },
  });

  const state = query.data;
  const active = isTranscriptionActive(state?.status);
  const canStart = state && !active;
  const transcript = state?.transcript;

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm" aria-labelledby="transcript-title">
      <DialogTitle id="transcript-title">Transcript</DialogTitle>
      <DialogContent dividers>
        <Stack spacing={2}>
          <Typography color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>{project.name}</Typography>
          {query.isPending && <LinearProgress aria-label="Loading transcript" />}
          {query.isError && (
            <Alert severity="error" action={
              <Button color="inherit" size="small" onClick={() => void query.refetch()}>Retry</Button>
            }>{query.error.message}</Alert>
          )}
          {state && <Typography role="status">{statusLabels[state.status]}</Typography>}
          {active && <LinearProgress aria-label="Transcription progress" />}
          {state?.error && <Alert severity="error">{state.error}</Alert>}
          {action.error && <Alert severity="error">{action.error.message}</Alert>}
          {canStart && (
            <TextField select fullWidth label="Spoken language" value={language} disabled={action.isPending}
              onChange={(event) => setLanguage(event.target.value as TranscriptionLanguage)}>
              {languages.map(({ value, name }) => <MenuItem key={value} value={value}>{name}</MenuItem>)}
            </TextField>
          )}
          {transcript && (
            <>
              {state?.status !== 'COMPLETED' && (
                <Typography variant="body2" color="text.secondary">Last saved transcript</Typography>
              )}
              {transcript.language && (
                <Typography variant="body2" color="text.secondary">{languageNames.of(transcript.language)}</Typography>
              )}
              {transcript.segments.length === 0 ? (
                <Typography color="text.secondary">No timestamped speech was found.</Typography>
              ) : (
                <ol className="transcript-segments">
                  {transcript.segments.map((segment, index) => (
                    <li key={index}>
                      <span className="transcript-time">
                        {formatTimecode(segment.startMs)} – {formatTimecode(segment.endMs)}
                      </span>
                      <Typography dir="auto">{segment.text}</Typography>
                    </li>
                  ))}
                </ol>
              )}
            </>
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
        {active && (
          <Button color="warning" disabled={action.isPending} onClick={() => action.mutate('cancel')}>
            Cancel transcription
          </Button>
        )}
        {canStart && (
          <Button variant="contained" disabled={action.isPending} onClick={() => action.mutate('start')}>
            {state.status === 'COMPLETED' ? 'Transcribe again'
              : state.status === 'NOT_STARTED' ? 'Start transcription' : 'Retry transcription'}
          </Button>
        )}
      </DialogActions>
    </Dialog>
  );
}
