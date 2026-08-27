/// <reference types="vitest/config" />
import { defineConfig } from 'vite';

export default defineConfig({
  root: import.meta.dirname,
  // The built plugin is mounted below Penpot's /plugins/ path in production.
  // Relative asset URLs keep the same bundle usable at any plugin host path.
  base: './',
  server: {
    port: 8465,
    host: '0.0.0.0',
  },
  preview: {
    port: 8465,
    host: '0.0.0.0',
  },
  resolve: {
    tsconfigPaths: true,
  },
  build: {
    outDir: '../../dist/apps/sayhi-component-importer-plugin',
    reportCompressedSize: true,
    rollupOptions: {
      input: {
        plugin: 'src/plugin.ts',
        index: 'index.html',
      },
      output: {
        entryFileNames: '[name].js',
      },
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    include: ['src/**/*.spec.ts'],
    reporters: ['default'],
    coverage: {
      reportsDirectory: '../../coverage/apps/sayhi-component-importer-plugin',
      provider: 'v8',
    },
  },
});
