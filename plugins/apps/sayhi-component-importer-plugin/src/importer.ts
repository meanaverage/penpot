import type {
  Board,
  Font,
  FontVariant,
  Group,
  LibraryComponent,
  Rectangle,
  Shape,
  Text,
  Token,
  TokenSet,
  TokenTheme,
  TokenValueString,
} from '@penpot/plugin-types';
import {
  DTCG_2025_10_SCHEMA,
  SAYHI_COMPONENT_ID,
  SAYHI_COMPONENT_VERSION,
  SAYHI_NAMESPACE,
  type ImportResult,
  type JsonValue,
  type MaterializedPackage,
  type MaterializedToken,
  type NativeSetSnapshot,
  type NativeThemeSnapshot,
  type PenpotProjectionSnapshot,
  type ProjectionFrame,
  type ProjectionNodeSnapshot,
} from './contract.js';
import {
  VERIFY_METHOD_ICON_SIZES,
  VERIFY_TOKEN_BINDINGS,
} from './verify-package.js';
import {
  VERIFY_TOKEN_SOURCE_MODE,
  canonicalVerifyTokenSource,
  legacyVerifyPackageJson,
  materializeVerifyTokenSource,
  roundTripStoredVerifyTokenSource,
  verifyComponentExtension,
} from './token-source.js';

const LIBRARY_PATH = 'SayHi vNext';
const COMPONENT_NAME = 'SayHi Verify';
const IMPORT_TRACK = 'native-dtcg/v2';
const LEGACY_SET_NAMES = new Map<string, string[]>([
  ['Foundation', ['Core/Primitives', 'SayHi/Primitives']],
  ['Semantic/Light', ['SayHi/Semantic/Light']],
  ['Semantic/Dark', ['SayHi/Semantic/Dark']],
  ['Components', ['Component/Verify', 'SayHi/Component/Verify']],
]);

interface ProvisionedTokens {
  tokensByName: Map<string, Token>;
  createdSets: number;
  createdThemes: number;
  createdTokens: number;
}

class MutationJournal {
  readonly changes: string[] = [];
  readonly #undo: Array<() => void> = [];

  record(change: string, undo?: () => void): void {
    this.changes.push(change);
    if (undo) this.#undo.push(undo);
  }

  rollback(): void {
    for (const undo of [...this.#undo].reverse()) {
      try {
        undo();
      } catch {
        // Continue restoring the remaining mutations. The original import
        // failure is more useful than a secondary rollback error.
      }
    }
  }
}

interface Point {
  x: number;
  y: number;
}

interface NativeFont {
  font: Font;
}

export async function importVerifyComponent(): Promise<ImportResult> {
  if (!penpot.currentFile || !penpot.currentPage) {
    throw new Error('Open a Penpot design file before importing SayHi Verify.');
  }

  // Penpot keeps the historical child-ordering behavior disabled per plugin
  // for compatibility. That legacy mode inserts newly appended children behind
  // earlier children, which puts a component's background face above all of
  // its content. This importer authors back-to-front, so opt into Penpot's
  // canonical z-index ordering before creating any shape.
  penpot.flags.naturalChildOrdering = true;

  const materialized = materializeVerifyTokenSource();
  const nativeFont = resolveNativeFont(materialized);
  const canonical = canonicalVerifyTokenSource();
  const fingerprint = fingerprintOf(canonical);
  const historyBlock = penpot.history.undoBlockBegin();
  const journal = new MutationJournal();
  let root: Board | null = null;
  let priorIdentity: {
    component: LibraryComponent;
    name: string;
    path: string;
  } | null = null;

  try {
    const provisioned = provisionTokens(materialized, journal);
    const existing = findCurrentComponent();

    if (
      existing &&
      readData(existing, 'dtcg-fingerprint') === fingerprint &&
      readData(existing, 'component-version') === SAYHI_COMPONENT_VERSION
    ) {
      const existingRoot = existing.mainInstance();
      const boundShapes = applyBindings(existingRoot, provisioned.tokensByName);
      writeProvenance(existing, canonical, fingerprint);
      writeProvenance(existingRoot, canonical, fingerprint);
      tagAnatomy(existingRoot);
      // A no-op reimport is also the repair path for a document that still has
      // an older placed instance selected. Select the verified main instance
      // so the native Motion Studio eligibility resolver sees the contract the
      // importer just repaired instead of leaving a stale sibling selected.
      penpot.selection = [existingRoot];
      return result(
        'unchanged',
        materialized,
        provisioned,
        boundShapes,
        journal.changes,
      );
    }

    if (existing) {
      priorIdentity = {
        component: existing,
        name: existing.name,
        path: existing.path,
      };
      existing.path = `${LIBRARY_PATH}/Legacy`;
      existing.name = `${COMPONENT_NAME} · ${readData(existing, 'component-version') || 'prior'}`;
      existing.setSharedPluginData(
        SAYHI_NAMESPACE,
        'superseded-by',
        SAYHI_COMPONENT_VERSION,
      );
      journal.record(
        `Superseded ${priorIdentity.name} without deleting it.`,
        () => {
          if (!priorIdentity) return;
          priorIdentity.component.name = priorIdentity.name;
          priorIdentity.component.path = priorIdentity.path;
        },
      );
    }

    root = buildVerifyComponent(nativeFont);
    writeProvenance(root, canonical, fingerprint);
    tagAnatomy(root);
    const boundShapes = applyBindings(root, provisioned.tokensByName);

    const component = penpot.library.local.createComponent([root]);
    component.name = COMPONENT_NAME;
    component.path = LIBRARY_PATH;
    writeProvenance(component, canonical, fingerprint);
    const componentRoot = component.mainInstance();
    writeProvenance(componentRoot, canonical, fingerprint);
    tagAnatomy(componentRoot);

    penpot.selection = [componentRoot];
    penpot.viewport.zoomIntoView([componentRoot]);
    journal.record(
      `${existing ? 'Updated' : 'Created'} editable ${COMPONENT_NAME} component.`,
    );
    return result(
      existing ? 'updated' : 'created',
      materialized,
      provisioned,
      boundShapes,
      journal.changes,
    );
  } catch (error) {
    root?.remove();
    if (priorIdentity) {
      priorIdentity.component.name = priorIdentity.name;
      priorIdentity.component.path = priorIdentity.path;
    }
    journal.rollback();
    throw error;
  } finally {
    penpot.history.undoBlockFinish(historyBlock);
  }
}

export function exportVerifyDtcgPackage(): string {
  const component = findCurrentComponent();
  if (!component) {
    throw new Error(
      'Import SayHi Verify vNext before exporting its DTCG resolver.',
    );
  }
  const resolver = readData(component, 'dtcg-resolver');
  const legacy = readData(component, 'dtcg-package');
  const stored = resolver || legacy;
  if (!stored) {
    throw new Error('The imported component has no canonical DTCG source.');
  }
  const catalog = penpot.library.local.tokens;
  const sets: NativeSetSnapshot[] = catalog.sets.map((set) => ({
    name: set.name,
    active: set.active,
    tokens: set.tokens.map((token) => ({
      name: token.name,
      value: publicTokenValue(token.type, token.value) as TokenValueString,
      description: token.description,
    })),
  }));
  const themes: NativeThemeSnapshot[] = catalog.themes.map((theme) => ({
    group: theme.group,
    name: theme.name,
    active: theme.active,
    sets: theme.activeSets.map((set) => set.name),
  }));
  return roundTripStoredVerifyTokenSource(
    stored,
    sets,
    themes,
    resolver ? 'resolver-2025.10' : 'legacy-package',
  );
}

/**
 * Capture what Penpot actually materialized, not what the importer requested.
 *
 * Every geometric value is read back through the public plugin API after the
 * component has been created. Coordinates and rotated bounds are normalized to
 * the component root so snapshots remain comparable across canvas placement.
 */
export function exportVerifyProjectionSnapshot(): string {
  const component = findCurrentComponent();
  if (!component) {
    throw new Error(
      'Import SayHi Verify vNext before exporting its Penpot projection.',
    );
  }
  const root = component.mainInstance();
  const rootOrigin = { x: root.x, y: root.y };
  const snapshot: PenpotProjectionSnapshot = {
    schemaName: 'io.sayhi.penpot-projection-snapshot',
    schemaVersion: '0.1.0',
    componentId: SAYHI_COMPONENT_ID,
    componentVersion: SAYHI_COMPONENT_VERSION,
    storyId: readData(component, 'story-id'),
    root: snapshotShape(root, rootOrigin),
  };
  return JSON.stringify(snapshot, null, 2);
}

function snapshotShape(shape: Shape, root: Point): ProjectionNodeSnapshot {
  const uniformRadius = finiteMetric(shape.borderRadius);
  const node: ProjectionNodeSnapshot = {
    sourceId: readData(shape, 'source-id'),
    motionPart: readData(shape, 'motion-part'),
    name: shape.name,
    type: shape.type,
    frame: relativeFrame(shape, root),
    bounds: relativeBounds(shape, root),
    parentFrame: coordinatePair(shape.parentX, shape.parentY),
    boardFrame: coordinatePair(shape.boardX, shape.boardY),
    rotation: finiteMetric(shape.rotation),
    flipX: Boolean(shape.flipX),
    flipY: Boolean(shape.flipY),
    opacity: finiteMetric(shape.opacity, 1),
    hidden: Boolean(shape.hidden),
    visible: shape.visible !== false,
    blendMode: shape.blendMode || 'normal',
    borderRadius: {
      topLeft: finiteMetric(shape.borderRadiusTopLeft, uniformRadius),
      topRight: finiteMetric(shape.borderRadiusTopRight, uniformRadius),
      bottomRight: finiteMetric(shape.borderRadiusBottomRight, uniformRadius),
      bottomLeft: finiteMetric(shape.borderRadiusBottomLeft, uniformRadius),
    },
    fills: jsonValue(shape.fills),
    strokes: jsonValue(shape.strokes),
    shadows: jsonValue(shape.shadows),
    children: [],
  };

  if ('clipContent' in shape) node.clipContent = shape.clipContent;
  if ('showInViewMode' in shape) node.showInViewMode = shape.showInViewMode;
  if ('d' in shape && typeof shape.d === 'string') node.pathData = shape.d;
  if (shape.type === 'text') {
    node.text = {
      characters: shape.characters,
      growType: shape.growType,
      fontId: stringMetric(shape.fontId),
      fontFamily: stringMetric(shape.fontFamily),
      fontVariantId: stringMetric(shape.fontVariantId),
      fontSize: stringMetric(shape.fontSize),
      fontWeight: stringMetric(shape.fontWeight),
      fontStyle: nullableStringMetric(shape.fontStyle),
      lineHeight: stringMetric(shape.lineHeight),
      letterSpacing: stringMetric(shape.letterSpacing),
      textTransform: nullableStringMetric(shape.textTransform),
      textDecoration: nullableStringMetric(shape.textDecoration),
      direction: nullableStringMetric(shape.direction),
      align: nullableStringMetric(shape.align),
      verticalAlign: nullableStringMetric(shape.verticalAlign),
      bounds: shape.textBounds
        ? normalizeFrame(shape.textBounds, root)
        : undefined,
    };
  }
  if ('children' in shape) {
    node.children = shape.children.map((child) => snapshotShape(child, root));
  }
  return node;
}

function relativeFrame(shape: Shape, root: Point): ProjectionFrame {
  return {
    x: finiteMetric(shape.x - root.x),
    y: finiteMetric(shape.y - root.y),
    width: finiteMetric(shape.width),
    height: finiteMetric(shape.height),
  };
}

function relativeBounds(shape: Shape, root: Point): ProjectionFrame {
  const bounds = shape.bounds;
  return bounds ? normalizeFrame(bounds, root) : relativeFrame(shape, root);
}

function normalizeFrame(
  frame: { x: number; y: number; width: number; height: number },
  root: Point,
): ProjectionFrame {
  return {
    x: finiteMetric(frame.x - root.x),
    y: finiteMetric(frame.y - root.y),
    width: finiteMetric(frame.width),
    height: finiteMetric(frame.height),
  };
}

function coordinatePair(
  x: number | undefined,
  y: number | undefined,
): { x: number; y: number } | undefined {
  if (!Number.isFinite(x) || !Number.isFinite(y)) return undefined;
  return { x: finiteMetric(x), y: finiteMetric(y) };
}

function finiteMetric(value: unknown, fallback = 0): number {
  return typeof value === 'number' && Number.isFinite(value)
    ? Number(value.toFixed(6))
    : fallback;
}

function stringMetric(value: unknown): string {
  return typeof value === 'string' ? value : String(value ?? '');
}

function nullableStringMetric(value: unknown): string | null {
  return value === null || value === undefined ? null : stringMetric(value);
}

function jsonValue(value: unknown): JsonValue {
  if (
    value === null ||
    typeof value === 'boolean' ||
    typeof value === 'number' ||
    typeof value === 'string'
  ) {
    return value;
  }
  if (Array.isArray(value)) return value.map((entry) => jsonValue(entry));
  if (!value || typeof value !== 'object') return String(value ?? '');
  const result: Record<string, JsonValue> = {};
  for (const key of Object.keys(value)) {
    let entry: unknown;
    try {
      entry = (value as Record<string, unknown>)[key];
    } catch {
      continue;
    }
    if (typeof entry === 'function' || entry === undefined) continue;
    result[key] = jsonValue(entry);
  }
  return result;
}

function result(
  action: ImportResult['action'],
  materialized: MaterializedPackage,
  provisioned: ProvisionedTokens,
  boundShapes: number,
  changes: string[],
): ImportResult {
  return {
    action,
    componentVersion: SAYHI_COMPONENT_VERSION,
    createdSets: provisioned.createdSets,
    createdThemes: provisioned.createdThemes,
    createdTokens: provisioned.createdTokens,
    unsupportedTokens: materialized.unsupported.length,
    boundShapes,
    changes: changes.length
      ? [...changes]
      : [`Verified ${boundShapes} native token bindings.`],
  };
}

function provisionTokens(
  materialized: MaterializedPackage,
  journal: MutationJournal,
): ProvisionedTokens {
  const catalog = penpot.library.local.tokens;
  migrateLegacyTokenArchitecture(catalog.sets, materialized, journal);
  const setsByName = new Map(catalog.sets.map((set) => [set.name, set]));
  const tokensByName = new Map<string, Token>();
  let createdSets = 0;
  let createdThemes = 0;
  let createdTokens = 0;

  for (const setPlan of materialized.sets) {
    let set = setsByName.get(setPlan.name);
    if (!set) {
      set = catalog.addSet({ name: setPlan.name, active: setPlan.active });
      setsByName.set(setPlan.name, set);
      createdSets += 1;
      const createdSet = set;
      journal.record(`Created token set ${setPlan.name}.`, () =>
        createdSet.remove(),
      );
    } else if (set.active !== setPlan.active) {
      const previousActive = set.active;
      set.active = setPlan.active;
      const changedSet = set;
      journal.record(`Aligned token set ${setPlan.name}.`, () => {
        changedSet.active = previousActive;
      });
    }

    for (const tokenPlan of setPlan.tokens) {
      const tokenResult = upsertToken(set, tokenPlan, journal);
      tokensByName.set(tokenPlan.name, tokenResult.token);
      if (tokenResult.created) createdTokens += 1;
    }
  }

  const themesByKey = new Map(
    catalog.themes.map((theme) => [themeKey(theme.group, theme.name), theme]),
  );
  for (const themePlan of materialized.themes) {
    const key = themeKey(themePlan.group, themePlan.name);
    let theme = themesByKey.get(key);
    if (!theme) {
      theme = catalog.addTheme({
        group: themePlan.group,
        name: themePlan.name,
      });
      themesByKey.set(key, theme);
      createdThemes += 1;
      const createdTheme = theme;
      journal.record(`Created theme ${themePlan.name}.`, () =>
        createdTheme.remove(),
      );
    }
    syncThemeSets(theme, themePlan.sets, setsByName, journal);
  }
  const changedThemeGroups = new Set<string>();
  for (const themePlan of materialized.themes) {
    const theme = themesByKey.get(themeKey(themePlan.group, themePlan.name));
    if (theme && theme.active !== themePlan.active) {
      if (!changedThemeGroups.has(theme.group)) {
        const groupThemes = catalog.themes.filter(
          (candidate) => candidate.group === theme.group,
        );
        const previouslyActive = groupThemes.find(
          (candidate) => candidate.active,
        );
        journal.record(`Activated theme ${themePlan.name}.`, () => {
          const currentlyActive = groupThemes.find(
            (candidate) => candidate.active,
          );
          if (previouslyActive && !previouslyActive.active) {
            previouslyActive.toggleActive();
          } else if (!previouslyActive && currentlyActive) {
            currentlyActive.toggleActive();
          }
        });
        changedThemeGroups.add(theme.group);
      }
      theme.toggleActive();
    }
  }

  return { tokensByName, createdSets, createdThemes, createdTokens };
}

function migrateLegacyTokenArchitecture(
  sets: TokenSet[],
  materialized: MaterializedPackage,
  journal: MutationJournal,
): void {
  const setsByName = new Map(sets.map((set) => [set.name, set]));

  for (const [currentName, legacyNames] of LEGACY_SET_NAMES) {
    if (setsByName.has(currentName)) continue;
    const legacyName = legacyNames.find((name) => setsByName.has(name));
    if (!legacyName) continue;
    const legacySet = setsByName.get(legacyName);
    if (!legacySet) continue;

    legacySet.name = currentName;
    setsByName.delete(legacyName);
    setsByName.set(currentName, legacySet);
    journal.record(
      `Migrated token set ${legacyName} to ${currentName}.`,
      () => {
        legacySet.name = legacyName;
      },
    );
  }

  const componentSet = setsByName.get('Components');
  const componentPlan = materialized.sets.find(
    (set) => set.name === 'Components',
  );
  if (!componentSet || !componentPlan) return;

  const plannedNames = new Set(componentPlan.tokens.map((token) => token.name));
  const existingNames = new Set(componentSet.tokens.map((token) => token.name));
  for (const token of componentSet.tokens) {
    if (token.name.startsWith('verify.')) continue;
    const previousName = token.name;
    const namespacedName = `verify.${previousName}`;
    if (
      !plannedNames.has(namespacedName) ||
      existingNames.has(namespacedName)
    ) {
      continue;
    }

    token.name = namespacedName;
    existingNames.delete(previousName);
    existingNames.add(namespacedName);
    journal.record(
      `Namespaced component token ${previousName} as ${namespacedName}.`,
      () => {
        token.name = previousName;
      },
    );
  }
}

function upsertToken(
  set: TokenSet,
  plan: MaterializedToken,
  journal: MutationJournal,
): { token: Token; created: boolean } {
  if (!plan.penpotType || plan.value === undefined) {
    throw new Error(`Token ${plan.name} cannot be materialized in Penpot.`);
  }
  const existing = set.tokens.find((token) => token.name === plan.name);
  if (existing && existing.type === plan.penpotType) {
    const mutable = existing as unknown as { value: unknown };
    // Penpot's current Token.value getter exposes composite values with its
    // internal kebab-case keys, while the documented plugin setter accepts the
    // public camelCase shape. Compare and restore through one public boundary;
    // otherwise an idempotent re-import rewrites a valid typography token with
    // a partially decoded object and leaves it visibly invalid.
    const previousValue = publicTokenValue(existing.type, mutable.value);
    const previousDescription = existing.description;
    const valueChanged =
      JSON.stringify(previousValue) !== JSON.stringify(plan.value);
    const descriptionChanged = previousDescription !== (plan.description ?? '');
    if (valueChanged) {
      mutable.value = plan.value;
    }
    if (descriptionChanged) {
      existing.description = plan.description ?? '';
    }
    if (valueChanged || descriptionChanged) {
      journal.record(`Updated token ${plan.name}.`, () => {
        mutable.value = previousValue;
        existing.description = previousDescription;
      });
    }
    return { token: existing, created: false };
  }
  if (existing) {
    const previous = {
      type: existing.type,
      name: existing.name,
      value: publicTokenValue(existing.type, existing.value),
      description: existing.description,
    };
    existing.remove();
    const replacement = addToken(set, plan, 'replacement');
    replacement.description = plan.description ?? '';
    journal.record(
      `Projected token ${plan.name} from ${previous.type} to ${plan.penpotType}.`,
      () => {
        replacement.remove();
        const restored = set.addToken({
          type: previous.type,
          name: previous.name,
          value: previous.value as TokenValueString,
        });
        if (restored) restored.description = previous.description;
      },
    );
    return { token: replacement, created: false };
  }
  const created = addToken(set, plan, 'new');
  created.description = plan.description ?? '';
  journal.record(`Created token ${plan.name}.`, () => created.remove());
  return { token: created, created: true };
}

function addToken(
  set: TokenSet,
  plan: MaterializedToken,
  operation: 'new' | 'replacement',
): Token {
  if (!plan.penpotType || plan.value === undefined) {
    throw new Error(`Token ${plan.name} has no native Penpot projection.`);
  }
  const token = set.addToken({
    type: plan.penpotType,
    name: plan.name,
    value: plan.value as TokenValueString,
  });
  if (!token) {
    throw new Error(
      `Penpot rejected ${operation} token ${plan.name} ` +
        `(${plan.penpotType}) with value ${JSON.stringify(plan.value)}.`,
    );
  }
  return token;
}

function syncThemeSets(
  theme: TokenTheme,
  desiredNames: string[],
  setsByName: Map<string, TokenSet>,
  journal: MutationJournal,
): void {
  const desired = new Set(desiredNames);
  for (const activeSet of theme.activeSets) {
    if (!desired.has(activeSet.name)) {
      theme.removeSet(activeSet);
      journal.record(
        `Removed ${activeSet.name} from theme ${theme.name}.`,
        () => {
          theme.addSet(activeSet);
        },
      );
    }
  }
  const activeNames = new Set(theme.activeSets.map((set) => set.name));
  for (const name of desiredNames) {
    const set = setsByName.get(name);
    if (!set)
      throw new Error(`Theme ${theme.name} references missing set ${name}.`);
    if (!activeNames.has(name)) {
      theme.addSet(set);
      journal.record(`Added ${name} to theme ${theme.name}.`, () => {
        theme.removeSet(set);
      });
    }
  }
}

function buildVerifyComponent(nativeFont: NativeFont): Board {
  const center = penpot.viewport.center;
  const origin = { x: center.x - 290, y: center.y - 250 };
  const root = penpot.createBoard();
  root.name = 'SayHi-Verify';
  root.x = origin.x;
  root.y = origin.y;
  root.resize(580, 500);
  root.fills = [{ fillColor: '#ffffff', fillOpacity: 0 }];
  root.clipContent = false;
  root.showInViewMode = true;
  root.setSharedPluginData(SAYHI_NAMESPACE, 'source-id', 'verify.root');

  const panel = rectangle(root, origin, 'Panel-face', 1, 1, 578, 498, {
    fill: '#eef1eb',
    stroke: '#dce1da',
    radius: 24,
  });
  panel.setSharedPluginData(SAYHI_NAMESPACE, 'source-id', 'verify.panel.face');

  text(
    root,
    origin,
    nativeFont,
    'Eyebrow-copy',
    'SAYHI VERIFY',
    0,
    34,
    580,
    22,
    {
      size: 11,
      weight: 700,
      color: '#315f47',
      tracking: 1.3,
    },
  );
  text(
    root,
    origin,
    nativeFont,
    'Heading-copy',
    'Verify it’s you',
    0,
    58,
    580,
    48,
    {
      size: 38,
      weight: 570,
      color: '#171a17',
      tracking: -1.8,
    },
  );
  text(
    root,
    origin,
    nativeFont,
    'Body-copy',
    'Choose how you’d like to verify.',
    0,
    108,
    580,
    28,
    { size: 16, weight: 400, color: '#697169' },
  );

  const methods = board(root, origin, 'Verification-methods', 0, 138, 580, 190);
  methods.fills = [{ fillColor: '#ffffff', fillOpacity: 0 }];
  methods.clipContent = false;
  sideMethod(methods, origin, nativeFont, {
    id: 'email',
    name: 'Email-code',
    x: 61,
    y: 179,
    rotation: -2.4,
    title: 'Email code',
    description: 'Send a code',
    icon: 'email',
  });
  sideMethod(methods, origin, nativeFont, {
    id: 'authenticator',
    name: 'Authenticator',
    x: 382,
    y: 179,
    rotation: 2.4,
    title: 'Authenticator',
    description: 'Use your app',
    icon: 'authenticator',
  });
  activeMethod(methods, origin, nativeFont);

  const pagination = board(root, origin, 'Pagination', 272, 340, 48, 12);
  pagination.fills = [{ fillColor: '#ffffff', fillOpacity: 0 }];
  pagination.clipContent = false;
  ellipse(
    pagination,
    origin,
    'Pagination-dot',
    272,
    340,
    8,
    8,
    '#aab0aa',
    0.55,
  );
  rectangle(pagination, origin, 'Pagination-active', 286, 341, 18, 6, {
    fill: '#315f47',
    radius: 3,
  });
  ellipse(
    pagination,
    origin,
    'Pagination-dot',
    310,
    340,
    8,
    8,
    '#aab0aa',
    0.55,
  );

  text(
    root,
    origin,
    nativeFont,
    'Hint-copy',
    'Tab or use ← → to move between methods. Enter to continue.',
    0,
    365,
    580,
    28,
    { size: 11.5, weight: 400, color: '#697169' },
  );
  text(
    root,
    origin,
    nativeFont,
    'Cancel-action',
    'Cancel sign-in',
    0,
    430,
    580,
    24,
    {
      size: 13,
      weight: 650,
      color: '#315f47',
    },
  );

  return root;
}

interface MethodOptions {
  id: string;
  name: string;
  x: number;
  y: number;
  rotation: number;
  title: string;
  description: string;
  icon: 'email' | 'authenticator';
}

function sideMethod(
  parent: Board,
  origin: Point,
  nativeFont: NativeFont,
  options: MethodOptions,
): void {
  const method = board(
    parent,
    origin,
    options.name,
    options.x,
    options.y,
    137,
    117,
  );
  method.opacity = 0.66;
  method.fills = [{ fillColor: '#ffffff', fillOpacity: 0 }];
  method.clipContent = false;
  method.setSharedPluginData(
    SAYHI_NAMESPACE,
    'source-id',
    `verify.method.${options.id}`,
  );
  rectangle(method, origin, 'Method-face', options.x, options.y, 137, 117, {
    fill: '#ffffff',
    stroke: '#dce1da',
    radius: 17,
  });
  if (options.icon === 'email')
    emailIcon(method, origin, options.x + 54.82, options.y + 20.32);
  else authenticatorIcon(method, origin, options.x + 54.82, options.y + 20.32);
  text(
    method,
    origin,
    nativeFont,
    'Side-method-label',
    options.title,
    options.x,
    options.y + 67,
    137,
    21,
    {
      size: 12.16,
      weight: 710,
      color: '#171a17',
    },
  );
  text(
    method,
    origin,
    nativeFont,
    'Side-method-description',
    options.description,
    options.x,
    options.y + 88,
    137,
    18,
    { size: 8.75, weight: 560, color: '#697169' },
  );
  // Penpot's rotation transform is applied to the descendants that exist at
  // the time of the operation. Rotate the completed subtree so its face,
  // icon, and copy remain visually aligned with the rotated frame geometry.
  method.rotation = options.rotation;
}

function activeMethod(
  parent: Board,
  origin: Point,
  nativeFont: NativeFont,
): void {
  const method = board(parent, origin, 'Passkey-selected', 190, 145, 200, 174);
  method.fills = [{ fillColor: '#ffffff', fillOpacity: 0 }];
  method.clipContent = false;
  method.setSharedPluginData(
    SAYHI_NAMESPACE,
    'source-id',
    'verify.method.passkey',
  );
  const shadow = rectangle(
    method,
    origin,
    'Passkey-shadow',
    198,
    155,
    192,
    166,
    {
      fill: '#28372b',
      radius: 25,
    },
  );
  shadow.opacity = 0.12;
  shadow.shadows = [
    {
      style: 'drop-shadow',
      offsetX: 0,
      offsetY: 10,
      blur: 28,
      spread: 0,
      color: { color: '#28372b', opacity: 0.12 },
    },
  ];
  const ring = rectangle(method, origin, 'Active-ring', 190, 145, 200, 174, {
    fill: '#8069ff',
    radius: 27,
  });
  ring.fills = [
    {
      fillColorGradient: {
        type: 'linear',
        startX: 0,
        startY: 0,
        endX: 1,
        endY: 1,
        width: 1,
        stops: [
          { color: '#55e6f0', opacity: 1, offset: 0 },
          { color: '#8069ff', opacity: 1, offset: 0.22 },
          { color: '#f15ac8', opacity: 1, offset: 0.48 },
          { color: '#f6ca5b', opacity: 1, offset: 0.72 },
          { color: '#58d891', opacity: 1, offset: 1 },
        ],
      },
    },
  ];
  rectangle(method, origin, 'Active-method-face', 194, 149, 192, 166, {
    fill: '#ffffff',
    radius: 23,
  });
  passkeyIcon(method, origin, 270.56, 173);
  text(
    method,
    origin,
    nativeFont,
    'Active-method-label',
    'Passkey',
    194,
    236,
    192,
    24,
    {
      size: 17,
      weight: 710,
      color: '#171a17',
    },
  );
  text(
    method,
    origin,
    nativeFont,
    'Active-method-description',
    'Continue',
    194,
    262,
    192,
    22,
    {
      size: 12,
      weight: 560,
      color: '#697169',
    },
  );
}

function emailIcon(parent: Board, origin: Point, x: number, y: number): void {
  svgIcon(
    parent,
    origin,
    'Email-icon',
    x,
    y,
    '<svg xmlns="http://www.w3.org/2000/svg" width="36" height="36" viewBox="0 0 32 32" fill="none" stroke="#171a17" stroke-width="1.8"><rect width="32" height="32" fill="#000000" fill-opacity="0" stroke="none"/><rect x="4" y="7" width="24" height="18" rx="3"/><path d="m6 10 10 8 10-8"/></svg>',
    VERIFY_METHOD_ICON_SIZES.side,
  );
}

function passkeyIcon(parent: Board, origin: Point, x: number, y: number): void {
  svgIcon(
    parent,
    origin,
    'Passkey-icon',
    x,
    y,
    '<svg xmlns="http://www.w3.org/2000/svg" width="36" height="36" viewBox="0 0 32 32" fill="none" stroke="#171a17" stroke-width="1.8" stroke-linecap="round"><rect width="32" height="32" fill="#000000" fill-opacity="0" stroke="none"/><path d="M16 5c-6 0-10 4.3-10 10.1M16 8c-4.2 0-7 3-7 7.2 0 5-1.2 7.3-2.4 9M16 11c-2.6 0-4 1.8-4 4.5 0 5.8-1.1 8.3-2.2 10.5M16 14c1.5 0 2 1 2 2.5 0 5.4-.8 8.1-1.6 10.5M20 12.5c1.4 1.3 2 3 2 5.2 0 4-.5 6.7-1.2 8.8M23 9.5c2.1 2 3 4.6 3 8.2 0 2.9-.3 5.1-.7 7"/></svg>',
    VERIFY_METHOD_ICON_SIZES.active,
  );
}

function authenticatorIcon(
  parent: Board,
  origin: Point,
  x: number,
  y: number,
): void {
  svgIcon(
    parent,
    origin,
    'Authenticator-icon',
    x,
    y,
    '<svg xmlns="http://www.w3.org/2000/svg" width="36" height="36" viewBox="0 0 32 32" fill="#171a17"><rect width="32" height="32" fill="#000000" fill-opacity="0"/><circle cx="9" cy="9" r="2"/><circle cx="16" cy="9" r="2"/><circle cx="23" cy="9" r="2"/><circle cx="9" cy="16" r="2"/><circle cx="16" cy="16" r="2"/><circle cx="23" cy="16" r="2"/><circle cx="9" cy="23" r="2"/><circle cx="16" cy="23" r="2"/><circle cx="23" cy="23" r="2"/></svg>',
    VERIFY_METHOD_ICON_SIZES.side,
  );
}

function svgIcon(
  parent: Board,
  origin: Point,
  name: string,
  x: number,
  y: number,
  markup: string,
  size: number,
): Group {
  const icon = penpot.createShapeFromSvg(markup);
  if (!icon) throw new Error(`Penpot could not import SVG layer ${name}.`);
  icon.name = name;
  icon.x = origin.x + x;
  icon.y = origin.y + y;
  icon.resize(size, size);
  parent.appendChild(icon);
  icon.setSharedPluginData(SAYHI_NAMESPACE, 'source-id', sourceId(name));
  return icon;
}

interface RectangleStyle {
  fill: string;
  stroke?: string;
  radius: number;
}

function rectangle(
  parent: Board,
  origin: Point,
  name: string,
  x: number,
  y: number,
  width: number,
  height: number,
  style: RectangleStyle,
): Rectangle {
  const shape = penpot.createRectangle();
  shape.name = name;
  shape.x = origin.x + x;
  shape.y = origin.y + y;
  shape.resize(width, height);
  shape.borderRadius = style.radius;
  shape.fills = [{ fillColor: style.fill, fillOpacity: 1 }];
  shape.strokes = style.stroke
    ? [
        {
          strokeColor: style.stroke,
          strokeOpacity: 1,
          strokeStyle: 'solid',
          strokeWidth: 1,
          strokeAlignment: 'inner',
        },
      ]
    : [];
  parent.appendChild(shape);
  shape.setSharedPluginData(SAYHI_NAMESPACE, 'source-id', sourceId(name));
  return shape;
}

function ellipse(
  parent: Board,
  origin: Point,
  name: string,
  x: number,
  y: number,
  width: number,
  height: number,
  fill: string,
  opacity: number,
): void {
  const shape = penpot.createEllipse();
  shape.name = name;
  shape.x = origin.x + x;
  shape.y = origin.y + y;
  shape.resize(width, height);
  shape.fills = [{ fillColor: fill, fillOpacity: 1 }];
  shape.opacity = opacity;
  parent.appendChild(shape);
  shape.setSharedPluginData(SAYHI_NAMESPACE, 'source-id', sourceId(name));
}

function board(
  parent: Board,
  origin: Point,
  name: string,
  x: number,
  y: number,
  width: number,
  height: number,
): Board {
  const shape = penpot.createBoard();
  shape.name = name;
  shape.x = origin.x + x;
  shape.y = origin.y + y;
  shape.resize(width, height);
  shape.clipContent = false;
  parent.appendChild(shape);
  shape.setSharedPluginData(SAYHI_NAMESPACE, 'source-id', sourceId(name));
  return shape;
}

interface TextStyle {
  size: number;
  weight: number;
  color: string;
  tracking?: number;
}

function text(
  parent: Board,
  origin: Point,
  nativeFont: NativeFont,
  name: string,
  characters: string,
  x: number,
  y: number,
  width: number,
  height: number,
  style: TextStyle,
): Text {
  const shape = penpot.createText(characters);
  if (!shape) throw new Error(`Penpot could not create text layer ${name}.`);
  shape.name = name;
  shape.x = origin.x + x;
  shape.y = origin.y + y;
  shape.growType = 'fixed';
  shape.resize(width, height);
  parent.appendChild(shape);
  nativeFont.font.applyToText(
    shape,
    closestFontVariant(nativeFont.font, style.weight),
  );
  shape.fontSize = String(style.size);
  shape.lineHeight = String(height);
  shape.letterSpacing = String(style.tracking ?? 0);
  shape.align = 'center';
  shape.verticalAlign = 'center';
  shape.fills = [{ fillColor: style.color, fillOpacity: 1 }];
  shape.setSharedPluginData(SAYHI_NAMESPACE, 'source-id', sourceId(name));
  return shape;
}

function resolveNativeFont(materialized: MaterializedPackage): NativeFont {
  const familyToken = materialized.sets
    .flatMap((set) => set.tokens)
    .find((token) => token.name === 'font.family.sans');
  const preferredFamilies = Array.isArray(familyToken?.sourceValue)
    ? familyToken.sourceValue.filter(
        (family): family is string => typeof family === 'string',
      )
    : [];

  for (const requestedFamily of preferredFamilies) {
    const font = penpot.fonts.all.find(
      (candidate) =>
        candidate.fontFamily.toLocaleLowerCase() ===
          requestedFamily.toLocaleLowerCase() ||
        candidate.name.toLocaleLowerCase() ===
          requestedFamily.toLocaleLowerCase(),
    );
    if (font) return { font };
  }

  const font = penpot.fonts.all[0];
  if (!font) {
    throw new Error(
      'Penpot has no registered fonts. Enable a font provider or install a custom font before importing.',
    );
  }
  return { font };
}

function closestFontVariant(font: Font, requestedWeight: number): FontVariant {
  const normalVariants = font.variants.filter(
    (variant) => variant.fontStyle === 'normal',
  );
  const variants = normalVariants.length ? normalVariants : font.variants;
  const fallback: FontVariant = {
    name: font.name,
    fontVariantId: font.fontVariantId,
    fontWeight: font.fontWeight,
    fontStyle: font.fontStyle === 'italic' ? 'italic' : 'normal',
  };

  return variants.reduce((closest, candidate) => {
    const closestWeight = numericFontWeight(closest.fontWeight);
    const candidateWeight = numericFontWeight(candidate.fontWeight);
    const closestDistance = Math.abs(closestWeight - requestedWeight);
    const candidateDistance = Math.abs(candidateWeight - requestedWeight);
    return candidateDistance < closestDistance ||
      (candidateDistance === closestDistance && candidateWeight > closestWeight)
      ? candidate
      : closest;
  }, variants[0] ?? fallback);
}

function numericFontWeight(value: string): number {
  const weight = Number.parseInt(value, 10);
  return Number.isFinite(weight) ? weight : 400;
}

function cloneJsonValue<T>(value: T): T {
  if (value === undefined) return value;
  return JSON.parse(JSON.stringify(value)) as T;
}

function publicTokenValue(type: string, source: unknown): unknown {
  const value = cloneJsonValue(source);
  if (type === 'fontFamilies') {
    return canonicalFontFamilyValue(value);
  }
  if (type === 'typography' && isPlainObject(value)) {
    return {
      fontFamilies: canonicalFontFamilyValue(
        value.fontFamilies ?? value.fontFamily ?? value['font-family'],
      ),
      fontSizes: value.fontSizes ?? value.fontSize ?? value['font-size'],
      fontWeight: value.fontWeight ?? value['font-weight'],
      letterSpacing: value.letterSpacing ?? value['letter-spacing'],
      lineHeight: value.lineHeight ?? value['line-height'],
      textCase: value.textCase ?? value['text-case'],
      textDecoration: value.textDecoration ?? value['text-decoration'],
    };
  }
  if (type === 'shadow' && Array.isArray(value)) {
    return value.map((entry) => {
      if (!isPlainObject(entry)) return entry;
      const inset = entry.inset;
      return {
        color: entry.color,
        inset: typeof inset === 'boolean' ? String(inset) : inset,
        offsetX: entry.offsetX ?? entry['offset-x'],
        offsetY: entry.offsetY ?? entry['offset-y'],
        spread: entry.spread,
        blur: entry.blur,
      };
    });
  }
  return value;
}

function canonicalFontFamilyValue(value: unknown): unknown {
  return Array.isArray(value) &&
    value.length === 1 &&
    typeof value[0] === 'string' &&
    /^\{[^{}]+\}$/.test(value[0])
    ? value[0]
    : value;
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value);
}

function applyBindings(root: Shape, tokensByName: Map<string, Token>): number {
  const byName = collectByName(root);
  let count = 0;
  for (const binding of VERIFY_TOKEN_BINDINGS) {
    const token = tokensByName.get(binding.token);
    if (!token) throw new Error(`Missing materialized token ${binding.token}.`);
    const targets = byName.get(binding.layer) ?? [];
    for (const target of targets) {
      target.applyToken(token, binding.properties);
      count += 1;
    }
  }
  return count;
}

function collectByName(root: Shape): Map<string, Shape[]> {
  const result = new Map<string, Shape[]>();
  visit(root, (shape) => {
    const matches = result.get(shape.name) ?? [];
    matches.push(shape);
    result.set(shape.name, matches);
  });
  return result;
}

function tagAnatomy(root: Shape): void {
  const anatomy = verifyComponentExtension().anatomy;
  const parts = anatomy.parts as Record<string, string[]>;
  const partByLayer = new Map<string, string>();
  for (const [part, layers] of Object.entries(parts)) {
    for (const layer of layers) partByLayer.set(layer, part);
  }
  visit(root, (shape) => {
    const part = partByLayer.get(shape.name);
    if (part) shape.setSharedPluginData(SAYHI_NAMESPACE, 'motion-part', part);
  });
}

function visit(root: Shape, visitor: (shape: Shape) => void): void {
  const queue = [root];
  const seen = new Set<string>();
  while (queue.length) {
    const shape = queue.shift();
    if (!shape || seen.has(shape.id)) continue;
    seen.add(shape.id);
    visitor(shape);
    if ('children' in shape) queue.push(...shape.children);
  }
}

function writeProvenance(
  target: Shape | LibraryComponent,
  canonical: string,
  fingerprint: string,
): void {
  const extension = verifyComponentExtension();
  const webObject = {
    schemaName: 'sayhi.web-object',
    schemaVersion: '1.0',
    id: extension.componentId,
    revision: extension.componentVersion,
    runtime: {
      provider: 'sayhi-studio',
      component: extension.componentId,
      story: extension.storyId,
    },
  };
  target.setSharedPluginData(
    SAYHI_NAMESPACE,
    'component-id',
    SAYHI_COMPONENT_ID,
  );
  target.setSharedPluginData(
    SAYHI_NAMESPACE,
    'component-version',
    SAYHI_COMPONENT_VERSION,
  );
  target.setSharedPluginData(SAYHI_NAMESPACE, 'story-id', extension.storyId);
  target.setSharedPluginData(SAYHI_NAMESPACE, 'projection', 'native-penpot/v2');
  target.setSharedPluginData(
    SAYHI_NAMESPACE,
    'web-object',
    JSON.stringify(webObject),
  );
  target.setSharedPluginData(SAYHI_NAMESPACE, 'import-track', IMPORT_TRACK);
  target.setSharedPluginData(SAYHI_NAMESPACE, 'dtcg-schema', '2025.10');
  target.setSharedPluginData(
    SAYHI_NAMESPACE,
    'dtcg-source-mode',
    VERIFY_TOKEN_SOURCE_MODE,
  );
  target.setSharedPluginData(SAYHI_NAMESPACE, 'dtcg-fingerprint', fingerprint);
  target.setSharedPluginData(SAYHI_NAMESPACE, 'dtcg-resolver', canonical);
  // Keep the v1 payload during the shadow period so an older importer can
  // still open this component without interpreting Resolver semantics.
  target.setSharedPluginData(
    SAYHI_NAMESPACE,
    'dtcg-package',
    legacyVerifyPackageJson(),
  );
  target.setSharedPluginData(
    SAYHI_NAMESPACE,
    'anatomy',
    JSON.stringify(extension.anatomy),
  );
  target.setSharedPluginData(
    SAYHI_NAMESPACE,
    'motion-dtcg',
    JSON.stringify({
      $schema: DTCG_2025_10_SCHEMA,
      $description: 'SayHi Verify motion program.',
      $extensions: { 'io.sayhi.motion': extension.motion },
    }),
  );
  target.setSharedPluginData(
    SAYHI_NAMESPACE,
    'runtime',
    JSON.stringify(extension.runtime),
  );
}

function findCurrentComponent(): LibraryComponent | undefined {
  const candidates = penpot.library.local.components.filter(
    (component) =>
      readData(component, 'component-id') === SAYHI_COMPONENT_ID &&
      readData(component, 'import-track') === IMPORT_TRACK,
  );
  return (
    candidates.find(
      (component) =>
        readData(component, 'component-version') === SAYHI_COMPONENT_VERSION &&
        !readData(component, 'superseded-by'),
    ) ?? candidates.find((component) => !readData(component, 'superseded-by'))
  );
}

function readData(target: Shape | LibraryComponent, key: string): string {
  try {
    return target.getSharedPluginData(SAYHI_NAMESPACE, key);
  } catch {
    return '';
  }
}

function sourceId(name: string): string {
  return `verify.${name.replace(/[^a-z0-9]+/gi, '-').toLowerCase()}`;
}

function themeKey(group: string, name: string): string {
  return `${group}\u0000${name}`;
}

function fingerprintOf(value: string): string {
  let hash = 0x811c9dc5;
  for (let index = 0; index < value.length; index += 1) {
    hash ^= value.charCodeAt(index);
    hash = Math.imul(hash, 0x01000193);
  }
  return `fnv1a32:${(hash >>> 0).toString(16).padStart(8, '0')}`;
}
