import {
  Alert, Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle, LinearProgress,
  Paper, Stack, TextField, Typography,
} from '@mui/material';
import { useIsMutating, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import {
  fetchHighlights, generateHighlights, highlightSource, highlightsKey, updateHighlight,
  type Highlight, type HighlightDecision, type HighlightEdit,
} from '../../services/highlights';
import type { Project } from '../../services/projects';
import { formatTimecode, parseTimecode } from '../../services/timecode';
import HighlightPreview from './HighlightPreview';

interface CardProps {
  highlight: Highlight;
  maxMs: number;
  previewUrl: string | null;
  busy: boolean;
  onPreview: () => void;
  onSave: (edit: HighlightEdit) => void;
}

const decisions = { PENDING: 'To review', SELECTED: 'Selected', REJECTED: 'Rejected' };

function HighlightCard({ highlight, maxMs, previewUrl, busy, onPreview, onSave }: CardProps) {
  const [draft, setDraft] = useState<{ start: string; end: string } | null>(null);
  const savedStart = formatTimecode(highlight.startMs);
  const savedEnd = formatTimecode(highlight.endMs);
  const start = draft?.start ?? savedStart;
  const end = draft?.end ?? savedEnd;
  // Keep the exact saved boundary when only the other field or the decision changes.
  const startMs = start === savedStart ? highlight.startMs : parseTimecode(start);
  const endMs = end === savedEnd ? highlight.endMs : parseTimecode(end);
  const valid = startMs !== null && endMs !== null && endMs > startMs && endMs <= maxMs;
  const changed = startMs !== highlight.startMs || endMs !== highlight.endMs;
  const save = (decision: HighlightDecision) => {
    if (valid) onSave({ startMs, endMs, decision });
  };

  return (
    <Paper variant="outlined" component="article" sx={{ p: 2 }}>
      <Stack spacing={1.5}>
        <Stack direction="row" spacing={1} alignItems="center" justifyContent="space-between">
          <Typography component="h3" fontWeight={650} dir="auto">{highlight.title}</Typography>
          <Chip label={decisions[highlight.decision]} size="small"
            color={highlight.decision === 'SELECTED' ? 'success' : 'default'} />
        </Stack>
        <Typography fontWeight={550} dir="auto">{highlight.hook}</Typography>
        <Typography variant="body2" color="text.secondary" dir="auto">{highlight.description}</Typography>
        <Typography variant="body2" color="text.secondary" dir="auto">{highlight.reason}</Typography>
        <Stack direction={{ xs: 'column', sm: 'row' }} gap={1.5}>
          <TextField label="Start time" size="small" value={start} error={startMs === null}
            disabled={busy} slotProps={{ htmlInput: { dir: 'ltr', spellCheck: false } }}
            onChange={(event) => setDraft({ start: event.target.value, end })} />
          <TextField label="End time" size="small" value={end} error={endMs === null || (startMs !== null && !valid)}
            disabled={busy} slotProps={{ htmlInput: { dir: 'ltr', spellCheck: false } }}
            onChange={(event) => setDraft({ start, end: event.target.value })} />
        </Stack>
        {!valid && (
          <Typography variant="body2" color="error">
            {startMs === null || endMs === null
              ? 'Use HH:MM:SS with optional milliseconds.'
              : `Enter a range within 00:00:00–${formatTimecode(maxMs)}, with the end after the start.`}
          </Typography>
        )}
        {previewUrl && valid && (
          <HighlightPreview
            key={`${startMs}:${endMs}`}
            src={previewUrl}
            title={highlight.title}
            startMs={startMs}
            endMs={endMs}
          />
        )}
        <Stack direction="row" useFlexGap flexWrap="wrap" gap={1} alignItems="center">
          <Button size="small" disabled={!valid} onClick={onPreview}>
            {previewUrl ? 'Close preview' : 'Preview'}
          </Button>
          {highlight.decision !== 'SELECTED' && (
            <Button size="small" variant="contained" disabled={!valid || busy} onClick={() => save('SELECTED')}>
              Select
            </Button>
          )}
          {highlight.decision === 'PENDING' ? (
            <Button size="small" disabled={!valid || busy} onClick={() => save('REJECTED')}>Reject</Button>
          ) : (
            <Button size="small" disabled={!valid || busy} onClick={() => save('PENDING')}>Clear decision</Button>
          )}
          {changed && (
            <Button size="small" disabled={!valid || busy} onClick={() => save(highlight.decision)}>Save timing</Button>
          )}
        </Stack>
      </Stack>
    </Paper>
  );
}

export default function HighlightsDialog({ project, onClose }: { project: Project; onClose: () => void }) {
  const [preview, setPreview] = useState<string | null>(null);
  const client = useQueryClient();
  const queryKey = highlightsKey(project.id);
  const query = useQuery({
    queryKey, queryFn: ({ signal }) => fetchHighlights(project.id, signal), retry: 1,
  });
  const generationKey = ['generate-highlights', project.id];
  const generating = useIsMutating({ mutationKey: generationKey }) > 0;
  const generation = useMutation({
    mutationKey: generationKey,
    mutationFn: () => generateHighlights(project.id),
    onSuccess: async () => { await client.invalidateQueries({ queryKey }); },
  });
  const update = useMutation({
    mutationFn: ({ id, edit }: { id: string; edit: HighlightEdit }) => updateHighlight(project.id, id, edit),
    onSuccess: async () => { await client.invalidateQueries({ queryKey }); },
  });

  const result = query.data;
  const items = result?.highlights ?? [];
  const selected = items.filter((item) => item.decision === 'SELECTED').length;
  const error = query.error ?? generation.error ?? update.error;

  return (
    <Dialog open fullWidth maxWidth="md" onClose={onClose} aria-labelledby="highlights-title">
      <DialogTitle id="highlights-title">AI Highlights</DialogTitle>
      <DialogContent dividers>
        <Stack spacing={2}>
          <Typography color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>{project.name}</Typography>
          {error && <Alert severity="error">{error.message}</Alert>}
          {query.isPending && <LinearProgress aria-label="Loading highlights" />}
          {generating && (
            <Stack spacing={1} role="status">
              <Typography variant="body2">Analyzing the transcript with the local model…</Typography>
              <LinearProgress aria-label="Generating highlights" />
            </Stack>
          )}
          {result?.transcriptRevision === null && <Alert severity="info">Transcribe this video to generate highlights.</Alert>}
          {items.length > 0 && (
            <Stack spacing={0.5}>
              <Typography variant="body2" color="text.secondary" role="status">
                {selected} selected of {items.length} suggestions
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Times use HH:MM:SS from the start of the original video.
              </Typography>
            </Stack>
          )}
          {items.map((item) => (
            <HighlightCard key={item.id} highlight={item}
              maxMs={Math.floor((project.video?.durationSeconds ?? 0) * 1000)}
              previewUrl={preview === item.id ? highlightSource(project.id) : null}
              busy={update.isPending}
              onPreview={() => setPreview(preview === item.id ? null : item.id)}
              onSave={(edit) => update.mutate({ id: item.id, edit })} />
          ))}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
        {result?.transcriptRevision != null && items.length === 0 && (
          <Button variant="contained" disabled={generating} onClick={() => generation.mutate()}>
            Generate highlights
          </Button>
        )}
      </DialogActions>
    </Dialog>
  );
}
