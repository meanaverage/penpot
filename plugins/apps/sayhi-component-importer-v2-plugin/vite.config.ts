/// <reference types="vitest/config" />
import { defineConfig } from 'vite';

export default defineConfig({
  root: import.meta.dirname,
  base: './',
  server: {
    port: 8466,
    host: '0.0.0.0',
  },
  preview: {
    port: 8466,
    host: '0.0.0.0',
  },
  resolve: {
    tsconfigPaths: true,
  },
  build: {
    outDir: '../../dist/apps/sayhi-component-importer-v2-plugin',
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
      reportsDirectory:
        '../../coverage/apps/sayhi-component-importer-v2-plugin',
      provider: 'v8',
    },
  },
});
