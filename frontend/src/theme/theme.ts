import { createTheme } from '@mui/material/styles';

export const theme = createTheme({
  palette: {
    mode: 'dark',
    primary: { main: '#477BFF' },
    secondary: { main: '#8957FF' },
    background: { default: '#101426', paper: '#202A45' },
    text: { primary: '#F8FAFC', secondary: '#AAB5D0' },
    divider: '#34405F',
  },
  typography: {
    fontFamily: '"Inter Variable", Inter, system-ui, sans-serif',
    h1: { fontSize: 'clamp(1.8rem, 3vw, 2.35rem)', fontWeight: 700, letterSpacing: '-0.055em' },
    h2: { fontSize: '1.1rem', fontWeight: 650, letterSpacing: '-0.025em' },
    button: { textTransform: 'none', fontWeight: 650 },
  },
  shape: { borderRadius: 12 },
  components: {
    MuiButton: { defaultProps: { disableElevation: true } },
    MuiPaper: { defaultProps: { elevation: 0 } },
  },
});
