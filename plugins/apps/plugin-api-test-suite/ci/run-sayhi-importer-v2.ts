import assert from 'node:assert/strict';
import { spawn, type ChildProcess } from 'node:child_process';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium, type Locator, type Page } from 'playwright';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(here, '../../../../');
const frontendDir = resolve(repoRoot, 'frontend');
const e2eDataDir = resolve(frontendDir, 'playwright/data');
const importerDir = resolve(
  repoRoot,
  'plugins/apps/sayhi-component-importer-v2-plugin',
);
const importerBuildDir = resolve(
  repoRoot,
  'plugins/dist/apps/sayhi-component-importer-v2-plugin',
);
const previewServer = resolve(
  repoRoot,
  'plugins/apps/sayhi-component-importer-plugin/preview-server.mjs',
);

const penpotBaseUrl = process.env['PENPOT_BASE_URL'] ?? 'http://127.0.0.1:9007';
const importerPort = Number(process.env['SAYHI_IMPORTER_V2_PORT'] ?? '4192');
const externalImporterHost = process.env['SAYHI_IMPORTER_V2_HOST'];
const importerHost =
  externalImporterHost ?? `http://127.0.0.1:${importerPort}/`;

const teamId = 'c7ce0794-0992-8105-8004-38e630f7920a';
const fileId = 'c7ce0794-0992-8105-8004-38f280443849';
const pageId = '66697432-c33d-8055-8006-2c62cc084cad';

const rpcFixtures: Record<string, string> = {
  'get-profile': 'logged-in-user/get-profile-logged-in.json',
  'get-teams': 'get-teams.json',
  'get-team?id=*': 'workspace/get-team-default.json',
  'get-team-members?team-id=*':
    'logged-in-user/get-team-members-your-penpot.json',
  'get-team-users?file-id=*': 'logged-in-user/get-team-users-single-user.json',
  'get-project?id=*': 'workspace/get-project-default.json',
  'get-enabled-flags': 'workspace/update-profile-empty.json',
  'get-comment-threads?file-id=*': 'workspace/get-comment-threads-empty.json',
  'get-profiles-for-file-comments?file-id=*':
    'workspace/get-profile-for-file-comments.json',
  'get-file-object-thumbnails?file-id=*':
    'workspace/get-file-object-thumbnails-blank.json',
  'get-font-variants?team-id=*': 'dashboard/get-font-variants.json',
  'get-file-fragment?file-id=*': 'workspace/get-file-fragment-blank.json',
  'get-file-libraries?file-id=*': 'workspace/get-file-libraries-empty.json',
  'update-profile-props': 'workspace/update-profile-empty.json',
  'delete-file-object-thumbnails': 'workspace/update-profile-empty.json',
};

async function waitForServer(url: string, timeoutMs = 30_000): Promise<void> {
  const started = Date.now();
  for (;;) {
    try {
      if ((await fetch(url)).ok) return;
    } catch {
      // The preview process is still starting.
    }
    if (Date.now() - started > timeoutMs) {
      throw new Error(`Timed out waiting for ${url}`);
    }
    await new Promise((resolvePromise) => setTimeout(resolvePromise, 100));
  }
}

function startImporterPreview(): ChildProcess {
  return spawn(
    'node',
    [previewServer, '--root', importerBuildDir, '--port', String(importerPort)],
    { cwd: importerDir, stdio: 'inherit' },
  );
}

async function installBackendMocks(page: Page): Promise<void> {
  await page.addInitScript(() => {
    const messages: unknown[] = [];
    Object.defineProperty(globalThis, '__sayhiMotionMessages', {
      value: messages,
      configurable: false,
      enumerable: false,
      writable: false,
    });
    globalThis.addEventListener('message', (event) => {
      const value = event.data;
      if (
        value &&
        typeof value === 'object' &&
        (value as { schema?: string }).schema === 'io.sayhi.penpot.motion-host'
      ) {
        messages.push(value);
      }
    });
  });
  await page.addInitScript({
    path: resolve(frontendDir, 'playwright/scripts/MockWebSocket.js'),
  });
  await page.route('**/js/config.js*', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/javascript',
      body: [
        'var penpotFlags = "disable-onboarding";',
        'var penpotSayHiStudioURI = "http://127.0.0.1:4184";',
        'var penpotSayHiMotionStudioMode = "native-v2";',
        'var penpotSayHiMotionStudioURI = "http://127.0.0.1:4187";',
        'var penpotSayHiMotionPreviewSurface = "canvas";',
        'var penpotSayHiWebMaterializerMode = "portable-v2";',
      ].join('\n'),
    }),
  );
  for (const [rpc, fixture] of Object.entries(rpcFixtures)) {
    await page.route(`**/api/main/methods/${rpc}`, (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/transit+json',
        path: resolve(e2eDataDir, fixture),
      }),
    );
  }
  await page.route(/\/api\/main\/methods\/get-file\?/, (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/transit+json',
      path: resolve(here, 'fixtures/get-file.json'),
    }),
  );
  await page.route(/\/api\/main\/methods\/update-file\b/, (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/transit+json',
      body: JSON.stringify({ '~:revn': 1, '~:lagged': [] }),
    }),
  );
}

async function importerFrame(page: Page) {
  const started = Date.now();
  for (;;) {
    const frame = page
      .frames()
      .find((candidate) => candidate.url().startsWith(importerHost));
    if (frame) return frame;
    if (Date.now() - started > 30_000) {
      throw new Error('Timed out waiting for the V2 importer iframe.');
    }
    await new Promise((resolvePromise) => setTimeout(resolvePromise, 100));
  }
}

async function motionHostFrame(page: Page) {
  const started = Date.now();
  for (;;) {
    const frame = page
      .frames()
      .find((candidate) =>
        candidate
          .url()
          .startsWith('http://127.0.0.1:4187/studio/penpot-motion-host/'),
      );
    if (frame) return frame;
    if (Date.now() - started > 30_000) {
      throw new Error('Timed out waiting for the native Motion Studio iframe.');
    }
    await new Promise((resolvePromise) => setTimeout(resolvePromise, 100));
  }
}

interface WebArtifactMetric {
  text: string;
  fontSize: number;
  lineHeight: number;
  y: number;
  height: number;
}

function assertWebArtifactMetrics(
  metrics: WebArtifactMetric[],
  label: string,
): void {
  const byText = new Map(metrics.map((metric) => [metric.text, metric]));
  const title = byText.get('Verify it’s you');
  const subtitle = byText.get('Choose how you’d like to verify.');
  const passkey = byText.get('Passkey');
  const hint = byText.get(
    'Tab or use ← → to move between methods. Enter to continue.',
  );
  assert.ok(
    title && subtitle && passkey && hint,
    `${label} lost required copy.`,
  );
  for (const metric of metrics) {
    assert.ok(
      metric.lineHeight <= Math.max(metric.fontSize * 2, 64),
      `${label} produced an implausible ${metric.lineHeight}px line height for ${metric.text}.`,
    );
  }
  assert.ok(
    title.y < subtitle.y,
    `${label} placed the title below its subtitle.`,
  );
  assert.ok(
    subtitle.y + subtitle.height < passkey.y,
    `${label} placed method content over the heading copy.`,
  );
  assert.ok(
    passkey.y + passkey.height < hint.y,
    `${label} placed the active method label over the hint.`,
  );
}

async function assertWebArtifactIsCoherent(
  document: Page,
  label: string,
): Promise<void> {
  const metrics = await document.evaluate(() =>
    [...globalThis.document.querySelectorAll('span')].map((node) => {
      const style = getComputedStyle(node);
      const rect = node.getBoundingClientRect();
      return {
        text: node.textContent?.trim() ?? '',
        fontSize: Number.parseFloat(style.fontSize),
        lineHeight: Number.parseFloat(style.lineHeight),
        y: rect.y,
        height: rect.height,
      };
    }),
  );
  assertWebArtifactMetrics(metrics, label);
}

async function assertRuntimeFrameIsCoherent(
  frame: Locator,
  label: string,
): Promise<void> {
  const metrics = await frame.evaluate((node) => {
    const document = (node as HTMLIFrameElement).contentDocument;
    if (!document) return [];
    return [...document.querySelectorAll('span')].map((span) => {
      const style = getComputedStyle(span);
      const rect = span.getBoundingClientRect();
      return {
        text: span.textContent?.trim() ?? '',
        fontSize: Number.parseFloat(style.fontSize),
        lineHeight: Number.parseFloat(style.lineHeight),
        y: rect.y,
        height: rect.height,
      };
    });
  });
  assertWebArtifactMetrics(metrics, label);
}

async function main(): Promise<void> {
  const preview = externalImporterHost ? undefined : startImporterPreview();
  let browser: Awaited<ReturnType<typeof chromium.launch>> | undefined;
  try {
    await waitForServer(importerHost);
    await waitForServer(penpotBaseUrl);
    browser = await chromium.launch({ args: ['--ignore-certificate-errors'] });
    const context = await browser.newContext({
      acceptDownloads: true,
      ignoreHTTPSErrors: true,
    });
    const page = await context.newPage();
    const pluginValidationErrors: string[] = [];
    const consoleMessages: Array<{ type: string; text: string }> = [];
    const consoleErrors: string[] = [];
    const failedResponses: Array<{ status: number; url: string }> = [];
    const pageErrors: string[] = [];
    page.on('console', (message) => {
      const text = message.text();
      consoleMessages.push({ type: message.type(), text });
      if (text.includes('[PENPOT PLUGIN] Value not valid')) {
        pluginValidationErrors.push(text);
      }
      if (
        message.type() === 'error' &&
        !text.startsWith('Failed to load resource:')
      ) {
        consoleErrors.push(text);
      }
    });
    page.on('response', (response) => {
      if (response.status() >= 400) {
        failedResponses.push({
          status: response.status(),
          url: response.url(),
        });
      }
    });
    page.on('pageerror', (error) => pageErrors.push(error.message));
    const assertNoGenericError = async (phase: string) => {
      const count = await page
        .getByText('Something wrong has happened.', { exact: true })
        .count();
      assert.equal(
        count,
        0,
        `Penpot surfaced its generic error notification ${phase}. HTTP failures: ${JSON.stringify(failedResponses)}. Console tail: ${JSON.stringify(consoleMessages.slice(-20))}`,
      );
    };

    await installBackendMocks(page);
    await page.goto(
      `${penpotBaseUrl}/#/workspace?team-id=${teamId}&file-id=${fileId}&page-id=${pageId}`,
    );
    await page.waitForSelector('[data-testid="viewport"]');
    await assertNoGenericError('while loading the workspace');
    await page.waitForFunction(
      () =>
        typeof (globalThis as unknown as { ɵloadPlugin?: unknown })
          .ɵloadPlugin === 'function',
    );
    await page.evaluate(
      ({ host }) =>
        (
          globalThis as unknown as {
            ɵloadPlugin: (manifest: unknown) => Promise<void>;
          }
        ).ɵloadPlugin({
          // The mocked Penpot file grants permissions to this deterministic
          // test plugin ID. Each gate runs in its own browser context, so V1
          // and V2 can safely exercise the same permission fixture.
          pluginId: '00000000-0000-0000-0000-000000000000',
          name: 'SayHi component importer V2 browser gate',
          description: 'Browser conformance gate for projection importer V2',
          host,
          code: 'plugin.js',
          version: 2,
          permissions: [
            'content:read',
            'content:write',
            'library:read',
            'library:write',
            'allow:downloads',
          ],
        }),
      { host: importerHost },
    );

    const frame = await importerFrame(page);
    const status = frame.locator('#status');
    await frame
      .getByRole('button', { name: /Import Verify through V2/i })
      .click();
    await frame.waitForFunction(() => {
      const node = document.querySelector<HTMLElement>('#status');
      return (
        node?.dataset['kind'] === 'success' || node?.dataset['kind'] === 'error'
      );
    });
    const firstStatus = (await status.textContent())?.trim() ?? '';
    assert.equal(
      await status.getAttribute('data-kind'),
      'success',
      firstStatus,
    );
    assert.match(firstStatus, /Imported 29 nodes/);
    assert.match(firstStatus, /3 live bindings/);
    await assertNoGenericError('after the first V2 import');

    await frame
      .getByRole('button', { name: /Import Verify through V2/i })
      .click();
    await frame.waitForFunction(() =>
      document
        .querySelector('#status')
        ?.textContent?.includes('Imported 0 nodes'),
    );
    const secondStatus = (await status.textContent())?.trim() ?? '';
    assert.match(secondStatus, /Imported 0 nodes/);
    assert.match(secondStatus, /3 live bindings/);
    await assertNoGenericError('after the idempotent V2 re-import');

    assert.deepEqual(pluginValidationErrors, []);
    assert.deepEqual(pageErrors, []);
    await frame.getByRole('button', { name: 'Close', exact: true }).click();
    const motionButton = page.getByTestId('sayhi-motion-studio-btn');
    await motionButton.waitFor({ state: 'visible' });
    assert.equal(
      await motionButton.getAttribute('disabled'),
      null,
      'Motion Studio must recognize the V2 component contract.',
    );

    await motionButton.click();
    const motionDock = page.getByTestId('sayhi-motion-studio-dock');
    await motionDock.waitFor({ state: 'visible' });
    const runtimeFrameElement = page.locator(
      'iframe[title="Live component motion preview"]',
    );
    await runtimeFrameElement.waitFor({ state: 'visible' });
    const motionFrame = await motionHostFrame(page);
    await motionFrame
      .locator('.motion-host__track-title', {
        hasText: 'Coverflow transition',
      })
      .waitFor();
    await page.waitForTimeout(750);
    await motionDock.evaluate((node) => {
      node.style.visibility = 'hidden';
    });
    await runtimeFrameElement.screenshot({
      path: '/tmp/sayhi-component-importer-v2-runtime.png',
    });
    const runtimeSource = await runtimeFrameElement.getAttribute('srcdoc');
    assert.ok(
      runtimeSource,
      'The Motion Studio runtime must mount a web artifact.',
    );
    await assertRuntimeFrameIsCoherent(
      runtimeFrameElement,
      'Motion Studio runtime',
    );

    const artifactPage = await context.newPage();
    await artifactPage.setViewportSize({ width: 620, height: 560 });
    await artifactPage.setContent(runtimeSource, { waitUntil: 'load' });
    await assertWebArtifactIsCoherent(artifactPage, 'Portable web artifact');
    await artifactPage.screenshot({
      path: '/tmp/sayhi-component-importer-v2-artifact.png',
      fullPage: true,
    });
    await artifactPage.close();
    await motionDock.evaluate((node) => {
      node.style.visibility = '';
    });
    await assertNoGenericError('after opening Motion Studio');
    assert.deepEqual(
      await motionFrame.locator('.motion-host__track-title').allTextContents(),
      ['Coverflow transition', 'Icons respond', 'Pagination response'],
    );
    assert.match(
      (await motionFrame
        .locator('.motion-host__track-value')
        .first()
        .textContent()) ?? '',
      /^0\.00–0\.42s$/,
      'The coverflow DTCG duration must resolve before the timeline renders.',
    );
    assert.doesNotMatch(
      (await motionFrame.locator('body').textContent()) ?? '',
      /no playable motion tracks|waiting for a penpot motion document/i,
    );

    const durationHandle = motionFrame.getByRole('button', {
      name: 'Adjust Coverflow transition duration',
    });
    const durationHandleBox = await durationHandle.boundingBox();
    assert.ok(
      durationHandleBox,
      'The coverflow duration handle must be visible.',
    );
    await page.mouse.move(
      durationHandleBox.x + durationHandleBox.width / 2,
      durationHandleBox.y + durationHandleBox.height / 2,
    );
    await page.mouse.down();
    await page.mouse.move(
      durationHandleBox.x + durationHandleBox.width / 2 + 72,
      durationHandleBox.y + durationHandleBox.height / 2,
    );
    await page.mouse.up();
    await motionFrame.waitForFunction(() => {
      const field = document.querySelector<HTMLInputElement>(
        '#motionPropertyDuration',
      );
      return Number(field?.value) > 0.42;
    });
    assert.ok(
      Number(
        await motionFrame.locator('#motionPropertyDuration').inputValue(),
      ) > 0.42,
      'Dragging the coverflow end handle must persist a longer duration.',
    );
    await assertNoGenericError('after resizing the coverflow track');

    await motionFrame.locator('#interactionTab').click();
    for (const responseId of ['next', 'next', 'previous', 'previous']) {
      await motionFrame.locator(`[data-response-id="${responseId}"]`).click();
      await page.waitForTimeout(150);
      await assertNoGenericError(`after simulating ${responseId}`);
    }
    assert.match(
      (await motionFrame.locator('#motionHostStatus').textContent()) ?? '',
      /^Loaded /,
      'Repeated semantic triggers must not reset the Motion Studio document.',
    );

    await page.waitForTimeout(500);
    const motionMessages = (await motionFrame.evaluate(
      () =>
        (
          globalThis as unknown as {
            __sayhiMotionMessages?: Array<{
              type?: string;
              payload?: Record<string, unknown>;
            }>;
          }
        ).__sayhiMotionMessages ?? [],
    )) as Array<{
      type?: string;
      payload?: Record<string, unknown>;
    }>;
    const contextMessages = motionMessages.filter(
      ({ type }) => type === 'host.context',
    );
    const documentMessages = motionMessages.filter(
      ({ type }) => type === 'host.motion.document',
    );
    const errorMessages = motionMessages.filter(
      ({ type }) => type === 'host.error',
    );
    assert.ok(
      contextMessages.length >= 1 && contextMessages.length <= 6,
      `Expected a bounded Motion Studio context handshake; saw ${contextMessages.length}.`,
    );
    assert.equal(
      documentMessages.length,
      2,
      'The host must load the initial document and exactly one persisted drag revision without context-driven reloads.',
    );
    assert.deepEqual(errorMessages, []);
    assert.deepEqual(pluginValidationErrors, []);
    assert.deepEqual(consoleErrors, []);
    assert.deepEqual(pageErrors, []);
    await assertNoGenericError('after completing the V2 Motion Studio flow');

    await page.screenshot({
      path: '/tmp/sayhi-component-importer-v2-motion-browser.png',
      fullPage: true,
    });

    await page.getByText('Tokens', { exact: true }).click();
    const labels = [
      ...(await page.getByTestId('tokens-set-group-item').allTextContents()),
      ...(await page.getByTestId('tokens-set-item').allTextContents()),
    ].map((label) => label.trim());
    assert.ok(
      labels.some((label) => label.includes('Projection V2')),
      `Expected independent Projection V2 token sets; saw ${labels.join(', ')}.`,
    );

    await page.screenshot({
      path: '/tmp/sayhi-component-importer-v2-browser.png',
      fullPage: true,
    });
    console.log(`✓ ${firstStatus}`);
    console.log(`✓ ${secondStatus}`);
    console.log(
      '✓ Motion Studio recognizes the V2 import and survives repeated triggers.',
    );
    console.log(
      '✓ Portable artifact and Motion Studio runtime preserve coherent typography and layout.',
    );
    console.log('✓ Projection V2 token sets are independently visible.');
    console.log('✓ No Penpot plugin validation or page errors were observed.');
  } finally {
    await browser?.close();
    preview?.kill();
  }
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
