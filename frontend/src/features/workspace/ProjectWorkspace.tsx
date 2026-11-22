import ArrowBackRounded from '@mui/icons-material/ArrowBackRounded';
import ContentCutRounded from '@mui/icons-material/ContentCutRounded';
import FileDownloadOutlined from '@mui/icons-material/FileDownloadOutlined';
import SubtitlesOutlined from '@mui/icons-material/SubtitlesOutlined';
import { Alert, Button, LinearProgress, Typography } from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { defaultCaptions } from '../../services/clips';
import ClipLibrary from '../clips/ClipLibrary';
import { formatDuration } from '../dashboard/projectFormatting';
import HighlightWorkspace from '../highlights/HighlightWorkspace';
import TranscriptPanel from '../transcription/TranscriptPanel';
import { fetchProject, projectKey } from '../../services/projects';
import { workspaceHref, type WorkspaceView } from './workspaceRoute';
import './workspace.css';

const views = [
  { id: 'highlights', label: 'Highlights', Icon: ContentCutRounded },
  { id: 'transcript', label: 'Transcript', Icon: SubtitlesOutlined },
  { id: 'exports', label: 'Exports', Icon: FileDownloadOutlined },
] as const;

export default function ProjectWorkspace({ projectId, view }: { projectId: string; view: WorkspaceView }) {
  const [captions, setCaptions] = useState(defaultCaptions);
  const query = useQuery({
    queryKey: projectKey(projectId),
    queryFn: ({ signal }) => fetchProject(projectId, signal),
    retry: 1,
    staleTime: 60_000,
  });
  useEffect(() => {
    window.scrollTo(0, 0);
  }, [projectId, view]);

  const project = query.data;

  return (
    <div className="edit-workspace">
      <header className="edit-workspace__header">
        <a className="edit-back" href="#projects"><ArrowBackRounded fontSize="small" /> Projects</a>
        <div className="edit-workspace__title">
          <Typography component="h1" variant="h1">{project?.name ?? 'Project'}</Typography>
          {project?.video && (
            <span className="edit-workspace__source" title={project.video.originalName}>
              {project.video.originalName} <span>· {formatDuration(project.video.durationSeconds)}</span>
            </span>
          )}
        </div>
      </header>
      {query.isPending && <LinearProgress aria-label="Loading project" />}
      {query.isError && <Alert severity="error" action={
        <Button color="inherit" size="small" onClick={() => void query.refetch()}>Retry</Button>
      }>{query.error.message}</Alert>}
      {project?.video && (
        <>
          <nav className="edit-workspace__tabs" aria-label="Project views">
            {views.map(({ id, label, Icon }) => (
              <a key={id} href={workspaceHref(project.id, id)} aria-current={view === id ? 'page' : undefined}>
                <Icon fontSize="small" /> {label}
              </a>
            ))}
          </nav>
          {view === 'highlights' && <HighlightWorkspace project={project} captions={captions} onCaptionsChange={setCaptions} />}
          {view === 'transcript' && <TranscriptPanel project={project} />}
          {view === 'exports' && <ClipLibrary projectId={projectId} onUseCaptions={setCaptions} />}
        </>
      )}
      {project && !project.video && <Alert severity="info">This project has no source video.</Alert>}
    </div>
  );
}
