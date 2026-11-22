import BookmarkAddOutlined from '@mui/icons-material/BookmarkAddOutlined';
import EditOutlined from '@mui/icons-material/EditOutlined';
import SaveOutlined from '@mui/icons-material/SaveOutlined';
import { Alert, Button, IconButton, LinearProgress, TextField } from '@mui/material';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import {
  captionThemesKey, fetchCaptionThemes, saveCaptionTheme, updateCaptionTheme, type CaptionTheme,
} from '../../services/captionThemes';
import { captionFontNames, type CaptionSettings } from '../../services/clips';

interface Props {
  settings: CaptionSettings;
  disabled: boolean;
  onApply: (captions: CaptionSettings) => void;
}

export default function CaptionThemes({ settings, disabled, onApply }: Props) {
  const [name, setName] = useState('');
  const [editing, setEditing] = useState<{ theme: CaptionTheme; previous: CaptionSettings } | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const client = useQueryClient();
  const library = useQuery({
    queryKey: captionThemesKey,
    queryFn: ({ signal }) => fetchCaptionThemes(signal),
    staleTime: 20_000,
    retry: 1,
  });
  const save = useMutation({
    mutationFn: ({ id, themeName, captions }: { id?: string; themeName: string; captions: CaptionSettings }) =>
      id ? updateCaptionTheme(id, themeName, captions) : saveCaptionTheme(themeName, captions),
    onSuccess: async (theme) => {
      setEditing(null);
      setName('');
      setMessage(`Saved “${theme.name}”.`);
      await client.invalidateQueries({ queryKey: captionThemesKey });
    },
  });

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const themeName = name.trim();
    if (!themeName || disabled || save.isPending) return;
    setMessage(null);
    save.mutate({ id: editing?.theme.id, themeName, captions: settings });
  }

  function editTheme(theme: CaptionTheme) {
    setEditing({ theme, previous: settings });
    setName(theme.name);
    setMessage(null);
    save.reset();
    onApply(theme.captions);
  }

  function cancelEditing() {
    if (!editing) return;
    onApply(editing.previous);
    setEditing(null);
    setName('');
    setMessage(null);
    save.reset();
  }

  return (
    <details className="edit-exact caption-themes">
      <summary>Caption themes</summary>
      <div className="caption-themes__content">
        <form onSubmit={submit} className="caption-themes__form">
          {editing && (
            <p className="caption-themes__editing">
              Editing <strong dir="auto">{editing.theme.name}</strong>. Adjust the caption controls above, then save changes.
            </p>
          )}
          <TextField label="Theme name" size="small" value={name}
            disabled={disabled || save.isPending} slotProps={{ htmlInput: { maxLength: 60 } }}
            onChange={(event) => {
              setName(event.target.value);
              if (save.isError) save.reset();
            }} />
          <div className="caption-themes__actions">
            <Button type="submit" size="small" startIcon={editing ? <SaveOutlined /> : <BookmarkAddOutlined />}
              disabled={disabled || save.isPending || !name.trim()}>
              {editing ? 'Save changes' : 'Save theme'}
            </Button>
            {editing && (
              <Button size="small" color="inherit" disabled={disabled || save.isPending} onClick={cancelEditing}>
                Cancel editing
              </Button>
            )}
          </div>
        </form>
        {message && <span className="caption-themes__status" role="status">{message}</span>}
        {save.error && <Alert severity="error">{save.error.message}</Alert>}
        {library.isPending && <LinearProgress aria-label="Loading caption themes" />}
        {library.isError && (
          <Alert severity="error" action={
            <Button color="inherit" size="small" onClick={() => void library.refetch()}>Retry</Button>
          }>{library.error.message}</Alert>
        )}
        {library.isSuccess && library.data.length === 0 && (
          <span className="caption-themes__empty">Saved themes are available in every project.</span>
        )}
        {!!library.data?.length && (
          <ul className="caption-themes__list">
            {library.data.map((theme) => (
              <li key={theme.id}>
                <button type="button" className="caption-themes__apply" aria-label={`Apply ${theme.name}`}
                  disabled={disabled || save.isPending || editing !== null}
                  onClick={() => {
                    onApply(theme.captions);
                    setMessage(`Applied “${theme.name}”.`);
                    save.reset();
                  }}>
                  <span className="caption-themes__swatch" aria-hidden="true"
                    style={{ color: theme.captions.textColor, backgroundColor: theme.captions.backgroundColor }}>Aa</span>
                  <span>
                    <strong dir="auto">{theme.name}</strong>
                    <small>{theme.captions.enabled ? `${captionFontNames[theme.captions.fontFamily]} · ${theme.captions.fontSize}px` : 'Captions off'}</small>
                  </span>
                </button>
                <IconButton size="small" aria-label={`Edit theme ${theme.name}`} title="Edit theme"
                  disabled={disabled || save.isPending || editing !== null}
                  onClick={() => editTheme(theme)}>
                  <EditOutlined fontSize="small" />
                </IconButton>
              </li>
            ))}
          </ul>
        )}
      </div>
    </details>
  );
}
