import AddRounded from '@mui/icons-material/AddRounded';
import CheckCircleOutlineRounded from '@mui/icons-material/CheckCircleOutlineRounded';
import ChevronRightRounded from '@mui/icons-material/ChevronRightRounded';
import CloudUploadOutlined from '@mui/icons-material/CloudUploadOutlined';
import ContentCutRounded from '@mui/icons-material/ContentCutRounded';
import FolderOpenRounded from '@mui/icons-material/FolderOpenRounded';
import PlayArrowRounded from '@mui/icons-material/PlayArrowRounded';
import TimelapseRounded from '@mui/icons-material/TimelapseRounded';
import { Button, ButtonBase, Typography } from '@mui/material';
import { categories, type Category } from './categories';

const metrics = [
  { label: 'Uploaded videos', Icon: CloudUploadOutlined, tone: 'blue' },
  { label: 'Processing jobs', Icon: TimelapseRounded, tone: 'amber' },
  { label: 'Completed projects', Icon: CheckCircleOutlineRounded, tone: 'green' },
  { label: 'Generated clips', Icon: ContentCutRounded, tone: 'violet' },
] as const;

function TimelineArtwork() {
  return (
    <div className="timeline-artwork" aria-hidden="true">
      <div className="source-frame">
        <div className="frame-preview"><PlayArrowRounded /></div>
        <div className="timeline-track"><span /><span /><span /><span /></div>
        <div className="timeline-playhead" />
      </div>
      <div className="clip-frame">
        <div className="clip-preview"><PlayArrowRounded /></div>
        <span />
        <span />
      </div>
    </div>
  );
}

export function Dashboard({ onNewProject }: { onNewProject: (category?: Category) => void }) {
  return (
    <>
      <section className="hero" aria-labelledby="studio-tagline">
        <div className="hero-copy">
          <Typography component="h2" id="studio-tagline" className="hero-title">
            Every long video has a <span>short story.</span>
          </Typography>
          <Typography className="hero-description" color="text.secondary">
            Start with a source video and a content category.
          </Typography>
          <Button className="upload-action" variant="contained" startIcon={<CloudUploadOutlined />}
            onClick={() => onNewProject()}>
            Upload video
          </Button>
        </div>
        <TimelineArtwork />
      </section>

      <section className="activity" id="activity" aria-labelledby="activity-title">
        <Typography id="activity-title" variant="h2">Processing activity</Typography>
        <dl className="metrics">
          {metrics.map(({ label, Icon, tone }) => (
            <div className="metric" key={label}>
              <span className="icon-tile" data-tone={tone} aria-hidden="true"><Icon fontSize="small" /></span>
              <dt>{label}</dt>
              <dd>0</dd>
            </div>
          ))}
        </dl>
      </section>

      <div className="dashboard-columns">
        <section className="panel projects-panel" id="projects" aria-labelledby="projects-title">
          <Typography id="projects-title" variant="h2">Recent projects</Typography>
          <div className="empty-projects">
            <span className="icon-tile" data-tone="blue"><FolderOpenRounded /></span>
            <Typography fontWeight={650}>No projects yet</Typography>
            <Button startIcon={<AddRounded />} onClick={() => onNewProject()}>New project</Button>
          </div>
        </section>

        <section className="panel" id="categories" aria-labelledby="categories-title">
          <Typography id="categories-title" variant="h2">Content categories</Typography>
          <div className="category-list">
            {categories.map(({ name, description, Icon, tone }) => (
              <ButtonBase className="category" key={name} onClick={() => onNewProject(name)}>
                <span className="icon-tile" data-tone={tone}><Icon fontSize="small" /></span>
                <span className="category-copy">
                  <strong>{name}</strong>
                  <span>{description}</span>
                </span>
                <ChevronRightRounded className="category-chevron" fontSize="small" />
              </ButtonBase>
            ))}
          </div>
        </section>
      </div>
    </>
  );
}
