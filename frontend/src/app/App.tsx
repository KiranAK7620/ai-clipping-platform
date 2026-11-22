import AddRounded from '@mui/icons-material/AddRounded';
import AnalyticsOutlined from '@mui/icons-material/AnalyticsOutlined';
import DashboardOutlined from '@mui/icons-material/DashboardOutlined';
import FolderOutlined from '@mui/icons-material/FolderOutlined';
import GridViewRounded from '@mui/icons-material/GridViewRounded';
import MovieFilterOutlined from '@mui/icons-material/MovieFilterOutlined';
import { Button, LinearProgress, Typography } from '@mui/material';
import { lazy, Suspense, useEffect, useState } from 'react';
import { Dashboard } from '../features/dashboard/Dashboard';
import { categories, type Category } from '../features/dashboard/categories';
import { workspaceHref, workspaceRoute } from '../features/workspace/workspaceRoute';
import { useHealth } from '../services/health';

const NewProjectPage = lazy(() => import('../features/dashboard/NewProjectPage'));
const ProjectWorkspace = lazy(() => import('../features/workspace/ProjectWorkspace'));

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
  const route = workspaceRoute(activeSection);
  const [section, categoryName] = activeSection.split('/');
  const creating = section === 'new-project';
  const fullPage = creating || route !== null;

  useEffect(() => {
    const onHashChange = () => setActiveSection(window.location.hash.slice(1) || 'overview');
    window.addEventListener('hashchange', onHashChange);
    return () => window.removeEventListener('hashchange', onHashChange);
  }, []);

  useEffect(() => {
    if (!fullPage && activeSection !== 'overview') document.getElementById(activeSection)?.scrollIntoView();
  }, [activeSection, fullPage]);

  function openProject(category?: Category) {
    window.location.hash = category ? `new-project/${category}` : 'new-project';
  }

  return (
    <div className={fullPage ? "app-shell app-shell--editor" : "app-shell"} id="overview">
      <a className="skip-link" href="#studio" onClick={(event) => {
        event.preventDefault();
        document.getElementById('studio')?.focus();
      }}>Skip to content</a>
      {!fullPage && (
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
      )}

      <main className={fullPage ? 'studio studio--editor' : 'studio'} id="studio" tabIndex={-1}>
        {route || creating ? (
          <Suspense fallback={<LinearProgress aria-label="Loading workspace" />}>
            {route ? <ProjectWorkspace key={route.projectId} projectId={route.projectId} view={route.view} /> : (
              <NewProjectPage category={categories.find(({ name }) => name === categoryName)?.name}
                onUploaded={(project) => {
                  if (window.location.hash.startsWith('#new-project')) {
                    window.location.hash = workspaceHref(project.id, 'transcript').slice(1);
                  }
                }} />
            )}
          </Suspense>
        ) : (
          <>
            <div className="workspace-heading">
              <Typography component="h1" variant="h1">Dashboard</Typography>
              <Button className="workspace-action" variant="contained" startIcon={<AddRounded />}
                onClick={() => openProject()}>
                New project
              </Button>
            </div>
            <Dashboard onNewProject={openProject} />
          </>
        )}
      </main>
    </div>
  );
}
