import { useInfiniteQuery } from '@tanstack/react-query';
import type { Category } from '../features/dashboard/categories';

type ProjectCategory = Uppercase<Category>;

export interface Video {
  id: string;
  originalName: string;
  sizeBytes: number;
  durationSeconds: number;
  width: number;
  height: number;
  frameRate: number | null;
  videoCodec: string;
  audioCodec: string | null;
}

export interface Project {
  id: string;
  name: string;
  category: ProjectCategory;
  createdAt: string;
  video: Video | null;
}

interface ProjectPage {
  projects: Project[];
  uploadedVideos: number;
  hasNext: boolean;
  maxUploadBytes: number;
  activeJobs: number;
}

export interface UploadRequest {
  projectId: string;
  name: string;
  category: Category;
  file: File;
}

export const projectsKey = ['projects'] as const;

export function useProjects() {
  return useInfiniteQuery({
    queryKey: projectsKey,
    initialPageParam: 0,
    queryFn: async ({ pageParam, signal }): Promise<ProjectPage> => {
      const response = await fetch(`/api/projects?page=${pageParam}`, { signal });
      if (!response.ok) throw new Error('Projects could not be loaded.');
      return response.json();
    },
    getNextPageParam: (page, _pages, pageNumber) => page.hasNext ? pageNumber + 1 : undefined,
    refetchInterval: ({ state }) => state.data?.pages[0]?.activeJobs ? 4000 : false,
    retry: 1,
  });
}

export function uploadProject(
  project: UploadRequest,
  onProgress: (percentage: number | null) => void,
  onTransferred: () => void,
): Promise<Project> {
  return new Promise((resolve, reject) => {
    const form = new FormData();
    form.set('projectId', project.projectId);
    form.set('name', project.name);
    form.set('category', project.category.toUpperCase());
    form.set('file', project.file);

    const request = new XMLHttpRequest();
    request.upload.onprogress = (event) => {
      onProgress(event.lengthComputable ? Math.round(event.loaded / event.total * 100) : null);
    };
    request.upload.onload = onTransferred;
    request.onerror = () => reject(new Error('The connection was lost. Try uploading again.'));
    request.onload = () => {
      const data = request.response;
      if ((request.status === 200 || request.status === 201) && data?.id && data?.video) {
        resolve(data);
        return;
      }

      const fallback = request.status === 413 ? 'The video exceeds the upload size limit.' : 'The upload failed.';
      reject(new Error(typeof data?.detail === 'string' ? data.detail : fallback));
    };

    request.open('POST', '/api/projects');
    request.responseType = 'json';
    request.send(form);
  });
}
