import AddRounded from '@mui/icons-material/AddRounded';
import FolderOpenRounded from '@mui/icons-material/FolderOpenRounded';
import { Alert, Button, Skeleton, Typography } from '@mui/material';
import { useProjects, type Project } from '../../services/projects';
import { workspaceHref } from '../workspace/workspaceRoute';
import { categories } from './categories';
import { formatBytes, formatDuration } from './projectFormatting';

function ProjectCard({ project }: { project: Project }) {
  const { video } = project;
  const category = categories.find(({ name }) => name.toUpperCase() === project.category);
  return (
    <article className="project-card">
      <div className="project-card-heading">
        <div>
          <Typography component="h3" fontWeight={650}>{project.name}</Typography>
          <Typography variant="body2" color="text.secondary">
            {category?.name ?? project.category}
          </Typography>
        </div>
        <span className="project-status" data-ready={!!video}>
          {video ? 'Video ready' : 'Awaiting video'}
        </span>
      </div>
      {video && (
        <>
          <p className="source-name">{video.originalName}</p>
          <dl className="video-metadata">
            <div><dt>Duration</dt><dd>{formatDuration(video.durationSeconds)}</dd></div>
            <div><dt>Resolution</dt><dd>{video.width} × {video.height}</dd></div>
            <div><dt>Size</dt><dd>{formatBytes(video.sizeBytes)}</dd></div>
            <div><dt>Frame rate</dt><dd>{video.frameRate === null ? 'Unavailable' : `${Number(video.frameRate.toFixed(2))} fps`}</dd></div>
            <div><dt>Video codec</dt><dd>{video.videoCodec}</dd></div>
            <div><dt>Audio codec</dt><dd>{video.audioCodec ?? 'Unavailable'}</dd></div>
          </dl>
          <div className="project-transcript-action">
            <Button href={workspaceHref(project.id)} variant="outlined" size="small">Open workspace</Button>
          </div>
        </>
      )}
    </article>
  );
}

export function RecentProjects({ onNewProject }: { onNewProject: () => void }) {
  const query = useProjects();
  const projects = query.data?.pages.flatMap((page) => page.projects) ?? [];

  return (
    <section className="panel projects-panel" id="projects" aria-labelledby="projects-title">
      <Typography id="projects-title" variant="h2">Recent projects</Typography>
      {query.isPending && <Skeleton height={100} aria-label="Loading projects" />}
      {query.isError && (
        <Alert className="projects-error" severity="error" action={
          <Button color="inherit" size="small" onClick={() => void query.refetch()}>Retry</Button>
        }>
          {query.error.message}
        </Alert>
      )}
      {query.isSuccess && projects.length === 0 && (
        <div className="empty-projects">
          <span className="icon-tile" data-tone="blue"><FolderOpenRounded /></span>
          <Typography fontWeight={650}>No projects yet</Typography>
          <Button startIcon={<AddRounded />} onClick={onNewProject}>New project</Button>
        </div>
      )}
      {projects.length > 0 && (
        <div className="project-list">
          {projects.map((project) => (
            <ProjectCard key={project.id} project={project} />
          ))}
          {query.hasNextPage && (
            <Button disabled={query.isFetching} onClick={() => void query.fetchNextPage()}>
              {query.isFetchingNextPage ? 'Loading projects…' : 'Load older projects'}
            </Button>
          )}
        </div>
      )}

    </section>
  );
}
