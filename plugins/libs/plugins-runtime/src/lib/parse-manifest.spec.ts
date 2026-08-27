import { afterEach, describe, expect, it, vi } from 'vitest';
import type { Manifest } from './models/manifest.model.js';
import { loadManifest, loadManifestCode } from './parse-manifest.js';

describe('plugin manifest loading', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('does not reuse a cached installed manifest', async () => {
    const manifest = {
      pluginId: 'sayhi-importer',
      name: 'SayHi importer',
      host: 'https://example.com/plugins/sayhi-importer/',
      code: 'plugin.js',
      permissions: ['content:read'],
    } satisfies Manifest;
    const fetch = vi.fn().mockResolvedValue({
      json: vi.fn().mockResolvedValue(manifest),
    });
    vi.stubGlobal('fetch', fetch);

    await expect(
      loadManifest('https://example.com/plugins/sayhi-importer/manifest.json'),
    ).resolves.toEqual(manifest);
    expect(fetch).toHaveBeenCalledWith(
      'https://example.com/plugins/sayhi-importer/manifest.json',
      { cache: 'no-store' },
    );
  });

  it('does not reuse cached plugin code', async () => {
    const manifest = {
      pluginId: 'sayhi-importer',
      name: 'SayHi importer',
      host: 'https://example.com/plugins/sayhi-importer/',
      code: 'plugin.js',
      permissions: ['content:read'],
    } satisfies Manifest;
    const fetch = vi.fn().mockResolvedValue({
      ok: true,
      text: vi.fn().mockResolvedValue('current plugin code'),
    });
    vi.stubGlobal('fetch', fetch);

    await expect(loadManifestCode(manifest)).resolves.toBe(
      'current plugin code',
    );
    expect(fetch).toHaveBeenCalledWith(
      new URL('https://example.com/plugins/sayhi-importer/plugin.js'),
      { cache: 'no-store' },
    );
  });
});
