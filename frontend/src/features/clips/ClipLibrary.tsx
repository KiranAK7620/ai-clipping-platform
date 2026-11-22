import MovieCreationOutlined from '@mui/icons-material/MovieCreationOutlined';
import DownloadRounded from '@mui/icons-material/DownloadRounded';
import { Alert, Button, Chip, LinearProgress, Paper, Stack, Typography } from '@mui/material';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { captionFontNames, clipMedia, clipsKey, fetchClips, type CaptionSettings } from '../../services/clips';
import { projectsKey } from '../../services/projects';
import { formatTimecode } from '../../services/timecode';
import { workspaceHref } from '../workspace/workspaceRoute';

const statusLabels = { QUEUED: 'Queued', RENDERING: 'Rendering', READY: 'Ready', FAILED: 'Failed' };

export default function ClipLibrary({ projectId, onUseCaptions }: {
  projectId: string;
  onUseCaptions: (settings: CaptionSettings) => void;
}) {
  const [preview, setPreview] = useState<string | null>(null);
  const query = useQuery({
    queryKey: clipsKey(projectId),
    queryFn: ({ signal }) => fetchClips(projectId, signal),
    refetchInterval: ({ state }) => state.data?.some((clip) => clip.status === 'QUEUED' || clip.status === 'RENDERING')
      ? 1500 : false,
    retry: 1,
  });
  const client = useQueryClient();
  const ready = query.data?.filter((clip) => clip.status === 'READY').length;
  useEffect(() => {
    if (ready !== undefined) void client.invalidateQueries({ queryKey: projectsKey });
  }, [client, ready]);

  const clips = query.data ?? [];
  if (query.isError) return <Alert severity="error" action={
    <Button color="inherit" size="small" onClick={() => void query.refetch()}>Retry</Button>
  }>{query.error.message}</Alert>;
  if (query.isPending) return <LinearProgress aria-label="Loading exports" />;
  if (!clips.length) return (
    <section className="edit-empty">
      <MovieCreationOutlined />
      <Typography component="h2">No exports yet</Typography>
      <Typography color="text.secondary">Generate a clip from a highlight to preview and download it here.</Typography>
      <Button href={workspaceHref(projectId)}>Open highlights</Button>
    </section>
  );

  return (
    <section className="edit-exports" aria-labelledby="generated-clips-title">
      <header className="edit-exports__head">
        <Typography id="generated-clips-title" component="h2">Generated clips</Typography>
        <span>{clips.length} {clips.length === 1 ? 'export' : 'exports'}</span>
      </header>
      <div className="edit-exports__grid">
      {clips.map((clip) => (
        <Paper component="article" className="edit-export-card" key={clip.id} variant="outlined" sx={{ p: 2 }}>
          <Stack spacing={1}>
            <Stack direction="row" justifyContent="space-between" alignItems="flex-start" gap={1}>
              <div>
                <Typography fontWeight={650} dir="auto">{clip.title}</Typography>
                <Typography variant="body2" color="text.secondary">
                  {formatTimecode(clip.startMs)}–{formatTimecode(clip.endMs)} · 9:16 MP4
                  {' · '}{clip.captions.enabled
                    ? `Captions: ${captionFontNames[clip.captions.fontFamily]} ${clip.captions.fontSize} px`
                    : 'Captions off'}
                </Typography>
              </div>
              <Chip size="small" label={statusLabels[clip.status]} color={clip.status === 'READY' ? 'success' : 'default'} />
            </Stack>
            {(clip.status === 'QUEUED' || clip.status === 'RENDERING') &&
              <LinearProgress aria-label="Rendering clip" />}
            {clip.status === 'FAILED' &&
              <Alert severity="error">{clip.error} Use the same export options on the highlight to retry.</Alert>}
            {clip.status === 'READY' && (
              <>
                <Stack direction="row" gap={1} useFlexGap flexWrap="wrap">
                  <Button size="small" onClick={() => setPreview(preview === clip.id ? null : clip.id)}>
                    {preview === clip.id ? 'Close output preview' : 'Preview MP4'}
                  </Button>
                  <Button size="small" variant="outlined" startIcon={<DownloadRounded />}
                    href={clipMedia(projectId, clip.id, 'download')} download>
                    Download MP4
                  </Button>
                  <Button size="small" href={workspaceHref(projectId)} onClick={() => onUseCaptions(clip.captions)}>
                    Use caption settings
                  </Button>
                </Stack>
                {preview === clip.id && (
                  <video controls playsInline preload="metadata" src={clipMedia(projectId, clip.id, 'preview')}
                    aria-label={`Rendered clip: ${clip.title}`}
                    style={{ width: '100%', maxHeight: '54vh', background: '#090d1a', borderRadius: 8 }} />
                )}
              </>
            )}
          </Stack>
        </Paper>
      ))}
      </div>
    </section>
  );
}
