export const workspaceViews = ['highlights', 'transcript', 'automatic', 'exports'] as const;
export type WorkspaceView = typeof workspaceViews[number];

export function workspaceHref(projectId: string, view: WorkspaceView = 'highlights') {
  return `#project/${projectId}/${view}`;
}

export function workspaceRoute(section: string) {
  const match = /^project\/([\da-f]{8}(?:-[\da-f]{4}){3}-[\da-f]{12})(?:\/([a-z]+))?$/i.exec(section);
  if (!match) return null;
  const name = match[2]?.toLowerCase() ?? 'highlights';
  const view = workspaceViews.find((candidate) => candidate === name);
  return view ? { projectId: match[1].toLowerCase(), view } : null;
}
