import type {
  Board,
  Fill,
  Font,
  FontVariant,
  LibraryComponent,
  Shape,
  Text,
  Token,
  TokenSet,
  TokenTheme,
  TokenValueString,
} from '@penpot/plugin-types';
import type {
  JsonObject,
  JsonValue,
  MaterializedPackage,
  MaterializedToken,
} from './dtcg-contract.js';
import {
  canonicalResolverJson,
  componentExtensionFromResolver,
  legacyPackageFromResolver,
  materializeResolver,
} from './dtcg-resolver.js';
import {
  PROJECTION_SCHEMA_NAME,
  PROJECTION_SCHEMA_VERSION,
  SAYHI_STUDIO_NAMESPACE,
  type ColorValue,
  type ComponentProjectionManifest,
  type Metric,
  type ProjectionFill,
  type ProjectionImportResult,
  type ProjectionNode,
  type ProjectionShadow,
  type ProjectionStroke,
  type ProjectionTextStyle,
} from './projection-contract.js';

const IMPORT_TRACK = 'projection-manifest/v2';

interface NativePlan {
  materialized: MaterializedPackage;
  values: TokenValueResolver;
  setPrefix: string;
}

interface ProvisionedTokens {
  byName: Map<string, Token>;
  createdSets: number;
  createdThemes: number;
  createdTokens: number;
}

interface MaterializationContext {
  manifest: ComponentProjectionManifest;
  origin: { x: number; y: number };
  values: TokenValueResolver;
  tokens: Map<string, Token>;
  font: Font;
  nodeCount: number;
  bindingCount: number;
}

export async function importProjectionManifest(
  manifest: ComponentProjectionManifest,
): Promise<ProjectionImportResult> {
  validateProjectionManifest(manifest);
  if (!penpot.currentFile || !penpot.currentPage) {
    throw new Error('Open a Penpot design file before importing a component.');
  }

  penpot.flags.naturalChildOrdering = true;
  const plan = createNativePlan(manifest);
  const fingerprint = fingerprintOf(canonicalManifestJson(manifest));
  const existing = findCurrentComponent(manifest);

  if (
    existing &&
    readData(existing, 'projection-fingerprint') === fingerprint &&
    readData(existing, 'component-version') === manifest.component.version
  ) {
    const root = existing.mainInstance();
    penpot.selection = [root];
    penpot.viewport.zoomIntoView([root]);
    return {
      componentId: manifest.component.id,
      componentVersion: manifest.component.version,
      createdNodes: 0,
      createdSets: 0,
      createdThemes: 0,
      createdTokens: 0,
      boundNodes: countLiveBindings(root),
      unsupportedTokens: plan.materialized.unsupported.length,
    };
  }

  const historyBlock = penpot.history.undoBlockBegin();
  let root: Board | null = null;
  let phase = 'provisioning DTCG tokens';
  try {
    const provisioned = provisionTokens(plan);
    phase = 'resolving the projection origin and font';
    const rootWidth = plan.values.metric(manifest.root.frame.width);
    const rootHeight = plan.values.metric(manifest.root.frame.height);
    const center = penpot.viewport.center;
    const origin = {
      x: center.x - rootWidth / 2,
      y: center.y - rootHeight / 2,
    };
    const font = resolveNativeFont(plan.values);
    const context: MaterializationContext = {
      manifest,
      origin,
      values: plan.values,
      tokens: provisioned.byName,
      font,
      nodeCount: 0,
      bindingCount: 0,
    };

    phase = `creating root node ${manifest.root.id}`;
    root = createRoot(manifest.root, context);
    await settlePenpotMutations();
    for (const child of manifest.root.children ?? []) {
      phase = `materializing node ${child.id}`;
      await materializeNode(child, root, context);
    }
    phase = `finalizing root node ${manifest.root.id}`;
    finishNode(manifest.root, root);
    writeNodeMetadata(root, manifest.root);

    if (existing) {
      existing.path = `${manifest.component.libraryPath}/Legacy`;
      existing.name = `${existing.name} · ${readData(existing, 'component-version') || 'prior'}`;
      existing.setSharedPluginData(
        SAYHI_STUDIO_NAMESPACE,
        'superseded-by',
        manifest.component.version,
      );
    }

    phase = 'writing root provenance';
    writeProvenance(root, manifest, fingerprint);
    phase = 'creating the Penpot library component';
    const component = penpot.library.local.createComponent([root]);
    if (!component) {
      throw new Error(
        `Penpot rejected library component creation (${componentDiagnostics(root)}).`,
      );
    }
    phase = 'naming the Penpot library component';
    component.name = manifest.component.name;
    component.path = manifest.component.libraryPath;
    phase = 'writing library component provenance';
    writeProvenance(component, manifest, fingerprint);
    phase = 'resolving the Penpot component main instance';
    const componentRoot = component.mainInstance();
    if (!componentRoot) {
      throw new Error('Penpot did not return the component main instance.');
    }
    phase = 'writing main-instance provenance';
    writeProvenance(componentRoot, manifest, fingerprint);
    phase = 'selecting the imported component';
    penpot.selection = [componentRoot];
    penpot.viewport.zoomIntoView([componentRoot]);

    return {
      componentId: manifest.component.id,
      componentVersion: manifest.component.version,
      createdNodes: context.nodeCount,
      createdSets: provisioned.createdSets,
      createdThemes: provisioned.createdThemes,
      createdTokens: provisioned.createdTokens,
      boundNodes: context.bindingCount,
      unsupportedTokens: plan.materialized.unsupported.length,
    };
  } catch (error) {
    root?.remove();
    const detail = error instanceof Error ? error.message : String(error);
    throw new Error(`V2 import failed while ${phase}: ${detail}`, {
      cause: error,
    });
  } finally {
    penpot.history.undoBlockFinish(historyBlock);
  }
}

function componentDiagnostics(root: Board): string {
  const descendants: Shape[] = [];
  const queue: Shape[] = [root];
  while (queue.length) {
    const shape = queue.shift();
    if (!shape) continue;
    descendants.push(shape);
    if ('children' in shape) queue.push(...shape.children);
  }
  const mainInstances = descendants.filter((shape) =>
    shape.isComponentMainInstance(),
  );
  const copyInstances = descendants.filter((shape) =>
    shape.isComponentCopyInstance(),
  );
  return [
    `root=${root.id}`,
    `parent=${root.parent?.name ?? 'page'}`,
    `nodes=${descendants.length}`,
    `mainInstances=${mainInstances.map((shape) => shape.name).join('|') || 'none'}`,
    `copyInstances=${copyInstances.map((shape) => shape.name).join('|') || 'none'}`,
  ].join(', ');
}

export function canonicalManifestJson(
  manifest: ComponentProjectionManifest,
): string {
  return JSON.stringify(manifest, sortedKeysReplacer, 2);
}

export function motionDocumentFromResolver(
  manifest: ComponentProjectionManifest,
): JsonObject {
  const packageDocument = legacyPackageFromResolver(manifest.resolver);
  const activeDocument = packageDocument.sets
    .filter((set) => set.active)
    .reduce<JsonObject>(
      (result, set) => mergeJsonObjects(result, set.document),
      {},
    );
  const extension = componentExtensionFromResolver(manifest.resolver);
  const extensions = isJsonObject(activeDocument.$extensions)
    ? activeDocument.$extensions
    : {};
  return {
    ...activeDocument,
    $description: `Portable motion document for ${manifest.component.name}.`,
    $extensions: {
      ...extensions,
      'io.sayhi.motion': extension.motion,
    },
  };
}

export function validateProjectionManifest(
  manifest: ComponentProjectionManifest,
): void {
  if (manifest.schemaName !== PROJECTION_SCHEMA_NAME) {
    throw new Error(`Unsupported projection schema ${manifest.schemaName}.`);
  }
  if (manifest.schemaVersion !== PROJECTION_SCHEMA_VERSION) {
    throw new Error(
      `Unsupported projection version ${manifest.schemaVersion}.`,
    );
  }
  if (!manifest.component.id || !manifest.component.version) {
    throw new Error('A component projection requires an ID and version.');
  }
  if (manifest.root.type !== 'board') {
    throw new Error('A component projection root must be a board.');
  }
  materializeResolver(manifest.resolver);
  const ids = new Set<string>();
  visitManifest(manifest.root, (node) => {
    if (!node.id) throw new Error('Every projection node requires an ID.');
    if (ids.has(node.id))
      throw new Error(`Duplicate projection node ${node.id}.`);
    ids.add(node.id);
    for (const value of Object.values(node.frame))
      validateMetric(value, node.id);
  });
}

export function projectionCoverage(manifest: ComponentProjectionManifest): {
  nodes: number;
  sets: number;
  bindings: number;
} {
  const materialized = materializeResolver(manifest.resolver);
  let nodes = 0;
  let bindings = 0;
  visitManifest(manifest.root, (node) => {
    nodes += 1;
    bindings += node.tokenBindings?.length ?? 0;
  });
  return { nodes, sets: materialized.sets.length, bindings };
}

function createNativePlan(manifest: ComponentProjectionManifest): NativePlan {
  const materialized = materializeResolver(manifest.resolver);
  const setPrefix = readNativeSetPrefix(manifest);
  return {
    materialized,
    values: new TokenValueResolver(materialized),
    setPrefix,
  };
}

class TokenValueResolver {
  readonly #tokens = new Map<string, MaterializedToken>();

  constructor(materialized: MaterializedPackage) {
    for (const set of materialized.sets.filter(
      (candidate) => candidate.active,
    )) {
      for (const token of set.tokens) this.#tokens.set(token.name, token);
    }
    for (const token of materialized.unsupported) {
      const set = materialized.sets.find(
        (candidate) => candidate.name === token.setName,
      );
      if (set?.active) this.#tokens.set(token.name, token);
    }
  }

  raw(value: Metric | ColorValue | string | number): JsonValue {
    if (isTokenReference(value)) return this.token(value.token);
    return value;
  }

  token(name: string, stack: string[] = []): JsonValue {
    if (stack.includes(name)) {
      throw new Error(`DTCG alias cycle: ${[...stack, name].join(' → ')}.`);
    }
    const token = this.#tokens.get(name);
    if (!token) throw new Error(`Unknown DTCG token ${name}.`);
    const value = token.sourceValue;
    if (typeof value === 'string') {
      const alias = aliasName(value);
      if (alias) return this.token(alias, [...stack, name]);
    }
    return resolveCompositeAliases(value, (alias) =>
      this.token(alias, [...stack, name]),
    );
  }

  metric(value: Metric): number {
    return numericValue(this.raw(value));
  }

  color(value: ColorValue): string {
    return colorValue(this.raw(value));
  }

  family(value: string | { token: string }): string[] {
    const raw = this.raw(value);
    if (typeof raw === 'string') return [raw];
    if (Array.isArray(raw) && raw.every((entry) => typeof entry === 'string')) {
      return [...raw] as string[];
    }
    throw new Error(
      `Expected a font-family token, received ${JSON.stringify(raw)}.`,
    );
  }

  weight(value: number | string | { token: string }): number {
    const raw = this.raw(value);
    const numeric = typeof raw === 'number' ? raw : Number(raw);
    if (!Number.isFinite(numeric)) {
      throw new Error(
        `Expected a numeric font weight, received ${JSON.stringify(raw)}.`,
      );
    }
    return numeric;
  }
}

function provisionTokens(plan: NativePlan): ProvisionedTokens {
  const catalog = penpot.library.local.tokens;
  const setsByName = new Map(catalog.sets.map((set) => [set.name, set]));
  const byName = new Map<string, Token>();
  let createdSets = 0;
  let createdThemes = 0;
  let createdTokens = 0;

  for (const setPlan of plan.materialized.sets) {
    const name = nativeSetName(plan.setPrefix, setPlan.name);
    let set = setsByName.get(name);
    if (!set) {
      set = catalog.addSet({ name, active: setPlan.active });
      setsByName.set(name, set);
      createdSets += 1;
    } else {
      set.active = setPlan.active;
    }
    for (const tokenPlan of setPlan.tokens) {
      if (!tokenPlan.penpotType || tokenPlan.value === undefined) continue;
      const existing = set.tokens.find(
        (token) => token.name === tokenPlan.name,
      );
      if (existing && existing.type !== tokenPlan.penpotType) {
        throw new Error(
          `Token ${tokenPlan.name} already exists as ${existing.type}; expected ${tokenPlan.penpotType}.`,
        );
      }
      const token =
        existing ??
        set.addToken({
          type: tokenPlan.penpotType,
          name: tokenPlan.name,
          value: tokenPlan.value as TokenValueString,
        });
      if (!token) throw new Error(`Penpot rejected token ${tokenPlan.name}.`);
      if (existing) {
        (existing as unknown as { value: unknown }).value = tokenPlan.value;
      } else {
        createdTokens += 1;
      }
      token.description = tokenPlan.description ?? '';
      byName.set(tokenPlan.name, token);
    }
  }

  const themesByKey = new Map(
    catalog.themes.map((theme) => [themeKey(theme.group, theme.name), theme]),
  );
  for (const themePlan of plan.materialized.themes) {
    const group = `${plan.setPrefix} / ${themePlan.group}`;
    const key = themeKey(group, themePlan.name);
    let theme = themesByKey.get(key);
    if (!theme) {
      theme = catalog.addTheme({ group, name: themePlan.name });
      themesByKey.set(key, theme);
      createdThemes += 1;
    }
    syncTheme(theme, themePlan.sets, plan.setPrefix, setsByName);
  }
  for (const themePlan of plan.materialized.themes) {
    const group = `${plan.setPrefix} / ${themePlan.group}`;
    const theme = themesByKey.get(themeKey(group, themePlan.name));
    if (theme && theme.active !== themePlan.active) theme.toggleActive();
  }

  return { byName, createdSets, createdThemes, createdTokens };
}

function syncTheme(
  theme: TokenTheme,
  desiredSourceNames: string[],
  prefix: string,
  setsByName: Map<string, TokenSet>,
): void {
  const desiredNames = new Set(
    desiredSourceNames.map((name) => nativeSetName(prefix, name)),
  );
  for (const current of theme.activeSets) {
    if (!desiredNames.has(current.name)) theme.removeSet(current);
  }
  const activeNames = new Set(theme.activeSets.map((set) => set.name));
  for (const name of desiredNames) {
    if (activeNames.has(name)) continue;
    const set = setsByName.get(name);
    if (!set)
      throw new Error(`Theme ${theme.name} references missing set ${name}.`);
    theme.addSet(set);
  }
}

function createRoot(
  node: ComponentProjectionManifest['root'],
  context: MaterializationContext,
): Board {
  const root = penpot.createBoard();
  root.name = node.name;
  root.x = context.origin.x;
  root.y = context.origin.y;
  root.resize(
    context.values.metric(node.frame.width),
    context.values.metric(node.frame.height),
  );
  assertMaterializedSize(
    root,
    context.values.metric(node.frame.width),
    context.values.metric(node.frame.height),
    node.id,
  );
  applyBoardStyle(root, node, context.values);
  context.nodeCount += 1;
  return root;
}

async function materializeNode(
  node: ProjectionNode,
  parent: Board,
  context: MaterializationContext,
): Promise<Shape> {
  const shape = createShape(node, context);
  parent.appendChild(shape);
  await settlePenpotMutations();
  if (shape.parent?.id !== parent.id) {
    throw new Error(
      `Penpot did not attach node ${node.id} to ${parent.name}; ` +
        `actual parent is ${shape.parent?.name ?? 'none'}.`,
    );
  }
  applyNodeStyle(shape, node, context);
  if ('children' in shape) {
    for (const child of node.children ?? []) {
      await materializeNode(child, shape as Board, context);
    }
  } else if (node.children?.length) {
    throw new Error(`Projection node ${node.id} cannot contain children.`);
  }
  finishNode(node, shape);
  writeNodeMetadata(shape, node);
  context.nodeCount += 1;
  return shape;
}

function settlePenpotMutations(): Promise<void> {
  return new Promise((resolvePromise) => setTimeout(resolvePromise, 0));
}

function createShape(
  node: ProjectionNode,
  context: MaterializationContext,
): Shape {
  const frame = resolveFrame(node, context);
  let shape: Shape | null;
  if (node.type === 'board') shape = penpot.createBoard();
  else if (node.type === 'rectangle') shape = penpot.createRectangle();
  else if (node.type === 'ellipse') shape = penpot.createEllipse();
  else if (node.type === 'text') shape = penpot.createText(node.characters);
  else shape = penpot.createShapeFromSvg(node.markup);
  if (!shape)
    throw new Error(`Penpot could not create ${node.type} node ${node.id}.`);
  shape.name = node.name;
  shape.x = frame.x;
  shape.y = frame.y;
  shape.resize(frame.width, frame.height);
  assertMaterializedSize(shape, frame.width, frame.height, node.id);
  return shape;
}

function assertMaterializedSize(
  shape: Shape,
  expectedWidth: number,
  expectedHeight: number,
  nodeId: string,
): void {
  const tolerance = 0.01;
  if (
    Math.abs(shape.width - expectedWidth) <= tolerance &&
    Math.abs(shape.height - expectedHeight) <= tolerance
  ) {
    return;
  }
  throw new Error(
    `Penpot resized ${nodeId} to ${shape.width}×${shape.height}; ` +
      `the projection requires ${expectedWidth}×${expectedHeight}.`,
  );
}

function resolveFrame(
  node: ProjectionNode,
  context: MaterializationContext,
): { x: number; y: number; width: number; height: number } {
  return {
    x: context.origin.x + context.values.metric(node.frame.x),
    y: context.origin.y + context.values.metric(node.frame.y),
    width: context.values.metric(node.frame.width),
    height: context.values.metric(node.frame.height),
  };
}

function applyNodeStyle(
  shape: Shape,
  node: ProjectionNode,
  context: MaterializationContext,
): void {
  if (node.opacity !== undefined) shape.opacity = node.opacity;
  if (node.hidden !== undefined) shape.hidden = node.hidden;

  if (node.type === 'board')
    applyBoardStyle(shape as Board, node, context.values);
  if (node.type === 'rectangle') {
    shape.borderRadius = context.values.metric(node.radius ?? 0);
    shape.fills = materializeFills(node.fills ?? [], context.values);
    shape.strokes = materializeStrokes(node.strokes ?? [], context.values);
    shape.shadows = materializeShadows(node.shadows ?? [], context.values);
  }
  if (node.type === 'ellipse') {
    shape.fills = materializeFills(node.fills ?? [], context.values);
  }
  if (node.type === 'text') {
    applyTextStyle(shape as Text, node.style, context);
  }

  for (const binding of node.tokenBindings ?? []) {
    const token = context.tokens.get(binding.token);
    if (!token) throw new Error(`Missing native token ${binding.token}.`);
    shape.applyToken(token, binding.properties);
    context.bindingCount += 1;
  }
}

function applyBoardStyle(
  board: Board,
  node:
    | ComponentProjectionManifest['root']
    | Extract<ProjectionNode, { type: 'board' }>,
  values: TokenValueResolver,
): void {
  board.clipContent = node.clipContent ?? false;
  if (node.showInViewMode !== undefined)
    board.showInViewMode = node.showInViewMode;
  board.fills = materializeFills(node.fills ?? [], values);
}

function applyTextStyle(
  text: Text,
  style: ProjectionTextStyle,
  context: MaterializationContext,
): void {
  const families = context.values.family(style.fontFamily);
  const font = findFont(families) ?? context.font;
  const weight = context.values.weight(style.fontWeight);
  font.applyToText(text, closestFontVariant(font, weight));
  text.growType = 'fixed';
  text.fontSize = String(context.values.metric(style.fontSize));
  text.lineHeight = String(style.lineHeight);
  text.letterSpacing = String(context.values.metric(style.letterSpacing));
  text.align = style.align ?? 'center';
  text.verticalAlign = style.verticalAlign ?? 'center';
  if (style.textTransform && style.textTransform !== 'none') {
    text.textTransform = style.textTransform;
  }
  text.fills = [
    { fillColor: context.values.color(style.color), fillOpacity: 1 },
  ];
}

function finishNode(node: ProjectionNode, shape: Shape): void {
  if (node.rotation !== undefined) shape.rotation = node.rotation;
}

function writeNodeMetadata(shape: Shape, node: ProjectionNode): void {
  shape.setSharedPluginData(SAYHI_STUDIO_NAMESPACE, 'source-id', node.id);
  if (node.motionPart) {
    shape.setSharedPluginData(
      SAYHI_STUDIO_NAMESPACE,
      'motion-part',
      node.motionPart,
    );
  }
}

function materializeFills(
  fills: ProjectionFill[],
  values: TokenValueResolver,
): Fill[] {
  return fills.map((fill) => {
    if (fill.gradient) {
      return {
        fillColorGradient: {
          type: 'linear' as const,
          startX: 0,
          startY: 0,
          endX: 1,
          endY: 1,
          width: 1,
          stops: fill.gradient.stops.map((stop) => ({
            color: stop.color,
            opacity: stop.opacity ?? 1,
            offset: stop.offset,
          })),
        },
      };
    }
    return {
      fillColor: values.color(fill.color ?? '#000000'),
      fillOpacity: fill.opacity ?? 1,
    };
  });
}

function materializeStrokes(
  strokes: ProjectionStroke[],
  values: TokenValueResolver,
): Shape['strokes'] {
  return strokes.map((stroke) => ({
    strokeColor: values.color(stroke.color),
    strokeOpacity: stroke.opacity ?? 1,
    strokeStyle: 'solid' as const,
    strokeWidth: values.metric(stroke.width),
    strokeAlignment: stroke.alignment ?? 'center',
  }));
}

function materializeShadows(
  shadows: ProjectionShadow[],
  values: TokenValueResolver,
): Shape['shadows'] {
  return shadows.map((shadow) => ({
    style: 'drop-shadow' as const,
    offsetX: values.metric(shadow.offsetX),
    offsetY: values.metric(shadow.offsetY),
    blur: values.metric(shadow.blur),
    spread: values.metric(shadow.spread),
    color: { color: shadow.color, opacity: shadow.opacity },
  }));
}

function resolveNativeFont(values: TokenValueResolver): Font {
  const preferred = values.family({ token: 'verify.font.family' });
  const font = findFont(preferred) ?? penpot.fonts.all[0];
  if (!font) throw new Error('Penpot has no registered fonts.');
  return font;
}

function findFont(families: string[]): Font | undefined {
  for (const family of families) {
    const match = penpot.fonts.all.find(
      (candidate) =>
        candidate.fontFamily.toLocaleLowerCase() ===
          family.toLocaleLowerCase() ||
        candidate.name.toLocaleLowerCase() === family.toLocaleLowerCase(),
    );
    if (match) return match;
  }
  return undefined;
}

function closestFontVariant(font: Font, requestedWeight: number): FontVariant {
  const normal = font.variants.filter(
    (variant) => variant.fontStyle === 'normal',
  );
  const variants = normal.length ? normal : font.variants;
  const fallback: FontVariant = {
    name: font.name,
    fontVariantId: font.fontVariantId,
    fontWeight: font.fontWeight,
    fontStyle: font.fontStyle === 'italic' ? 'italic' : 'normal',
  };
  return variants.reduce((closest, candidate) => {
    const current = Number.parseInt(closest.fontWeight, 10) || 400;
    const next = Number.parseInt(candidate.fontWeight, 10) || 400;
    return Math.abs(next - requestedWeight) <
      Math.abs(current - requestedWeight)
      ? candidate
      : closest;
  }, variants[0] ?? fallback);
}

function writeProvenance(
  target: Shape | LibraryComponent,
  manifest: ComponentProjectionManifest,
  fingerprint: string,
): void {
  const extension = componentExtensionFromResolver(manifest.resolver);
  const runtime = extension.runtime;
  const anatomy = extension.anatomy;
  const webObject = {
    schemaName: 'sayhi.web-object',
    schemaVersion: '1.0',
    id: manifest.component.id,
    revision: manifest.component.version,
    runtime: {
      ...runtime,
      provider: 'sayhi-studio',
      component: manifest.component.id,
      story: manifest.component.storyId ?? '',
    },
  };
  target.setSharedPluginData(
    SAYHI_STUDIO_NAMESPACE,
    'component-id',
    manifest.component.id,
  );
  target.setSharedPluginData(
    SAYHI_STUDIO_NAMESPACE,
    'component-version',
    manifest.component.version,
  );
  target.setSharedPluginData(
    SAYHI_STUDIO_NAMESPACE,
    'story-id',
    manifest.component.storyId ?? '',
  );
  target.setSharedPluginData(
    SAYHI_STUDIO_NAMESPACE,
    'projection',
    'manifest-native/v2',
  );
  target.setSharedPluginData(
    SAYHI_STUDIO_NAMESPACE,
    'import-track',
    IMPORT_TRACK,
  );
  target.setSharedPluginData(
    SAYHI_STUDIO_NAMESPACE,
    'projection-fingerprint',
    fingerprint,
  );
  target.setSharedPluginData(
    SAYHI_STUDIO_NAMESPACE,
    'component-projection',
    canonicalManifestJson(manifest),
  );
  target.setSharedPluginData(
    SAYHI_STUDIO_NAMESPACE,
    'dtcg-resolver',
    canonicalResolverJson(manifest.resolver),
  );
  target.setSharedPluginData(
    SAYHI_STUDIO_NAMESPACE,
    'dtcg-package',
    JSON.stringify(
      legacyPackageFromResolver(manifest.resolver),
      sortedKeysReplacer,
      2,
    ),
  );
  target.setSharedPluginData(
    SAYHI_STUDIO_NAMESPACE,
    'anatomy',
    JSON.stringify(anatomy),
  );
  target.setSharedPluginData(
    SAYHI_STUDIO_NAMESPACE,
    'motion-dtcg',
    JSON.stringify(motionDocumentFromResolver(manifest), sortedKeysReplacer, 2),
  );
  target.setSharedPluginData(
    SAYHI_STUDIO_NAMESPACE,
    'runtime',
    JSON.stringify(runtime),
  );
  target.setSharedPluginData(
    SAYHI_STUDIO_NAMESPACE,
    'web-object',
    JSON.stringify(webObject),
  );
}

function mergeJsonObjects(left: JsonObject, right: JsonObject): JsonObject {
  const result: JsonObject = { ...left };
  for (const [key, value] of Object.entries(right)) {
    const current = result[key];
    result[key] =
      isJsonObject(current) && isJsonObject(value)
        ? mergeJsonObjects(current, value)
        : structuredClone(value);
  }
  return result;
}

function isJsonObject(value: unknown): value is JsonObject {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value);
}

function findCurrentComponent(
  manifest: ComponentProjectionManifest,
): LibraryComponent | undefined {
  return penpot.library.local.components.find(
    (component) =>
      readData(component, 'component-id') === manifest.component.id &&
      readData(component, 'import-track') === IMPORT_TRACK &&
      !readData(component, 'superseded-by'),
  );
}

function countLiveBindings(root: Shape): number {
  let count = 0;
  const queue = [root];
  while (queue.length) {
    const shape = queue.shift();
    if (!shape) continue;
    count += new Set(Object.values(shape.tokens)).size;
    if ('children' in shape) queue.push(...shape.children);
  }
  return count;
}

function readData(target: Shape | LibraryComponent, key: string): string {
  try {
    return target.getSharedPluginData(SAYHI_STUDIO_NAMESPACE, key);
  } catch {
    return '';
  }
}

function readNativeSetPrefix(manifest: ComponentProjectionManifest): string {
  const extension =
    manifest.resolver.sets?.Components?.$extensions?.['io.sayhi.penpot'];
  if (isObject(extension) && typeof extension.nativeSetPrefix === 'string') {
    return extension.nativeSetPrefix;
  }
  return 'Projection V2';
}

function nativeSetName(prefix: string, sourceName: string): string {
  return `${prefix}/${sourceName}`;
}

function visitManifest(
  node: ProjectionNode,
  visitor: (node: ProjectionNode) => void,
): void {
  visitor(node);
  for (const child of node.children ?? []) visitManifest(child, visitor);
}

function validateMetric(value: Metric, nodeId: string): void {
  if (typeof value === 'number' && Number.isFinite(value)) return;
  if (isTokenReference(value) && value.token) return;
  throw new Error(`Projection node ${nodeId} has an invalid metric.`);
}

function isTokenReference(value: unknown): value is { token: string } {
  return isObject(value) && typeof value.token === 'string';
}

function isObject(value: unknown): value is JsonObject {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value);
}

function aliasName(value: string): string | undefined {
  return value.match(/^\{([^{}]+)\}$/)?.[1];
}

function resolveCompositeAliases(
  value: JsonValue,
  resolve: (name: string) => JsonValue,
): JsonValue {
  if (typeof value === 'string') {
    const alias = aliasName(value);
    return alias ? resolve(alias) : value;
  }
  if (Array.isArray(value)) {
    return value.map((entry) => resolveCompositeAliases(entry, resolve));
  }
  if (isObject(value)) {
    return Object.fromEntries(
      Object.entries(value).map(([key, entry]) => [
        key,
        entry === undefined ? null : resolveCompositeAliases(entry, resolve),
      ]),
    );
  }
  return value;
}

function numericValue(value: JsonValue): number {
  if (typeof value === 'number' && Number.isFinite(value)) return value;
  if (
    isObject(value) &&
    typeof value.value === 'number' &&
    Number.isFinite(value.value)
  ) {
    if (value.unit === 'rem') return value.value * 16;
    return value.value;
  }
  const numeric = typeof value === 'string' ? Number.parseFloat(value) : NaN;
  if (Number.isFinite(numeric)) return numeric;
  throw new Error(
    `Expected a numeric DTCG value, received ${JSON.stringify(value)}.`,
  );
}

function colorValue(value: JsonValue): string {
  if (typeof value === 'string' && /^#[0-9a-f]{6,8}$/i.test(value))
    return value;
  if (isObject(value) && typeof value.hex === 'string') return value.hex;
  if (
    isObject(value) &&
    value.colorSpace === 'srgb' &&
    Array.isArray(value.components) &&
    value.components.length >= 3
  ) {
    return `#${value.components
      .slice(0, 3)
      .map((entry) =>
        Math.round(Number(entry) * 255)
          .toString(16)
          .padStart(2, '0'),
      )
      .join('')}`;
  }
  throw new Error(`Expected a DTCG color, received ${JSON.stringify(value)}.`);
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

function sortedKeysReplacer(_key: string, value: unknown): unknown {
  if (!isObject(value)) return value;
  return Object.fromEntries(
    Object.entries(value).sort(([left], [right]) => left.localeCompare(right)),
  );
}
