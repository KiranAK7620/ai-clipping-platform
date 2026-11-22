import CloudUploadOutlined from '@mui/icons-material/CloudUploadOutlined';
import {
  Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle,
  LinearProgress, MenuItem, Stack, TextField, Typography,
} from '@mui/material';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useRef, useState, type FormEvent } from 'react';
import { projectsKey, uploadProject, useProjects, type UploadRequest } from '../../services/projects';
import { categories, type Category } from './categories';
import { formatBytes } from './projectFormatting';

interface NewProjectDialogProps {
  category?: Category;
  onClose: () => void;
  onUploaded: () => void;
}

export default function NewProjectDialog({ category, onClose, onUploaded }: NewProjectDialogProps) {
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
    : maxUploadBytes && file.size > maxUploadBytes ? `Choose a file up to ${formatBytes(maxUploadBytes)}.`
    : null
  );

  const upload = useMutation({
    mutationFn: (project: UploadRequest) =>
      uploadProject(project, setProgress, () => setCheckingMetadata(true)),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: projectsKey });
      onUploaded();
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

  return (
    <Dialog open onClose={isUploading ? undefined : onClose} disableEscapeKeyDown={isUploading}
      fullWidth maxWidth="xs" aria-labelledby="new-project-title">
      <form onSubmit={submit}>
        <DialogTitle id="new-project-title">New project</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField autoFocus required label="Project name" fullWidth disabled={isUploading} value={name}
              slotProps={{ htmlInput: { maxLength: 160 } }}
              onChange={(event) => {
                setName(event.target.value);
                resetAttempt();
              }} />
            <TextField required select label="Content category" fullWidth disabled={isUploading}
              value={selectedCategory} onChange={(event) => {
                setSelectedCategory(event.target.value as Category | '');
                resetAttempt();
              }}>
              {categories.map(({ name }) => <MenuItem key={name} value={name}>{name}</MenuItem>)}
            </TextField>

            <div className="video-picker">
              <label htmlFor="project-video">Source video</label>
              <input type="file" id="project-video" accept=".mp4,.mov,.mkv,.webm" required
                aria-describedby="project-formats" disabled={isUploading} onChange={(event) => {
                  setFile(event.target.files?.[0] ?? null);
                  resetAttempt();
                }} />
              <Typography variant="body2" color="text.secondary" id="project-formats">
                MP4, MOV, MKV or WebM{maxUploadBytes ? ` · Up to ${formatBytes(maxUploadBytes)}` : ''}
              </Typography>
            </div>

            {isUploading && (
              <Stack spacing={1}>
                <Typography variant="body2" role="status">
                  {checkingMetadata ? 'Checking video metadata…'
                    : progress === null ? 'Uploading video…' : `Uploading video · ${progress}%`}
                </Typography>
                <LinearProgress aria-label={checkingMetadata ? 'Checking video metadata' : 'Video upload progress'}
                  variant={!checkingMetadata && progress !== null ? 'determinate' : 'indeterminate'}
                  value={progress ?? 0} />
              </Stack>
            )}
            {(fileError || upload.error) && <Alert severity="error">{fileError || upload.error?.message}</Alert>}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button disabled={isUploading} onClick={onClose}>Cancel</Button>
          <Button type="submit" variant="contained" startIcon={<CloudUploadOutlined />}
            disabled={isUploading || !!fileError || !file || !name.trim() || !selectedCategory}>
            {upload.isError ? 'Retry upload' : 'Upload video'}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}
