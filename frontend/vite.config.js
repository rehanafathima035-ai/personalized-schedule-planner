import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { VitePWA } from 'vite-plugin-pwa';

export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      manifest: {
        name: 'Personalized Life Planner',
        short_name: 'Life Planner',
        description:
          'A personalized planner for habits, tasks, goals, prayers, and schedules.',
        theme_color: '#111827',
        background_color: '#ffffff',
        display: 'standalone',
        start_url: '/',
        scope: '/',
        icons: [
          {
            src: '/pwa-192x192.png',
            sizes: '192x192',
            type: 'image/png',
          },
          {
            src: '/pwa-512x512.png',
            sizes: '512x512',
            type: 'image/png',
          },
        ],
      },
    }),
  ],

  server: {
    port: 5173,

    proxy: {
      '/api': {
        target:
          process.env.VITE_API_BASE ||
          'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
});