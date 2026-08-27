import {
  DTCG_RESOLVER_2025_10_SCHEMA,
  type DtcgDocument,
  type DtcgReferenceObject,
  type DtcgResolverDocument,
  type DtcgResolverInput,
  type DtcgResolverModifier,
  type DtcgResolverSet,
  type DtcgResolverSource,
  type JsonObject,
  type JsonValue,
  type MaterializedPackage,
  type MaterializedToken,
  type NativeSetSnapshot,
  type NativeThemeSnapshot,
  type SayHiComponentExtension,
  type SayHiDtcgPackage,
  type TokenThemeContract,
} from './dtcg-contract.js';
import {
  materializePackage,
  roundTripPackage,
  validateDtcgDocument,
} from './dtcg.js';

const COMPONENT_EXTENSION = 'io.sayhi.component';
const PENPOT_EXTENSION = 'io.sayhi.penpot';

interface ProjectionOrigin {
  kind: 'set' | 'modifier';
  name: string;
  context?: string;
}

interface ProjectionEntry {
  kind: 'set' | 'modifier';
  name: string;
  nativeName?: string;
  contextSets?: Map<string, string>;
}

interface ResolverProjection {
  legacy: SayHiDtcgPackage;
  origins: Map<string, ProjectionOrigin>;
  themeInputs: Map<string, DtcgResolverInput>;
}

export function validateDtcgResolver(
  source: DtcgResolverDocument,
  input?: DtcgResolverInput,
): void {
  projectResolver(source, input);
}

export function materializeResolver(
  source: DtcgResolverDocument,
  input?: DtcgResolverInput,
): MaterializedPackage {
  const materialized = materializePackage(
    projectResolver(source, input).legacy,
  );
  validateAliasGraph(materialized);
  return materialized;
}

export function legacyPackageFromResolver(
  source: DtcgResolverDocument,
  input?: DtcgResolverInput,
): SayHiDtcgPackage {
  return projectResolver(source, input).legacy;
}

export function componentExtensionFromResolver(
  source: DtcgResolverDocument,
): SayHiComponentExtension {
  const extensions = Object.values(source.sets ?? {})
    .map((set) => set.$extensions?.[COMPONENT_EXTENSION])
    .filter(isObject);
  if (extensions.length !== 1) {
    throw new Error(
      `Expected exactly one ${COMPONENT_EXTENSION} resolver set extension, found ${extensions.length}.`,
    );
  }
  return cloneJson(extensions[0]) as unknown as SayHiComponentExtension;
}

export function canonicalResolverJson(source: DtcgResolverDocument): string {
  return JSON.stringify(source, sortedKeysReplacer, 2);
}

export function roundTripResolver(
  source: DtcgResolverDocument,
  nativeSets: NativeSetSnapshot[],
  nativeThemes: NativeThemeSnapshot[],
): DtcgResolverDocument {
  const projection = projectResolver(source);
  const editedLegacy = roundTripPackage(
    projection.legacy,
    nativeSets,
    nativeThemes,
  );
  const result = cloneJson(
    source as unknown as JsonValue,
  ) as unknown as DtcgResolverDocument;

  for (const set of editedLegacy.sets) {
    const origin = projection.origins.get(set.name);
    if (!origin) continue;
    const sources =
      origin.kind === 'set'
        ? result.sets?.[origin.name]?.sources
        : result.modifiers?.[origin.name]?.contexts[origin.context ?? ''];
    if (!sources) continue;
    replaceSingleInlineSource(sources, set.document, set.name);
  }

  const activeTheme = nativeThemes.find((theme) => theme.active);
  const activeInputs = activeTheme
    ? projection.themeInputs.get(themeKey(activeTheme.group, activeTheme.name))
    : undefined;
  if (activeInputs) {
    for (const [modifierName, context] of Object.entries(activeInputs)) {
      const modifier = result.modifiers?.[modifierName];
      if (modifier) modifier.default = context;
    }
  }

  validateDtcgResolver(result);
  return result;
}

function projectResolver(
  source: DtcgResolverDocument,
  input?: DtcgResolverInput,
): ResolverProjection {
  validateResolverHeader(source);
  const selectedInput = normalizeInput(source, input);
  const origins = new Map<string, ProjectionOrigin>();
  const entries: ProjectionEntry[] = [];
  const sets: SayHiDtcgPackage['sets'] = [];

  for (const reference of source.resolutionOrder) {
    const target = resolverOrderTarget(reference);
    if (target.kind === 'set') {
      const set = source.sets?.[target.name];
      if (!set) throw new Error(`Unknown resolver set ${target.name}.`);
      const nativeName = nativeSetName(target.name, set);
      sets.push({
        name: nativeName,
        active: true,
        document: mergeSources(source, set.sources, [target.name]),
      });
      origins.set(nativeName, { kind: 'set', name: target.name });
      entries.push({ kind: 'set', name: target.name, nativeName });
      continue;
    }

    const modifier = source.modifiers?.[target.name];
    if (!modifier) throw new Error(`Unknown resolver modifier ${target.name}.`);
    validateModifier(target.name, modifier);
    const contextSets = new Map<string, string>();
    const activeContext = selectedInput[target.name];
    for (const [context, contextSources] of Object.entries(modifier.contexts)) {
      const nativeName = nativeContextSetName(target.name, context, modifier);
      contextSets.set(context, nativeName);
      sets.push({
        name: nativeName,
        active: context === activeContext,
        document: mergeSources(source, contextSources, [
          `${target.name}:${context}`,
        ]),
      });
      origins.set(nativeName, {
        kind: 'modifier',
        name: target.name,
        context,
      });
    }
    entries.push({ kind: 'modifier', name: target.name, contextSets });
  }

  const { themes, inputsByTheme } = projectThemes(
    source,
    entries,
    selectedInput,
  );
  const extension = componentExtensionFromResolver(source);
  const legacy: SayHiDtcgPackage = {
    schemaName: 'io.sayhi.dtcg-package',
    schemaVersion: '0.1.0',
    sets,
    themes,
    $extensions: { [COMPONENT_EXTENSION]: extension },
  };

  return { legacy, origins, themeInputs: inputsByTheme };
}

function validateResolverHeader(source: DtcgResolverDocument): void {
  if (
    source.$schema !== undefined &&
    source.$schema !== DTCG_RESOLVER_2025_10_SCHEMA
  ) {
    throw new Error(
      `Expected DTCG Resolver 2025.10 schema, received ${source.$schema}.`,
    );
  }
  if (source.version !== '2025.10') {
    throw new Error(
      `Expected DTCG Resolver version 2025.10, received ${source.version}.`,
    );
  }
  if (!Array.isArray(source.resolutionOrder)) {
    throw new Error('A DTCG Resolver requires resolutionOrder.');
  }
  for (const [name, set] of Object.entries(source.sets ?? {})) {
    if (!Array.isArray(set.sources)) {
      throw new Error(`Resolver set ${name} requires a sources array.`);
    }
  }
  for (const [name, modifier] of Object.entries(source.modifiers ?? {})) {
    validateModifier(name, modifier);
  }
}

function validateModifier(name: string, modifier: DtcgResolverModifier): void {
  const contexts = Object.keys(modifier.contexts ?? {});
  if (contexts.length < 2) {
    throw new Error(
      `Resolver modifier ${name} requires at least two contexts.`,
    );
  }
  if (modifier.default && !contexts.includes(modifier.default)) {
    throw new Error(
      `Resolver modifier ${name} has unknown default ${modifier.default}.`,
    );
  }
}

function normalizeInput(
  source: DtcgResolverDocument,
  input?: DtcgResolverInput,
): DtcgResolverInput {
  const result: DtcgResolverInput = {};
  const modifiers = source.modifiers ?? {};
  for (const key of Object.keys(input ?? {})) {
    if (!Object.hasOwn(modifiers, key)) {
      throw new Error(`Unknown resolver input ${key}.`);
    }
    if (typeof input?.[key] !== 'string') {
      throw new Error(`Resolver input ${key} must be a string.`);
    }
  }
  for (const [name, modifier] of Object.entries(modifiers)) {
    const requested = input?.[name] ?? modifier.default;
    if (!requested) {
      throw new Error(`Missing resolver input ${name}.`);
    }
    const actual = Object.keys(modifier.contexts).find(
      (context) => context.toLowerCase() === requested.toLowerCase(),
    );
    if (!actual) {
      throw new Error(
        `Invalid context ${requested} for resolver input ${name}.`,
      );
    }
    result[name] = actual;
  }
  return result;
}

function resolverOrderTarget(reference: DtcgReferenceObject): {
  kind: 'set' | 'modifier';
  name: string;
} {
  if (!isReference(reference)) {
    throw new Error(
      'The SayHi Penpot projection currently requires referenced resolutionOrder entries.',
    );
  }
  const match = reference.$ref.match(/^#\/(sets|modifiers)\/([^/]+)$/);
  if (!match) {
    throw new Error(`Unsupported resolutionOrder reference ${reference.$ref}.`);
  }
  return {
    kind: match[1] === 'sets' ? 'set' : 'modifier',
    name: decodePointerSegment(match[2]),
  };
}

function mergeSources(
  resolver: DtcgResolverDocument,
  sources: DtcgResolverSource[],
  stack: string[],
): DtcgDocument {
  let result: JsonObject = {};
  for (const source of sources) {
    if (isReference(source)) {
      const target = source.$ref.match(/^#\/sets\/([^/]+)$/);
      if (!target) {
        throw new Error(
          `The Penpot projection cannot load external token source ${source.$ref}.`,
        );
      }
      const name = decodePointerSegment(target[1]);
      if (stack.includes(name)) {
        throw new Error(
          `Circular resolver set reference: ${[...stack, name].join(' -> ')}.`,
        );
      }
      const set = resolver.sets?.[name];
      if (!set) throw new Error(`Unknown referenced resolver set ${name}.`);
      result = mergeObjects(
        result,
        mergeSources(resolver, set.sources, [...stack, name]),
      );
      continue;
    }
    validateDtcgDocument(source);
    result = mergeObjects(result, source);
  }
  const document = result as DtcgDocument;
  validateDtcgDocument(document);
  return document;
}

function projectThemes(
  source: DtcgResolverDocument,
  entries: ProjectionEntry[],
  selectedInput: DtcgResolverInput,
): {
  themes: TokenThemeContract[];
  inputsByTheme: Map<string, DtcgResolverInput>;
} {
  const modifiers = entries.filter(
    (entry): entry is ProjectionEntry & { contextSets: Map<string, string> } =>
      entry.kind === 'modifier' && Boolean(entry.contextSets),
  );
  if (!modifiers.length) return { themes: [], inputsByTheme: new Map() };

  const permutations = modifierPermutations(source, modifiers);
  const themes: TokenThemeContract[] = [];
  const inputsByTheme = new Map<string, DtcgResolverInput>();
  for (const inputs of permutations) {
    const group =
      modifiers.length === 1
        ? nativeThemeGroup(
            modifiers[0].name,
            source.modifiers![modifiers[0].name],
          )
        : source.name || 'Resolver';
    const name =
      modifiers.length === 1
        ? humanize(inputs[modifiers[0].name])
        : modifiers
            .map(
              (modifier) =>
                `${humanize(modifier.name)}: ${humanize(inputs[modifier.name])}`,
            )
            .join(', ');
    const sets = entries.map((entry) => {
      if (entry.kind === 'set') return entry.nativeName!;
      return entry.contextSets!.get(inputs[entry.name])!;
    });
    const active = Object.entries(inputs).every(
      ([modifier, context]) => selectedInput[modifier] === context,
    );
    themes.push({ group, name, sets, active });
    inputsByTheme.set(themeKey(group, name), inputs);
  }
  return { themes, inputsByTheme };
}

function modifierPermutations(
  source: DtcgResolverDocument,
  modifiers: ProjectionEntry[],
): DtcgResolverInput[] {
  let results: DtcgResolverInput[] = [{}];
  for (const entry of modifiers) {
    const contexts = Object.keys(source.modifiers![entry.name].contexts);
    results = results.flatMap((existing) =>
      contexts.map((context) => ({ ...existing, [entry.name]: context })),
    );
  }
  return results;
}

function validateAliasGraph(materialized: MaterializedPackage): void {
  const tokens = [
    ...materialized.sets.flatMap((set) => set.tokens),
    ...materialized.unsupported,
  ];
  const byName = new Map<string, MaterializedToken>();
  const edges = new Map<string, Set<string>>();

  for (const token of tokens) {
    const previous = byName.get(token.name);
    if (previous && previous.dtcgType !== token.dtcgType) {
      throw new Error(
        `DTCG token ${token.name} changes type from ${previous.dtcgType} to ${token.dtcgType} across resolver contexts.`,
      );
    }
    byName.set(token.name, previous ?? token);
    const references = edges.get(token.name) ?? new Set<string>();
    for (const reference of referencesIn(token.sourceValue)) {
      references.add(reference);
    }
    edges.set(token.name, references);
  }

  for (const token of tokens) {
    if (typeof token.sourceValue !== 'string') continue;
    const reference = wholeAlias(token.sourceValue);
    if (!reference) continue;
    const target = byName.get(reference);
    if (target && target.dtcgType !== token.dtcgType) {
      throw new Error(
        `DTCG alias ${token.name} (${token.dtcgType}) targets ${reference} (${target.dtcgType}).`,
      );
    }
  }

  const visiting = new Set<string>();
  const visited = new Set<string>();
  const visit = (name: string, path: string[]): void => {
    if (visiting.has(name)) {
      const start = path.indexOf(name);
      throw new Error(
        `Circular DTCG alias: ${[...path.slice(start), name].join(' -> ')}.`,
      );
    }
    if (visited.has(name)) return;
    visiting.add(name);
    for (const target of edges.get(name) ?? []) visit(target, [...path, name]);
    visiting.delete(name);
    visited.add(name);
  };
  for (const name of edges.keys()) visit(name, []);
}

function nativeSetName(name: string, set: DtcgResolverSet): string {
  return penpotMetadata(set.$extensions).setName ?? humanize(name);
}

function nativeContextSetName(
  modifierName: string,
  context: string,
  modifier: DtcgResolverModifier,
): string {
  const prefix =
    penpotMetadata(modifier.$extensions).setPrefix ?? humanize(modifierName);
  return `${prefix}/${humanize(context)}`;
}

function nativeThemeGroup(
  name: string,
  modifier: DtcgResolverModifier,
): string {
  return penpotMetadata(modifier.$extensions).themeGroup ?? humanize(name);
}

function penpotMetadata(extensions?: JsonObject): {
  setName?: string;
  setPrefix?: string;
  themeGroup?: string;
} {
  const value = extensions?.[PENPOT_EXTENSION];
  if (!isObject(value)) return {};
  return {
    setName: typeof value.setName === 'string' ? value.setName : undefined,
    setPrefix:
      typeof value.setPrefix === 'string' ? value.setPrefix : undefined,
    themeGroup:
      typeof value.themeGroup === 'string' ? value.themeGroup : undefined,
  };
}

function replaceSingleInlineSource(
  sources: DtcgResolverSource[],
  document: DtcgDocument,
  nativeName: string,
): void {
  if (sources.length !== 1 || isReference(sources[0])) {
    throw new Error(
      `Cannot round-trip ${nativeName}: editable Penpot sets require one inline resolver source.`,
    );
  }
  sources[0] = cloneJson(document as unknown as JsonValue) as DtcgDocument;
}

function referencesIn(value: JsonValue | undefined): string[] {
  if (value === undefined) return [];
  if (typeof value === 'string') {
    const match = value.match(/^\{([^{}]+)\}$/);
    return match ? [match[1]] : [];
  }
  if (Array.isArray(value)) return value.flatMap(referencesIn);
  if (isObject(value)) return Object.values(value).flatMap(referencesIn);
  return [];
}

function wholeAlias(value: string): string | undefined {
  return value.match(/^\{([^{}]+)\}$/)?.[1];
}

function mergeObjects(left: JsonObject, right: JsonObject): JsonObject {
  const result = cloneJson(left as JsonValue) as JsonObject;
  for (const [key, value] of Object.entries(right)) {
    if (value === undefined) continue;
    const previous = result[key];
    result[key] =
      isObject(previous) && isObject(value)
        ? mergeObjects(previous, value)
        : cloneJson(value);
  }
  return result;
}

function isReference(value: unknown): value is DtcgReferenceObject {
  return isObject(value) && typeof value.$ref === 'string';
}

function isObject(value: unknown): value is JsonObject {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value);
}

function cloneJson<T extends JsonValue>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T;
}

function decodePointerSegment(value: string): string {
  return decodeURIComponent(value).replace(/~1/g, '/').replace(/~0/g, '~');
}

function humanize(value: string): string {
  const spaced = value
    .replace(/([a-z\d])([A-Z])/g, '$1 $2')
    .replace(/[-_]+/g, ' ')
    .trim();
  return spaced ? `${spaced[0].toUpperCase()}${spaced.slice(1)}` : value;
}

function themeKey(group: string, name: string): string {
  return `${group}\u0000${name}`;
}

function sortedKeysReplacer(_key: string, value: unknown): unknown {
  if (!isObject(value)) return value;
  return Object.fromEntries(
    Object.entries(value).sort(([left], [right]) => left.localeCompare(right)),
  );
}
