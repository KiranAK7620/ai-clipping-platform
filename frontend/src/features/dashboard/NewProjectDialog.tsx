import CloudUploadOutlined from '@mui/icons-material/CloudUploadOutlined';
import { Button, Dialog, DialogActions, DialogContent, DialogTitle, MenuItem, TextField, Typography } from '@mui/material';
import { useState } from 'react';
import { categories, type Category } from './categories';

interface NewProjectDialogProps {
  category?: Category;
  onClose: () => void;
}

export function NewProjectDialog({ category, onClose }: NewProjectDialogProps) {
  const [selectedCategory, setSelectedCategory] = useState<Category | ''>(category ?? '');

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="xs" aria-labelledby="new-project-title">
      <DialogTitle id="new-project-title">New project</DialogTitle>
      <DialogContent>
        <TextField autoFocus select label="Content category" fullWidth margin="dense"
          value={selectedCategory} onChange={(event) => setSelectedCategory(event.target.value as Category | '')}>
          <MenuItem value="">Choose a category</MenuItem>
          {categories.map(({ name }) => <MenuItem key={name} value={name}>{name}</MenuItem>)}
        </TextField>
        <div className="upload-placeholder">
          <CloudUploadOutlined />
          <Typography fontWeight={650}>Video upload</Typography>
          <Typography color="text.secondary" variant="body2">Uploads will be available in a future sprint.</Typography>
        </div>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
}
