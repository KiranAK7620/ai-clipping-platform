import AddRounded from '@mui/icons-material/AddRounded';
import AnalyticsOutlined from '@mui/icons-material/AnalyticsOutlined';
import DashboardOutlined from '@mui/icons-material/DashboardOutlined';
import FolderOutlined from '@mui/icons-material/FolderOutlined';
import GridViewRounded from '@mui/icons-material/GridViewRounded';
import MovieFilterOutlined from '@mui/icons-material/MovieFilterOutlined';
import { Button, Typography } from '@mui/material';
import { lazy, Suspense, useEffect, useState } from 'react';
import { Dashboard } from '../features/dashboard/Dashboard';
import type { Category } from '../features/dashboard/categories';
import { useHealth } from '../services/health';

const NewProjectDialog = lazy(() => import('../features/dashboard/NewProjectDialog'));

const navigation = [
  { id: 'overview', label: 'Overview', Icon: DashboardOutlined },
  { id: 'activity', label: 'Processing activity', Icon: AnalyticsOutlined },
  { id: 'projects', label: 'Recent projects', Icon: FolderOutlined },
  { id: 'categories', label: 'Content categories', Icon: GridViewRounded },
];

function ServiceStatus() {
  const health = useHealth();
  let label = 'Checking system';
  let state = 'pending';

  if (health.isError) {
    label = 'Backend unavailable';
    state = 'down';
  } else if (health.data?.status === 'UP') {
    label = 'System operational';
    state = 'up';
  } else if (health.data?.status === 'DOWN') {
    label = 'Database unavailable';
    state = 'down';
  }

  return (
    <div className="service-status" role="status">
      <span className="status-dot" data-state={state} aria-hidden="true" />
      {label}
    </div>
  );
}

export function App() {
  const [activeSection, setActiveSection] = useState(() => window.location.hash.slice(1) || 'overview');
  const [projectDraft, setProjectDraft] = useState<{ category?: Category } | null>(null);

  useEffect(() => {
    const onHashChange = () => setActiveSection(window.location.hash.slice(1) || 'overview');
    window.addEventListener('hashchange', onHashChange);
    return () => window.removeEventListener('hashchange', onHashChange);
  }, []);

  const openProject = (category?: Category) => setProjectDraft({ category });

  return (
    <div className="app-shell" id="overview">
      <a className="skip-link" href="#studio">Skip to content</a>
      <aside className="sidebar">
        <a className="brand" href="#overview" aria-label="ClipTova overview">
          <span className="brand-mark"><MovieFilterOutlined fontSize="small" /></span>
          ClipTova
        </a>
        <nav className="studio-nav" aria-label="Studio navigation">
          {navigation.map(({ id, label, Icon }) => (
            <a key={id} href={`#${id}`} aria-current={activeSection === id ? 'location' : undefined}>
              <Icon fontSize="small" />
              {label}
            </a>
          ))}
        </nav>
        <ServiceStatus />
      </aside>

      <main className="studio" id="studio" tabIndex={-1}>
        <div className="workspace-heading">
          <Typography component="h1" variant="h1">Dashboard</Typography>
          <Button className="workspace-action" variant="contained" startIcon={<AddRounded />}
            onClick={() => openProject()}>
            New project
          </Button>
        </div>
        <Dashboard onNewProject={openProject} />
      </main>

      {projectDraft && (
        <Suspense fallback={null}>
          <NewProjectDialog category={projectDraft.category} onClose={() => setProjectDraft(null)}
            onUploaded={() => {
              setProjectDraft(null);
              window.location.hash = 'projects';
            }} />
        </Suspense>
      )}
    </div>
  );
}
