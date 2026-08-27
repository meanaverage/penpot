import { beforeEach, describe, expect, it } from 'vitest';
import {
  SAYHI_COMPONENT_ID,
  SAYHI_COMPONENT_VERSION,
  SAYHI_NAMESPACE,
} from './contract.js';
import {
  exportVerifyDtcgPackage,
  exportVerifyProjectionSnapshot,
  importVerifyComponent,
} from './importer.js';

interface FakeDataTarget {
  data: Map<string, string>;
  setSharedPluginData(namespace: string, key: string, value: string): void;
  getSharedPluginData(namespace: string, key: string): string;
}

interface FakeShape extends FakeDataTarget {
  id: string;
  type: string;
  name: string;
  x: number;
  y: number;
  width: number;
  height: number;
  children: FakeShape[];
  removed: boolean;
  applied: Array<{ token: string; properties: string[] }>;
  rotationSetWithChildCount?: number;
  svgMarkup?: string;
  resize(width: number, height: number): void;
  appendChild(child: FakeShape): void;
  applyToken(token: FakeToken, properties: string[]): void;
  remove(): void;
  [key: string]: unknown;
}

interface FakeToken {
  id: string;
  type: string;
  name: string;
  value: unknown;
  description: string;
  remove(): void;
}

interface FakeSet {
  id: string;
  name: string;
  active: boolean;
  tokens: FakeToken[];
  addToken(input: { type: string; name: string; value: unknown }): FakeToken;
  remove(): void;
}

interface FakeTheme {
  id: string;
  group: string;
  name: string;
  active: boolean;
  activeSets: FakeSet[];
  addSet(set: FakeSet): void;
  removeSet(set: FakeSet): void;
  toggleActive(): void;
  remove(): void;
}

interface FakeComponent extends FakeDataTarget {
  id: string;
  name: string;
  path: string;
  root: FakeShape;
  mainInstance(): FakeShape;
}

interface FakeFontVariant {
  name: string;
  fontVariantId: string;
  fontWeight: string;
  fontStyle: 'normal' | 'italic';
}

interface FakeFont {
  name: string;
  fontId: string;
  fontFamily: string;
  fontStyle: 'normal' | 'italic';
  fontVariantId: string;
  fontWeight: string;
  variants: FakeFontVariant[];
  applyToText(text: FakeShape, variant?: FakeFontVariant): void;
}

interface FakePenpot {
  currentFile: { id: string };
  currentPage: { id: string };
  flags: {
    naturalChildOrdering: boolean;
  };
  viewport: {
    center: { x: number; y: number };
    zoomed: FakeShape[][];
    zoomIntoView(shapes: FakeShape[]): void;
  };
  fonts: {
    all: FakeFont[];
  };
  history: {
    began: number;
    finished: number;
    undoBlockBegin(): symbol;
    undoBlockFinish(): void;
  };
  library: {
    local: {
      components: FakeComponent[];
      tokens: {
        sets: FakeSet[];
        themes: FakeTheme[];
        addSet(input: { name: string; active?: boolean }): FakeSet;
        addTheme(input: { group: string; name: string }): FakeTheme;
      };
      createComponent(shapes: FakeShape[]): FakeComponent;
    };
  };
  selection: FakeShape[];
  createdShapes: FakeShape[];
  createBoard(): FakeShape;
  createRectangle(): FakeShape;
  createEllipse(): FakeShape;
  createPath(): FakeShape;
  createShapeFromSvg(markup: string): FakeShape | null;
  createText(characters: string): FakeShape;
}

let fakePenpot: FakePenpot;

beforeEach(() => {
  fakePenpot = createFakePenpot();
  (globalThis as unknown as { penpot: FakePenpot }).penpot = fakePenpot;
});

describe('Penpot-native Verify import', () => {
  it('creates native token sets, themes, editable shapes and a component in one undo block', async () => {
    const result = await importVerifyComponent();
    const component = fakePenpot.library.local.components[0];
    const root = component.root;

    expect(result.action).toBe('created');
    expect(result.createdSets).toBe(4);
    expect(result.createdThemes).toBe(2);
    expect(result.createdTokens).toBeGreaterThan(20);
    expect(result.boundShapes).toBeGreaterThan(10);
    expect(fakePenpot.flags.naturalChildOrdering).toBe(true);
    expect(fakePenpot.history).toMatchObject({ began: 1, finished: 1 });
    expect(component).toMatchObject({
      name: 'SayHi Verify',
      path: 'SayHi vNext',
    });
    expect(component.getSharedPluginData(SAYHI_NAMESPACE, 'component-id')).toBe(
      SAYHI_COMPONENT_ID,
    );
    expect(
      component.getSharedPluginData(SAYHI_NAMESPACE, 'component-version'),
    ).toBe(SAYHI_COMPONENT_VERSION);
    expect(
      JSON.parse(component.getSharedPluginData(SAYHI_NAMESPACE, 'web-object')),
    ).toEqual({
      schemaName: 'sayhi.web-object',
      schemaVersion: '1.0',
      id: SAYHI_COMPONENT_ID,
      revision: SAYHI_COMPONENT_VERSION,
      runtime: {
        provider: 'sayhi-studio',
        component: SAYHI_COMPONENT_ID,
        story: 'story.verify-methods-standalone',
      },
    });
    const storedPackage = JSON.parse(
      component.getSharedPluginData(SAYHI_NAMESPACE, 'dtcg-package'),
    ) as {
      $extensions: { 'io.sayhi.component': { motion?: unknown } };
    };
    expect(
      storedPackage.$extensions['io.sayhi.component'].motion,
    ).toBeDefined();
    const storedResolver = JSON.parse(
      component.getSharedPluginData(SAYHI_NAMESPACE, 'dtcg-resolver'),
    ) as {
      version: string;
      sets: {
        Components: {
          $extensions: { 'io.sayhi.component': { motion?: unknown } };
        };
      };
    };
    expect(storedResolver.version).toBe('2025.10');
    expect(
      storedResolver.sets.Components.$extensions['io.sayhi.component'].motion,
    ).toBeDefined();
    expect(
      component.getSharedPluginData(SAYHI_NAMESPACE, 'dtcg-source-mode'),
    ).toBe('resolver-2025.10');
    expect(findByName(root, 'Panel-face')).toHaveLength(1);
    expect(root.children[0]?.name).toBe('Panel-face');
    expect(root.children.at(-1)?.name).toBe('Cancel-action');
    expect(findByName(root, 'Method-face')).toHaveLength(2);
    expect(findByName(root, 'Active-method-face')).toHaveLength(1);
    expect(findByName(root, 'Email-icon')[0]).toMatchObject({
      width: 27.36,
      height: 27.36,
    });
    expect(findByName(root, 'Passkey-icon')).toHaveLength(1);
    expect(findByName(root, 'Passkey-icon')[0]).toMatchObject({
      width: 38.88,
      height: 38.88,
    });
    expect(findByName(root, 'Authenticator-icon')[0]).toMatchObject({
      width: 27.36,
      height: 27.36,
    });
    expect(findByName(root, 'Passkey-icon')[0]?.svgMarkup).toContain(
      'M16 14c1.5 0 2 1 2 2.5 0 5.4-.8 8.1-1.6 10.5',
    );
    expect(findByName(root, 'Passkey-icon')[0]?.svgMarkup).toContain(
      '<rect width="32" height="32" fill="#000000" fill-opacity="0" stroke="none"/>',
    );
    for (const sideCard of [
      ...findByName(root, 'Email-code'),
      ...findByName(root, 'Authenticator'),
    ]) {
      expect(sideCard.rotationSetWithChildCount).toBe(sideCard.children.length);
      expect(sideCard.rotationSetWithChildCount).toBeGreaterThan(0);
    }
    expect(findByName(root, 'Heading-copy')[0]).toMatchObject({
      fontId: 'gfont-instrument-sans',
      fontFamily: 'Instrument Sans',
      fontVariantId: '600',
      fontWeight: '600',
    });
    expect(findByName(root, 'Side-method-label')[0]).toMatchObject({
      fontVariantId: '700',
      fontWeight: '700',
    });
    expect(findByName(root, 'Heading-copy')[0]?.applied).toContainEqual({
      token: 'verify.typography.heading',
      properties: ['typography'],
    });
    expect(findByName(root, 'Body-copy')[0]?.applied).toContainEqual({
      token: 'verify.typography.body',
      properties: ['typography'],
    });
    for (const layer of findByName(root, 'Side-method-label')) {
      expect(layer.applied).toContainEqual({
        token: 'verify.typography.method.side.label',
        properties: ['typography'],
      });
    }
    for (const layer of findByName(root, 'Side-method-description')) {
      expect(layer.applied).toContainEqual({
        token: 'verify.typography.method.side.description',
        properties: ['typography'],
      });
    }
    expect(findByName(root, 'Passkey-icon')[0]?.applied).toContainEqual({
      token: 'verify.method.active.icon-size',
      properties: ['width', 'height'],
    });
    expect(root.applied).toHaveLength(0);
    expect(allShapes(root).some((shape) => shape.applied.length > 0)).toBe(
      true,
    );

    const themes = fakePenpot.library.local.tokens.themes;
    expect(themes.find((theme) => theme.name === 'Light')?.active).toBe(true);
    expect(themes.find((theme) => theme.name === 'Dark')?.active).toBe(false);
  });

  it('is idempotent and repairs bindings without creating a second component', async () => {
    const first = await importVerifyComponent();
    const component = fakePenpot.library.local.components[0];
    const shapeCount = fakePenpot.createdShapes.length;
    const second = await importVerifyComponent();

    expect(first.action).toBe('created');
    expect(second.action).toBe('unchanged');
    expect(second.createdSets).toBe(0);
    expect(second.createdThemes).toBe(0);
    expect(second.createdTokens).toBe(0);
    expect(fakePenpot.library.local.components).toHaveLength(1);
    expect(fakePenpot.createdShapes).toHaveLength(shapeCount);
    expect(fakePenpot.selection).toEqual([component.root]);
    expect(fakePenpot.history).toMatchObject({ began: 2, finished: 2 });
  });

  it('treats Penpot internal composite getters as the public typography contract', async () => {
    await importVerifyComponent();
    const components = fakePenpot.library.local.tokens.sets.find(
      (set) => set.name === 'Components',
    );
    const family = components?.tokens.find(
      (token) => token.name === 'verify.font.family',
    );
    const heading = components?.tokens.find(
      (token) => token.name === 'verify.typography.heading',
    );
    if (!family || !heading) throw new Error('Missing typography fixture.');

    // Penpot releases before the symmetric plugin getter fix expose a
    // whole-family reference as a one-entry vector and composite members with
    // internal singular/kebab keys. Re-importing must compare these as the
    // documented public value instead of rewriting a valid token.
    family.value = ['{font.family.sans}'];
    heading.value = {
      'font-family': ['{verify.font.family}'],
      'font-size': '{verify.heading.font-size}',
      'font-weight': '{verify.font.weight.medium}',
      'letter-spacing': '{verify.heading.letter-spacing}',
      'line-height': '1.2631578947',
      'text-case': 'none',
      'text-decoration': 'none',
    };

    const result = await importVerifyComponent();

    expect(result.changes).not.toContain('Updated token verify.font.family.');
    expect(result.changes).not.toContain(
      'Updated token verify.typography.heading.',
    );
    expect(heading.value).toMatchObject({
      'font-family': ['{verify.font.family}'],
      'font-size': '{verify.heading.font-size}',
      'font-weight': '{verify.font.weight.medium}',
    });
  });

  it('migrates legacy importer sets and unqualified component tokens without duplicates', async () => {
    await importVerifyComponent();
    const foundation = fakePenpot.library.local.tokens.sets.find(
      (set) => set.name === 'Foundation',
    );
    const components = fakePenpot.library.local.tokens.sets.find(
      (set) => set.name === 'Components',
    );
    if (!foundation || !components) {
      throw new Error('Missing canonical token architecture fixture.');
    }
    foundation.name = 'Core/Primitives';
    components.name = 'Component/Verify';
    for (const token of components.tokens) {
      token.name = token.name.replace(/^verify\./, '');
    }

    const migrated = await importVerifyComponent();

    expect(migrated.action).toBe('unchanged');
    expect(migrated.createdSets).toBe(0);
    expect(migrated.createdTokens).toBe(0);
    expect(fakePenpot.library.local.tokens.sets).toHaveLength(4);
    expect(fakePenpot.library.local.tokens.sets.map((set) => set.name)).toEqual(
      ['Foundation', 'Semantic/Light', 'Semantic/Dark', 'Components'],
    );
    expect(
      components.tokens.every((token) => token.name.startsWith('verify.')),
    ).toBe(true);
    expect(migrated.changes).toEqual(
      expect.arrayContaining([
        'Migrated token set Core/Primitives to Foundation.',
        'Migrated token set Component/Verify to Components.',
        'Namespaced component token panel.background as verify.panel.background.',
      ]),
    );
  });

  it('updates a prior component once and then reuses the current revision', async () => {
    await importVerifyComponent();
    const prior = fakePenpot.library.local.components[0];
    prior.setSharedPluginData(
      SAYHI_NAMESPACE,
      'component-version',
      '0.2.1-native-dtcg',
    );

    const updated = await importVerifyComponent();
    const verified = await importVerifyComponent();

    expect(updated.action).toBe('updated');
    expect(verified.action).toBe('unchanged');
    expect(fakePenpot.library.local.components).toHaveLength(2);
    expect(prior.getSharedPluginData(SAYHI_NAMESPACE, 'superseded-by')).toBe(
      SAYHI_COMPONENT_VERSION,
    );
  });

  it('migrates existing generic dimensions into their native Penpot token types', async () => {
    await importVerifyComponent();
    const verifySet = fakePenpot.library.local.tokens.sets.find(
      (set) => set.name === 'Components',
    );
    const radius = verifySet?.tokens.find(
      (token) => token.name === 'verify.panel.border-radius',
    );
    if (!radius) throw new Error('Missing radius token fixture.');
    radius.type = 'dimension';

    await expect(importVerifyComponent()).resolves.toMatchObject({
      action: 'unchanged',
    });
    expect(
      verifySet?.tokens.find(
        (token) => token.name === 'verify.panel.border-radius',
      )?.type,
    ).toBe('borderRadius');
  });

  it('does not depend on the receiver-bound browser structuredClone method', async () => {
    await importVerifyComponent();
    const originalStructuredClone = globalThis.structuredClone;
    Object.defineProperty(globalThis, 'structuredClone', {
      configurable: true,
      value: () => {
        throw new TypeError('Illegal invocation');
      },
    });

    try {
      await expect(importVerifyComponent()).resolves.toMatchObject({
        action: 'unchanged',
      });
      expect(() => exportVerifyDtcgPackage()).not.toThrow();
    } finally {
      Object.defineProperty(globalThis, 'structuredClone', {
        configurable: true,
        value: originalStructuredClone,
      });
    }
  });

  it('rolls token and theme mutations back when native shape creation fails', async () => {
    fakePenpot.createText = () => {
      throw new Error('simulated text failure');
    };

    await expect(importVerifyComponent()).rejects.toThrow(
      'simulated text failure',
    );
    expect(fakePenpot.library.local.tokens.sets).toHaveLength(0);
    expect(fakePenpot.library.local.tokens.themes).toHaveLength(0);
    expect(fakePenpot.library.local.components).toHaveLength(0);
    expect(fakePenpot.history).toMatchObject({ began: 1, finished: 1 });
  });

  it('exports native token edits back into the canonical resolver document', async () => {
    await importVerifyComponent();
    const primitives = fakePenpot.library.local.tokens.sets.find(
      (set) => set.name === 'Foundation',
    );
    const panel = primitives?.tokens.find(
      (token) => token.name === 'color.base.panel',
    );
    if (!panel) throw new Error('Missing native panel token.');
    panel.value = '#ff00aa';

    const exported = JSON.parse(exportVerifyDtcgPackage()) as {
      version: string;
      sets: {
        Foundation: {
          sources: Array<{
            color?: {
              base?: { panel?: { $value?: { hex?: string } } };
            };
          }>;
        };
        Components: {
          $extensions: { 'io.sayhi.component': { motion?: unknown } };
        };
      };
    };
    expect(exported.version).toBe('2025.10');
    expect(
      exported.sets.Foundation.sources[0].color?.base?.panel?.$value?.hex,
    ).toBe('#ff00aa');
    expect(
      exported.sets.Components.$extensions['io.sayhi.component'].motion,
    ).toBeDefined();
  });

  it('exports the actual imported Penpot tree as a normalized fidelity snapshot', async () => {
    await importVerifyComponent();

    const snapshot = JSON.parse(exportVerifyProjectionSnapshot()) as {
      schemaName: string;
      schemaVersion: string;
      componentId: string;
      componentVersion: string;
      root: {
        sourceId: string;
        frame: { x: number; y: number; width: number; height: number };
        children: Array<unknown>;
      };
    };

    expect(snapshot).toMatchObject({
      schemaName: 'io.sayhi.penpot-projection-snapshot',
      schemaVersion: '0.1.0',
      componentId: SAYHI_COMPONENT_ID,
      componentVersion: SAYHI_COMPONENT_VERSION,
    });
    expect(snapshot.root).toMatchObject({
      sourceId: 'verify.root',
      frame: { x: 0, y: 0, width: 580, height: 500 },
    });

    const nodes = flattenProjection(snapshot.root);
    expect(nodes.get('verify.panel.face')).toMatchObject({
      type: 'rectangle',
      frame: { x: 1, y: 1, width: 578, height: 498 },
      borderRadius: {
        topLeft: 24,
        topRight: 24,
        bottomRight: 24,
        bottomLeft: 24,
      },
    });
    expect(nodes.get('verify.method.passkey')).toMatchObject({
      type: 'board',
      frame: { x: 190, y: 145, width: 200, height: 174 },
    });
    expect(nodes.get('verify.passkey-icon')).toMatchObject({
      type: 'group',
      frame: { x: 270.56, y: 173, width: 38.88, height: 38.88 },
    });
  });
});

function flattenProjection(root: {
  sourceId: string;
  children?: Array<{
    sourceId: string;
    children?: unknown[];
  }>;
}): Map<string, unknown> {
  const nodes = new Map<string, unknown>();
  const queue: Array<{
    sourceId: string;
    children?: Array<{
      sourceId: string;
      children?: unknown[];
    }>;
  }> = [root];
  while (queue.length) {
    const node = queue.shift();
    if (!node) continue;
    if (node.sourceId) nodes.set(node.sourceId, node);
    for (const child of node.children ?? []) {
      queue.push(
        child as {
          sourceId: string;
          children?: Array<{
            sourceId: string;
            children?: unknown[];
          }>;
        },
      );
    }
  }
  return nodes;
}

function createFakePenpot(): FakePenpot {
  let nextId = 1;
  const sets: FakeSet[] = [];
  const themes: FakeTheme[] = [];
  const components: FakeComponent[] = [];
  const createdShapes: FakeShape[] = [];
  const variants: FakeFontVariant[] = [400, 500, 600, 700].map((weight) => ({
    name: String(weight),
    fontVariantId: weight === 400 ? 'regular' : String(weight),
    fontWeight: String(weight),
    fontStyle: 'normal',
  }));
  const fonts: FakeFont[] = [
    {
      name: 'Instrument Sans',
      fontId: 'gfont-instrument-sans',
      fontFamily: 'Instrument Sans',
      fontStyle: 'normal',
      fontVariantId: 'regular',
      fontWeight: '400',
      variants,
      applyToText(text, variant = variants[0]) {
        text.fontId = this.fontId;
        text.fontFamily = this.fontFamily;
        text.fontStyle = variant.fontStyle;
        text.fontVariantId = variant.fontVariantId;
        text.fontWeight = variant.fontWeight;
      },
    },
  ];

  const makeDataTarget = (): FakeDataTarget => ({
    data: new Map(),
    setSharedPluginData(namespace, key, value) {
      this.data.set(`${namespace}\u0000${key}`, value);
    },
    getSharedPluginData(namespace, key) {
      return this.data.get(`${namespace}\u0000${key}`) ?? '';
    },
  });

  const makeShape = (type: string): FakeShape => {
    let rotation = 0;
    const shape: FakeShape = {
      ...makeDataTarget(),
      id: `shape-${nextId++}`,
      type,
      name: '',
      x: 0,
      y: 0,
      width: 0,
      height: 0,
      children: [],
      removed: false,
      applied: [],
      resize(width, height) {
        this.width = width;
        this.height = height;
      },
      appendChild(child) {
        this.children.push(child);
      },
      applyToken(token, properties) {
        this.applied.push({ token: token.name, properties: [...properties] });
      },
      remove() {
        this.removed = true;
      },
    };
    Object.defineProperty(shape, 'rotation', {
      configurable: true,
      enumerable: true,
      get: () => rotation,
      set: (value: number) => {
        rotation = value;
        shape.rotationSetWithChildCount = shape.children.length;
      },
    });
    createdShapes.push(shape);
    return shape;
  };

  const tokens = {
    sets,
    themes,
    addSet(input: { name: string; active?: boolean }): FakeSet {
      const tokenSet: FakeSet = {
        id: `set-${nextId++}`,
        name: input.name,
        active: Boolean(input.active),
        tokens: [],
        addToken(tokenInput) {
          const token: FakeToken = {
            id: `token-${nextId++}`,
            type: tokenInput.type,
            name: tokenInput.name,
            value: structuredClone(tokenInput.value),
            description: '',
            remove() {
              const index = tokenSet.tokens.indexOf(token);
              if (index >= 0) tokenSet.tokens.splice(index, 1);
            },
          };
          tokenSet.tokens.push(token);
          return token;
        },
        remove() {
          const index = sets.indexOf(tokenSet);
          if (index >= 0) sets.splice(index, 1);
        },
      };
      sets.push(tokenSet);
      return tokenSet;
    },
    addTheme(input: { group: string; name: string }): FakeTheme {
      const theme: FakeTheme = {
        id: `theme-${nextId++}`,
        group: input.group,
        name: input.name,
        active: false,
        activeSets: [],
        addSet(set) {
          if (!this.activeSets.includes(set)) this.activeSets.push(set);
        },
        removeSet(set) {
          this.activeSets = this.activeSets.filter(
            (candidate) => candidate !== set,
          );
        },
        toggleActive() {
          if (!this.active) {
            for (const candidate of themes) {
              if (candidate.group === this.group) candidate.active = false;
            }
          }
          this.active = !this.active;
        },
        remove() {
          const index = themes.indexOf(theme);
          if (index >= 0) themes.splice(index, 1);
        },
      };
      themes.push(theme);
      return theme;
    },
  };

  const history = {
    began: 0,
    finished: 0,
    undoBlockBegin() {
      this.began += 1;
      return Symbol('undo');
    },
    undoBlockFinish() {
      this.finished += 1;
    },
  };

  return {
    currentFile: { id: 'file-1' },
    currentPage: { id: 'page-1' },
    flags: { naturalChildOrdering: false },
    fonts: { all: fonts },
    viewport: {
      center: { x: 800, y: 500 },
      zoomed: [],
      zoomIntoView(shapes) {
        this.zoomed.push([...shapes]);
      },
    },
    history,
    library: {
      local: {
        components,
        tokens,
        createComponent(shapes) {
          const target = makeDataTarget();
          const component: FakeComponent = {
            ...target,
            id: `component-${nextId++}`,
            name: '',
            path: '',
            root: shapes[0],
            mainInstance() {
              return this.root;
            },
          };
          components.push(component);
          return component;
        },
      },
    },
    selection: [],
    createdShapes,
    createBoard: () => makeShape('board'),
    createRectangle: () => makeShape('rectangle'),
    createEllipse: () => makeShape('ellipse'),
    createPath: () => makeShape('path'),
    createShapeFromSvg: (markup) => {
      const shape = makeShape('group');
      shape.svgMarkup = markup;
      return shape;
    },
    createText: (characters) => {
      const shape = makeShape('text');
      shape.characters = characters;
      return shape;
    },
  };
}

function allShapes(root: FakeShape): FakeShape[] {
  return [root, ...root.children.flatMap(allShapes)];
}

function findByName(root: FakeShape, name: string): FakeShape[] {
  return allShapes(root).filter((shape) => shape.name === name);
}
