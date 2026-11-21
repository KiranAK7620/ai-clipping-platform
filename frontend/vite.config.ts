import react from '@vitejs/plugin-react';
import { defineConfig, loadEnv } from 'vite';

export default defineConfig(({ mode }) => {
  const { BACKEND_URL = 'http://127.0.0.1:8080' } = loadEnv(mode, process.cwd(), 'BACKEND_');
  const proxy = { '/api': BACKEND_URL };

  return {
    plugins: [react()],
    server: { host: '127.0.0.1', port: 5173, strictPort: true, proxy },
    preview: { host: '127.0.0.1', proxy },
  };
});
