import ArrowBackRounded from '@mui/icons-material/ArrowBackRounded';
import CloudUploadOutlined from '@mui/icons-material/CloudUploadOutlined';
import {
  Alert, Button, LinearProgress, Stack, TextField, Typography,
} from '@mui/material';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef, useState, type FormEvent } from 'react';
import { projectKey, projectsKey, uploadProject, useProjects, type Project, type UploadRequest } from '../../services/projects';
import { categories, type Category } from './categories';
import { formatBytes } from './projectFormatting';
import '../workspace/workspace.css';

interface Props {
  category?: Category;
  onUploaded: (project: Project) => void;
}

export default function NewProjectPage({ category, onUploaded }: Props) {
  useEffect(() => { window.scrollTo(0, 0); }, []);
  const [dragging, setDragging] = useState(false);
  const [name, setName] = useState('');
  const [selectedCategory, setSelectedCategory] = useState<Category | ''>(category ?? '');
  const [file, setFile] = useState<File | null>(null);
  const [progress, setProgress] = useState<number | null>(null);
  const [checkingMetadata, setCheckingMetadata] = useState(false);
  const uploadId = useRef<string | null>(null);
  const queryClient = useQueryClient();
  const maxUploadBytes = useProjects().data?.pages[0]?.maxUploadBytes;
  const fileError = file && (
    file.size === 0 ? 'Choose a video file with content.'
    : !/\.(mp4|mov|mkv|webm)$/i.test(file.name) ? 'Choose an MP4, MOV, MKV or WebM video.'
    : maxUploadBytes && file.size > maxUploadBytes ? `Choose a file up to ${formatBytes(maxUploadBytes)}.`
    : null
  );

  const upload = useMutation({
    mutationFn: (project: UploadRequest) =>
      uploadProject(project, setProgress, () => setCheckingMetadata(true)),
    onSuccess: async (project) => {
      queryClient.setQueryData(projectKey(project.id), project);
      await queryClient.invalidateQueries({ queryKey: projectsKey });
      onUploaded(project);
    },
  });
  const isUploading = upload.isPending;

  function resetAttempt() {
    uploadId.current = null;
    upload.reset();
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!file || fileError || !name.trim() || !selectedCategory || isUploading) return;

    setCheckingMetadata(false);
    setProgress(null);
    uploadId.current ??= crypto.randomUUID();
    upload.mutate({
      projectId: uploadId.current,
      name: name.trim(),
      category: selectedCategory,
      file,
    });
  }

  function chooseFile(next: File | null) {
    setFile(next);
    resetAttempt();
  }

  return (
    <section className="edit-upload">
      <header className="edit-workspace__header">
        <a className="edit-back" href="#projects"><ArrowBackRounded fontSize="small" /> Projects</a>
        <Typography component="h1" variant="h1">New project</Typography>
      </header>
      <form onSubmit={submit} className="edit-upload__form">
        <TextField required label="Project name" fullWidth disabled={isUploading} value={name}
          slotProps={{ htmlInput: { maxLength: 160 } }}
          onChange={(event) => { setName(event.target.value); resetAttempt(); }} />
        <fieldset className="edit-fieldset" disabled={isUploading}>
          <legend>Content category</legend>
          <div className="edit-language">
            {categories.map(({ name: categoryValue }) => (
              <label className="edit-choice" key={categoryValue}>
                <input type="radio" name="content-category" checked={selectedCategory === categoryValue}
                  onChange={() => { setSelectedCategory(categoryValue); resetAttempt(); }} />
                <span>{categoryValue}</span>
              </label>
            ))}
          </div>
        </fieldset>
        <div className="edit-drop" data-dragging={dragging}
          onDragOver={(event) => { event.preventDefault(); if (!isUploading) setDragging(true); }}
          onDragLeave={() => setDragging(false)}
          onDrop={(event) => {
            event.preventDefault();
            setDragging(false);
            if (!isUploading) chooseFile(event.dataTransfer.files[0] ?? null);
          }}>
          <input type="file" id="project-video" accept=".mp4,.mov,.mkv,.webm"
            aria-label="Choose source video" aria-describedby="project-formats" disabled={isUploading}
            onChange={(event) => chooseFile(event.target.files?.[0] ?? null)} />
          <CloudUploadOutlined />
          <strong dir="auto">{file?.name ?? 'Drop a source video here or browse files'}</strong>
          <Typography variant="body2" color="text.secondary" id="project-formats">
            {file ? formatBytes(file.size) : `MP4, MOV, MKV or WebM${maxUploadBytes ? ` · Up to ${formatBytes(maxUploadBytes)}` : ''}`}
          </Typography>
        </div>
        {isUploading && (
          <Stack spacing={1}>
            <Typography variant="body2" role="status">
              {checkingMetadata ? 'Checking video metadata…'
                : progress === null ? 'Uploading video…' : `Uploading video · ${progress}%`}
            </Typography>
            <LinearProgress aria-label={checkingMetadata ? 'Checking video metadata' : 'Video upload progress'}
              variant={!checkingMetadata && progress !== null ? 'determinate' : 'indeterminate'} value={progress ?? 0} />
          </Stack>
        )}
        {(fileError || upload.error) && <Alert severity="error">{fileError || upload.error?.message}</Alert>}
        <Button type="submit" variant="contained" startIcon={<CloudUploadOutlined />}
          disabled={isUploading || !!fileError || !file || !name.trim() || !selectedCategory}>
          {upload.isError ? 'Retry upload' : 'Upload video'}
        </Button>
      </form>
    </section>
  );
}
