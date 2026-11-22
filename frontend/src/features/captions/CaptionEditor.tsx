import SubtitlesOutlined from '@mui/icons-material/SubtitlesOutlined';
import { Button, Slider, Switch } from '@mui/material';
import { useState } from 'react';
import { defaultCaptions, type CaptionSettings } from '../../services/clips';
import CaptionThemes from './CaptionThemes';
import './captionEditor.css';

interface Props {
  settings: CaptionSettings;
  disabled: boolean;
  onChange: (settings: CaptionSettings) => void;
}

const fontChoices = [
  { value: 'SANS_SERIF', label: 'Sans' },
  { value: 'SERIF', label: 'Serif' },
  { value: 'MONOSPACE', label: 'Mono' },
] as const;
const positions = [
  { value: 'TOP', label: 'Top' },
  { value: 'MIDDLE', label: 'Middle' },
  { value: 'BOTTOM', label: 'Bottom' },
] as const;
const alignments = [
  { value: 'LEFT', label: 'Left' },
  { value: 'CENTER', label: 'Center' },
  { value: 'RIGHT', label: 'Right' },
] as const;

function ColorSetting({ label, value, disabled, onChange }: {
  label: string;
  value: string;
  disabled: boolean;
  onChange: (value: string) => void;
}) {
  const [hex, setHex] = useState<string | null>(null);
  const valid = hex === null || /^#?[0-9a-f]{6}$/i.test(hex.trim());

  function applyHex() {
    if (hex === null || !valid) return;
    const color = hex.trim().replace(/^#/, '').toLowerCase();
    onChange(`#${color}`);
    setHex(null);
  }

  return (
    <div className="caption-color">
      <span>{label}</span>
      <span className="caption-color__well">
        <input type="color" aria-label={label} value={value} disabled={disabled}
          onInput={(event) => { setHex(null); onChange(event.currentTarget.value); }}
          onChange={(event) => { setHex(null); onChange(event.currentTarget.value); }} />
        <input type="text" aria-label={`${label} hex`} value={hex ?? value.toUpperCase()}
          aria-invalid={!valid} disabled={disabled} spellCheck={false} maxLength={7}
          onChange={(event) => setHex(event.target.value)} onBlur={applyHex}
          onKeyDown={(event) => {
            if (event.key === 'Enter') {
              event.preventDefault();
              event.currentTarget.blur();
            }
            if (event.key === 'Escape') setHex(null);
          }} />
      </span>
    </div>
  );
}

function NumberSetting({ label, value, min, max, unit, disabled, onChange }: {
  label: string;
  value: number;
  min: number;
  max: number;
  unit: string;
  disabled: boolean;
  onChange: (value: number) => void;
}) {
  return (
    <div className="caption-number">
      <div><span>{label}</span><span>{value}{unit}</span></div>
      <Slider size="small" aria-label={label} getAriaValueText={(amount) => `${amount}${unit}`}
        value={value} min={min} max={max} step={1} disabled={disabled} onChange={(_, next) => onChange(Number(next))} />
    </div>
  );
}

function Choices<Value extends string>({ label, name, value, choices, onChange }: {
  label: string;
  name: string;
  value: Value;
  choices: readonly { value: Value; label: string }[];
  onChange: (value: Value) => void;
}) {
  return (
    <fieldset className="caption-options">
      <legend>{label}</legend>
      <div>
        {choices.map((choice) => (
          <label className="edit-choice" key={choice.value}>
            <input name={name} type="radio" checked={value === choice.value} onChange={() => onChange(choice.value)} />
            <span data-font-choice={name === 'caption-font' ? choice.value : undefined}>{choice.label}</span>
          </label>
        ))}
      </div>
    </fieldset>
  );
}

export default function CaptionEditor({ settings, disabled, onChange }: Props) {
  function change<Key extends keyof CaptionSettings>(key: Key, value: CaptionSettings[Key]) {
    onChange({ ...settings, [key]: value });
  }

  return (
    <section className="caption-editor" aria-label="Caption settings">
      <div className="caption-editor__header">
        <h3><SubtitlesOutlined fontSize="small" /> Captions</h3>
        <Switch size="small" checked={settings.enabled} disabled={disabled}
          slotProps={{ input: { 'aria-label': 'Enable captions' } }}
          onChange={(_, checked) => change('enabled', checked)} />
      </div>
      {settings.enabled && (
        <fieldset className="caption-editor__options" disabled={disabled}>
          <legend className="edit-sr-only">Caption appearance</legend>
          <Choices label="Font family" name="caption-font" value={settings.fontFamily} choices={fontChoices}
            onChange={(font) => change('fontFamily', font)} />
          <NumberSetting label="Font size" value={settings.fontSize} min={20} max={72} unit=" px"
            disabled={disabled} onChange={(size) => change('fontSize', size)} />
          <div className="caption-editor__colors">
            <ColorSetting label="Text color" value={settings.textColor} disabled={disabled}
              onChange={(color) => change('textColor', color)} />
            <ColorSetting label="Background color" value={settings.backgroundColor} disabled={disabled}
              onChange={(color) => onChange({
                ...settings, backgroundColor: color, backgroundOpacity: settings.backgroundOpacity || 86,
              })} />
          </div>
          <NumberSetting label="Background opacity" value={settings.backgroundOpacity} min={0} max={100} unit="%"
            disabled={disabled} onChange={(opacity) => change('backgroundOpacity', opacity)} />
          <Choices label="Position" name="caption-position" value={settings.position} choices={positions}
            onChange={(position) => change('position', position)} />
          <details className="edit-exact caption-editor__advanced">
            <summary>More caption options</summary>
            <div className="caption-editor__more">
              <Choices label="Alignment" name="caption-alignment" value={settings.alignment} choices={alignments}
                onChange={(alignment) => change('alignment', alignment)} />
              <label className="caption-editor__bold">
                Bold
                <Switch size="small" checked={settings.bold} disabled={disabled}
                  slotProps={{ input: { 'aria-label': 'Bold captions' } }}
                  onChange={(_, checked) => change('bold', checked)} />
              </label>
              <ColorSetting label="Outline color" value={settings.outlineColor} disabled={disabled}
                onChange={(color) => onChange({
                  ...settings, outlineColor: color, outlineWidth: settings.outlineWidth || 3,
                })} />
              <NumberSetting label="Outline width" value={settings.outlineWidth} min={0} max={8} unit=" px"
                disabled={disabled} onChange={(width) => change('outlineWidth', width)} />
              <Button size="small" disabled={disabled} onClick={() => onChange({ ...defaultCaptions })}>
                Reset caption style
              </Button>
            </div>
          </details>
        </fieldset>
      )}
      <CaptionThemes settings={settings} disabled={disabled} onApply={onChange} />
    </section>
  );
}
