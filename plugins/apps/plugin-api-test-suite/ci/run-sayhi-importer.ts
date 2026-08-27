import assert from 'node:assert/strict';
import { spawn, type ChildProcess } from 'node:child_process';
import { writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium, type Page } from 'playwright';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(here, '../../../../');
const frontendDir = resolve(repoRoot, 'frontend');
const e2eDataDir = resolve(frontendDir, 'playwright/data');
const importerDir = resolve(
  repoRoot,
  'plugins/apps/sayhi-component-importer-plugin',
);
const importerBuildDir = resolve(
  repoRoot,
  'plugins/dist/apps/sayhi-component-importer-plugin',
);

const penpotBaseUrl = process.env['PENPOT_BASE_URL'] ?? 'http://127.0.0.1:9007';
const importerPort = Number(process.env['SAYHI_IMPORTER_PORT'] ?? '4191');
const externalImporterHost = process.env['SAYHI_IMPORTER_HOST'];
const screenshotPath = process.env['SAYHI_IMPORTER_SCREENSHOT'];
const tokensScreenshotPath = process.env['SAYHI_IMPORTER_TOKENS_SCREENSHOT'];
const projectionSnapshotPath =
  process.env['SAYHI_IMPORTER_PROJECTION_SNAPSHOT'];
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
  'get-comment-threads?file-id=*': 'workspace/get-comment-threads-empty.json',
  'get-profiles-for-file-comments?file-id=*':
    'workspace/get-profile-for-file-comments.json',
  'get-file-object-thumbnails?file-id=*':
    'workspace/get-file-object-thumbnails-blank.json',
  'get-font-variants?team-id=*': 'dashboard/get-font-variants.json',
  'get-file-fragment?file-id=*': 'workspace/get-file-fragment-blank.json',
  'get-file-libraries?file-id=*': 'workspace/get-file-libraries-empty.json',
  'update-profile-props': 'workspace/update-profile-empty.json',
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
    [
      'preview-server.mjs',
      '--root',
      importerBuildDir,
      '--port',
      String(importerPort),
    ],
    { cwd: importerDir, stdio: 'inherit' },
  );
}

async function installWebSocketMock(page: Page): Promise<void> {
  await page.addInitScript({
    path: resolve(frontendDir, 'playwright/scripts/MockWebSocket.js'),
  });
}

async function installBackendMocks(page: Page): Promise<void> {
  await page.route('**/js/config.js*', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/javascript',
      body: [
        'var penpotFlags = "disable-onboarding";',
        'var penpotSayHiStudioURI = "http://127.0.0.1:8463";',
        'var penpotSayHiMotionStudioMode = "native-v2";',
        'var penpotSayHiMotionStudioURI = "http://127.0.0.1:8464";',
        'var penpotSayHiMotionPreviewSurface = "canvas";',
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
      throw new Error('Timed out waiting for the importer iframe.');
    }
    await new Promise((resolvePromise) => setTimeout(resolvePromise, 100));
  }
}

async function streamText(
  stream: NodeJS.ReadableStream | null,
): Promise<string> {
  if (!stream) throw new Error('The DTCG export download had no body.');
  const chunks: Buffer[] = [];
  for await (const chunk of stream) {
    chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk));
  }
  return Buffer.concat(chunks).toString('utf8');
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
    const pageErrors: string[] = [];

    page.on('console', (message) => {
      const text = message.text();
      if (text.includes('[PENPOT PLUGIN] Value not valid')) {
        pluginValidationErrors.push(text);
      }
    });
    page.on('pageerror', (error) => pageErrors.push(error.message));

    await installWebSocketMock(page);
    await installBackendMocks(page);
    await page.goto(
      `${penpotBaseUrl}/#/workspace?team-id=${teamId}&file-id=${fileId}&page-id=${pageId}`,
    );
    await page.waitForSelector('[data-testid="viewport"]');
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
          pluginId: '00000000-0000-0000-0000-000000000000',
          name: 'SayHi component importer browser gate',
          description: 'Browser conformance gate for the SayHi importer',
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
      .getByRole('button', { name: /Import or update SayHi Verify/i })
      .click();
    await status.waitFor({ state: 'visible' });
    await frame.waitForFunction(() => {
      const node = document.querySelector<HTMLElement>('#status');
      return (
        node?.dataset['kind'] === 'success' || node?.dataset['kind'] === 'error'
      );
    });

    const statusKind = await status.getAttribute('data-kind');
    const statusText = (await status.textContent())?.trim() ?? '';
    assert.equal(statusKind, 'success', statusText);
    assert.match(statusText, /79 new tokens/);
    assert.match(statusText, /38 live bindings/);
    assert.match(statusText, /3 preserved-only motion values/);

    // The user's normal recovery path is to run the importer again in a
    // document that already contains the component and its native tokens.
    // Keep this in the hosted gate: the update path uses Token.value setters,
    // while a fresh import primarily exercises TokenSet.addToken().
    await frame
      .getByRole('button', { name: /Import or update SayHi Verify/i })
      .click();
    await frame.waitForFunction(() => {
      const node = document.querySelector<HTMLElement>('#status');
      return (
        node?.dataset['kind'] === 'success' &&
        /^Verified Verify /.test(node.textContent?.trim() ?? '')
      );
    });
    const reimportStatusText = (await status.textContent())?.trim() ?? '';
    assert.match(reimportStatusText, /0 new tokens/);

    const projectionDownloadPromise = page.waitForEvent('download');
    await frame.evaluate(() => {
      parent.postMessage({ type: 'sayhi.export-projection' }, '*');
    });
    const projectionDownload = await projectionDownloadPromise;
    const projectionJson = await streamText(
      await projectionDownload.createReadStream(),
    );
    const projection = JSON.parse(projectionJson) as {
      schemaName: string;
      schemaVersion: string;
      componentId: string;
      componentVersion: string;
      root: ProjectionNode;
    };
    assert.equal(projection.schemaName, 'io.sayhi.penpot-projection-snapshot');
    assert.equal(projection.schemaVersion, '0.1.0');
    assert.deepEqual(projection.root.frame, {
      x: 0,
      y: 0,
      width: 580,
      height: 500,
    });
    const projectionNodes = flattenProjection(projection.root);
    assert.deepEqual(projectionNodes.get('verify.panel.face')?.frame, {
      x: 1,
      y: 1,
      width: 578,
      height: 498,
    });
    assert.deepEqual(projectionNodes.get('verify.method.passkey')?.frame, {
      x: 190,
      y: 145,
      width: 200,
      height: 174,
    });
    if (projectionSnapshotPath) {
      await writeFile(projectionSnapshotPath, projectionJson, 'utf8');
    }

    const downloadPromise = page.waitForEvent('download');
    await frame.getByRole('button', { name: /Export DTCG resolver/i }).click();
    const download = await downloadPromise;
    const exported = JSON.parse(
      await streamText(await download.createReadStream()),
    ) as {
      version: string;
      sets: {
        Foundation: {
          sources: Array<{
            font?: {
              weight?: { medium?: { $value?: unknown } };
            };
          }>;
        };
        Components: {
          sources: Array<{
            $schema?: string;
            verify?: {
              heading?: {
                'font-size'?: { $value?: unknown };
              };
            };
          }>;
        };
      };
    };
    assert.equal(exported.version, '2025.10');
    const primitives = exported.sets.Foundation.sources[0];
    assert.equal(
      primitives?.font?.weight?.medium?.$value,
      560,
      'The native Penpot token must export back to numeric DTCG font weight 560.',
    );
    const verify = exported.sets.Components.sources[0];
    assert.equal(
      verify?.$schema,
      'https://www.designtokens.org/schemas/2025.10/format.json',
      'DTCG exports must identify the official stable 2025.10 machine schema.',
    );
    assert.deepEqual(
      verify?.verify?.heading?.['font-size']?.$value,
      { value: 38, unit: 'px' },
      'Penpot may resolve 38px to coordinate 38, but export must preserve the DTCG dimension and its px unit.',
    );
    assert.deepEqual(pluginValidationErrors, []);
    assert.deepEqual(pageErrors, []);

    await frame.getByRole('button', { name: 'Close', exact: true }).click();
    await page.waitForTimeout(100);

    const motionButton = page.getByTestId('sayhi-motion-studio-btn');
    await motionButton.waitFor({ state: 'visible' });
    assert.equal(
      await motionButton.getAttribute('disabled'),
      null,
      'Motion Studio must be enabled for the component selected by the importer.',
    );

    if (screenshotPath) {
      await page.screenshot({ path: screenshotPath, fullPage: true });
    }

    await page.getByText('Tokens', { exact: true }).click();
    const setTreeLabels = [
      ...(await page.getByTestId('tokens-set-group-item').allTextContents()),
      ...(await page.getByTestId('tokens-set-item').allTextContents()),
    ].map((label) => label.trim());
    for (const label of [
      'Foundation',
      'Semantic',
      'Light',
      'Dark',
      'Components',
    ]) {
      assert.ok(
        setTreeLabels.some((candidate) => candidate.includes(label)),
        `The native Penpot set tree must visibly expose ${label}; saw ${setTreeLabels.join(', ')}.`,
      );
    }
    const activeSetTitle = page.getByTestId('active-token-set-title');
    await activeSetTitle.waitFor({ state: 'visible' });
    assert.match(
      (await activeSetTitle.textContent()) ?? '',
      /Components/,
      'The shared component token set should be the initial token workspace.',
    );
    for (const type of [
      'border-radius',
      'color',
      'font-family',
      'font-size',
      'font-weight',
      'letter-spacing',
      'shadow',
      'sizing',
      'spacing',
      'typography',
    ]) {
      const section = page.getByTestId(`section-${type}`);
      await section.waitFor({ state: 'visible' });
      assert.match(
        (await section.textContent()) ?? '',
        /[1-9]/,
        `Expected a populated ${type} token section.`,
      );
    }
    const fontFamilySection = page.getByTestId('section-font-family');
    await fontFamilySection.locator('button').first().click();
    const familyValue = fontFamilySection.getByTestId('token-value').first();
    await familyValue.waitFor({ state: 'visible' });
    assert.match(
      (await familyValue.textContent()) ?? '',
      /Instrument Sans/,
      'Resolved font-family values should be visible without hovering.',
    );

    const componentWeightSection = page.getByTestId('section-font-weight');
    await componentWeightSection.locator('button').first().click();
    const componentWeightValues = await componentWeightSection
      .getByTestId('token-value')
      .allTextContents();
    assert.deepEqual(
      componentWeightValues.map((value) => value.trim()).sort(),
      ['400', '600', '700'],
      'The component set should visibly expose its regular, medium, and strong weight aliases.',
    );

    const typographySection = page.getByTestId('section-typography');
    await typographySection.locator('button').first().click();
    const typographyValues = await typographySection
      .getByTestId('token-value')
      .allTextContents();
    assert.equal(
      typographyValues.length,
      9,
      'Verify should expose nine composite typography roles in the component set.',
    );
    const typographyTitles: string[] = [];
    for (let index = 0; index < typographyValues.length; index += 1) {
      const tokenValue = typographySection
        .getByTestId('token-value')
        .nth(index);
      const tokenPill = tokenValue.locator('..');
      await tokenPill.hover();
      typographyTitles.push((await tokenPill.getAttribute('title')) ?? '');
    }
    assert.ok(
      typographyTitles.every(
        (title) =>
          title.includes('Font Family') &&
          title.includes('Font Size') &&
          title.includes('Font Weight'),
      ),
      `Every composite typography role should retain its complete family, size, and weight definition; saw ${typographyTitles.join(' | ')}.`,
    );

    if (tokensScreenshotPath) {
      await page.screenshot({ path: tokensScreenshotPath, fullPage: true });
    }

    await page.getByText('Foundation', { exact: true }).click();
    await page.waitForFunction(() =>
      document
        .querySelector('[data-testid="active-token-set-title"]')
        ?.textContent?.includes('Foundation'),
    );
    const fontWeightSection = page.getByTestId('section-font-weight');
    await fontWeightSection.waitFor({ state: 'visible' });
    await fontWeightSection.locator('button').first().click();
    const nativeWeightValues = await fontWeightSection
      .getByTestId('token-value')
      .allTextContents();
    assert.deepEqual(
      nativeWeightValues.map((value) => value.trim()).sort(),
      ['400', '600', '700'],
      'Penpot-native weights should use its supported 100-step scale.',
    );

    await page.goto(`${penpotBaseUrl}/#/dashboard/recent?team-id=${teamId}`);
    await page.waitForTimeout(500);
    assert.equal(
      await page.getByText(/Help us get to know you/i).count(),
      0,
      'SayHi-authenticated users must never receive Penpot onboarding.',
    );
    assert.deepEqual(pageErrors, []);

    console.log(`✓ ${statusText}`);
    console.log(`✓ ${reimportStatusText}`);
    console.log('✓ Actual Penpot projection exported through the plugin API.');
    console.log('✓ DTCG font weight round-tripped as numeric 560.');
    console.log('✓ DTCG dimensions preserve their px unit through Penpot.');
    console.log('✓ Motion Studio is enabled for the imported selection.');
    console.log('✓ Verify tokens expose native categories and inline values.');
    console.log(
      '✓ Component weight aliases and nine typography roles are visible.',
    );
    console.log(
      '✓ Native weights render as 400 / 600 / 700 without invalid tokens.',
    );
    console.log('✓ Penpot onboarding remains disabled on the dashboard.');
    console.log('✓ No Penpot plugin validation or page errors were observed.');
  } finally {
    await browser?.close();
    preview?.kill();
  }
}

interface ProjectionNode {
  sourceId: string;
  frame: { x: number; y: number; width: number; height: number };
  children: ProjectionNode[];
}

function flattenProjection(root: ProjectionNode): Map<string, ProjectionNode> {
  const nodes = new Map<string, ProjectionNode>();
  const queue = [root];
  while (queue.length) {
    const node = queue.shift();
    if (!node) continue;
    if (node.sourceId) nodes.set(node.sourceId, node);
    queue.push(...node.children);
  }
  return nodes;
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
