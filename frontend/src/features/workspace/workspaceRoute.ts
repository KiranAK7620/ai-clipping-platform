export const workspaceViews = ['highlights', 'transcript', 'exports'] as const;
export type WorkspaceView = typeof workspaceViews[number];

export function workspaceHref(projectId: string, view: WorkspaceView = 'highlights') {
  return `#project/${projectId}/${view}`;
}

export function workspaceRoute(section: string) {
  const match = /^project\/([\da-f]{8}(?:-[\da-f]{4}){3}-[\da-f]{12})(?:\/(highlights|transcript|exports))?$/i.exec(section);
  return match ? { projectId: match[1].toLowerCase(), view: (match[2]?.toLowerCase() ?? 'highlights') as WorkspaceView } : null;
}
