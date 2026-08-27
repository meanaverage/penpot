import type {
  TokenShadowValueString,
  TokenTypographyValueString,
  TokenType,
  TokenValueString,
} from '@penpot/plugin-types';
import {
  DTCG_2025_10_SCHEMA,
  type DtcgDocument,
  type JsonObject,
  type JsonValue,
  type MaterializedPackage,
  type MaterializedToken,
  type NativeSetSnapshot,
  type NativeThemeSnapshot,
  type SayHiDtcgPackage,
} from './dtcg-contract.js';

const RESERVED_KEYS = new Set([
  '$schema',
  '$description',
  '$extensions',
  '$type',
  '$value',
  '$deprecated',
  '$extends',
  '$root',
]);

const DEFAULT_PENPOT_TYPES: Partial<Record<string, TokenType>> = {
  color: 'color',
  dimension: 'dimension',
  fontFamily: 'fontFamilies',
  fontWeight: 'fontWeights',
  number: 'number',
  shadow: 'shadow',
  typography: 'typography',
};

interface WalkState {
  type?: string;
  extensions?: JsonObject;
}

const isObject = (value: unknown): value is JsonObject =>
  Boolean(value) && typeof value === 'object' && !Array.isArray(value);

const cloneJson = <T extends JsonValue>(value: T): T =>
  JSON.parse(JSON.stringify(value)) as T;

export function validateDtcgDocument(document: DtcgDocument): void {
  if (
    document.$schema !== undefined &&
    document.$schema !== DTCG_2025_10_SCHEMA
  ) {
    throw new Error(
      `Expected DTCG 2025.10 schema, received ${document.$schema}.`,
    );
  }

  walkDocument(document, '', {}, () => undefined);
}

export function materializePackage(
  source: SayHiDtcgPackage,
): MaterializedPackage {
  const extension = source.$extensions['io.sayhi.component'];
  const bindings = extension.tokenBindings;
  const typeHints = new Map([
    ...extension.nativeTokenTypes.map(
      (projection) => [projection.token, projection.type] as const,
    ),
    ...bindings.map((binding) => [binding.token, binding.type] as const),
  ]);
  const materialized: MaterializedPackage = {
    sets: [],
    themes: source.themes.map((theme) => ({ ...theme, sets: [...theme.sets] })),
    unsupported: [],
  };
  const knownNames = new Set<string>();

  for (const set of source.sets) {
    validateDtcgDocument(set.document);
    const tokens: MaterializedToken[] = [];
    walkDocument(set.document, '', {}, (token) => {
      const penpotType =
        typeHints.get(token.name) ?? DEFAULT_PENPOT_TYPES[token.dtcgType];
      const next: MaterializedToken = {
        ...token,
        setName: set.name,
        penpotType,
        value: penpotType
          ? materializeValue(token.dtcgType, token.sourceValue)
          : undefined,
      };
      if (penpotType && next.value !== undefined) tokens.push(next);
      else materialized.unsupported.push(next);
      knownNames.add(token.name);
    });
    materialized.sets.push({ name: set.name, active: set.active, tokens });
  }

  const unresolved = allTokens(materialized)
    .flatMap((token) => referencesIn(token.sourceValue))
    .filter((reference) => !knownNames.has(reference));
  if (unresolved.length) {
    throw new Error(
      `Unresolved DTCG aliases: ${[...new Set(unresolved)].join(', ')}.`,
    );
  }

  return materialized;
}

export function canonicalPackageJson(source: SayHiDtcgPackage): string {
  return JSON.stringify(source, sortedKeysReplacer, 2);
}

export function roundTripPackage(
  source: SayHiDtcgPackage,
  nativeSets: NativeSetSnapshot[],
  nativeThemes: NativeThemeSnapshot[],
): SayHiDtcgPackage {
  const result = cloneJson(
    source as unknown as JsonValue,
  ) as unknown as SayHiDtcgPackage;
  const sourcePlan = materializePackage(source);
  const setsByName = new Map(nativeSets.map((set) => [set.name, set]));

  for (const set of result.sets) {
    const nativeSet = setsByName.get(set.name);
    const plannedSet = sourcePlan.sets.find(
      (candidate) => candidate.name === set.name,
    );
    if (!nativeSet || !plannedSet) continue;
    set.active = nativeSet.active;
    const nativeTokens = new Map(
      nativeSet.tokens.map((token) => [token.name, token]),
    );
    for (const plannedToken of plannedSet.tokens) {
      const nativeToken = nativeTokens.get(plannedToken.name);
      if (!nativeToken) continue;
      const tokenNode = findTokenNode(set.document, plannedToken.name);
      tokenNode.$value = dematerializeValue(
        plannedToken.dtcgType,
        nativeToken.value,
        plannedToken.sourceValue,
      );
      if (nativeToken.description)
        tokenNode.$description = nativeToken.description;
      else delete tokenNode.$description;
    }
  }

  const themesByKey = new Map(
    nativeThemes.map((theme) => [themeKey(theme.group, theme.name), theme]),
  );
  result.themes = result.themes.map((theme) => {
    const nativeTheme = themesByKey.get(themeKey(theme.group, theme.name));
    return nativeTheme
      ? {
          ...theme,
          active: nativeTheme.active,
          sets: [...nativeTheme.sets],
        }
      : theme;
  });
  return result;
}

function allTokens(materialized: MaterializedPackage): MaterializedToken[] {
  return [
    ...materialized.sets.flatMap((set) => set.tokens),
    ...materialized.unsupported,
  ];
}

function walkDocument(
  node: JsonObject,
  path: string,
  inherited: WalkState,
  visit: (token: Omit<MaterializedToken, 'setName'>) => void,
): void {
  const type = typeof node.$type === 'string' ? node.$type : inherited.type;
  const description =
    typeof node.$description === 'string' ? node.$description : undefined;
  const extensions = isObject(node.$extensions)
    ? cloneJson(node.$extensions)
    : inherited.extensions;
  const hasValue = Object.hasOwn(node, '$value');
  const childKeys = Object.keys(node).filter((key) => !RESERVED_KEYS.has(key));

  if (hasValue) {
    if (!path) throw new Error('A DTCG token must have a name.');
    if (!type) throw new Error(`DTCG token ${path} has no $type.`);
    if (childKeys.length) {
      throw new Error(`DTCG token ${path} cannot contain child tokens.`);
    }
    const sourceValue = node.$value;
    if (!isJsonValue(sourceValue)) {
      throw new Error(`DTCG token ${path} has a non-JSON $value.`);
    }
    validateTokenValue(type, sourceValue, path);
    visit({
      name: path,
      dtcgType: type,
      sourceValue: cloneJson(sourceValue),
      description,
      extensions,
    });
    return;
  }

  for (const key of childKeys) {
    const child = node[key];
    if (!isObject(child)) {
      throw new Error(
        `DTCG group ${path || '<root>'}.${key} must be an object.`,
      );
    }
    walkDocument(
      child,
      path ? `${path}.${key}` : key,
      {
        type,
        extensions,
      },
      visit,
    );
  }
}

function validateTokenValue(
  type: string,
  value: JsonValue,
  path: string,
): void {
  // A whole-token alias is valid for every DTCG type. Component-level
  // references inside composite values are validated by their owning type as
  // those projections are added.
  if (typeof value === 'string' && /^\{[^{}]+\}$/.test(value)) return;

  if (type === 'dimension') {
    if (
      !isObject(value) ||
      typeof value.value !== 'number' ||
      !Number.isFinite(value.value) ||
      (value.unit !== 'px' && value.unit !== 'rem')
    ) {
      throw new Error(
        `DTCG dimension ${path} must contain a finite numeric value and a px or rem unit.`,
      );
    }
    return;
  }

  if (type === 'duration') {
    if (
      !isObject(value) ||
      typeof value.value !== 'number' ||
      !Number.isFinite(value.value) ||
      (value.unit !== 'ms' && value.unit !== 's')
    ) {
      throw new Error(
        `DTCG duration ${path} must contain a finite numeric value and an ms or s unit.`,
      );
    }
    return;
  }

  if (type === 'number') {
    if (typeof value !== 'number' || !Number.isFinite(value)) {
      throw new Error(`DTCG number ${path} must be a finite JSON number.`);
    }
    return;
  }

  if (type === 'fontFamily') {
    if (
      typeof value !== 'string' &&
      (!Array.isArray(value) ||
        !value.length ||
        !value.every((family) => typeof family === 'string' && family.length))
    ) {
      throw new Error(
        `DTCG font family ${path} must be a string or a non-empty array of strings.`,
      );
    }
    return;
  }

  if (type === 'fontWeight') {
    const namedWeights = new Set([
      'thin',
      'hairline',
      'extra-light',
      'ultra-light',
      'light',
      'normal',
      'regular',
      'book',
      'medium',
      'semi-bold',
      'demi-bold',
      'bold',
      'extra-bold',
      'ultra-bold',
      'black',
      'heavy',
      'extra-black',
      'ultra-black',
    ]);
    if (!(
      (typeof value === 'number' &&
        Number.isFinite(value) &&
        value >= 1 &&
        value <= 1000) ||
      (typeof value === 'string' && namedWeights.has(value))
    )) {
      throw new Error(
        `DTCG font weight ${path} must be a number from 1 through 1000 or a standard named weight.`,
      );
    }
    return;
  }

  if (type === 'typography') {
    validateTypographyValue(value, path);
    return;
  }

  if (type === 'cubicBezier') {
    if (
      !Array.isArray(value) ||
      value.length !== 4 ||
      !value.every(
        (coordinate) =>
          typeof coordinate === 'number' && Number.isFinite(coordinate),
      ) ||
      Number(value[0]) < 0 ||
      Number(value[0]) > 1 ||
      Number(value[2]) < 0 ||
      Number(value[2]) > 1
    ) {
      throw new Error(
        `DTCG cubic Bézier ${path} must contain four finite coordinates with x values from 0 through 1.`,
      );
    }
  }
}

function isJsonValue(value: unknown): value is JsonValue {
  if (
    value === null ||
    typeof value === 'string' ||
    typeof value === 'number' ||
    typeof value === 'boolean'
  ) {
    return true;
  }
  if (Array.isArray(value)) return value.every(isJsonValue);
  if (isObject(value)) {
    return Object.values(value).every(
      (entry) => entry === undefined || isJsonValue(entry),
    );
  }
  return false;
}

function materializeValue(
  type: string,
  value: JsonValue,
): TokenValueString | TokenShadowValueString[] | undefined {
  if (typeof value === 'string') return value;
  if (typeof value === 'number') {
    // DTCG represents variable font weights as numbers, but Penpot's
    // fontWeights token API accepts text (or one of its named variants).
    // Keep the canonical DTCG value numeric and adapt only at this boundary.
    return type === 'fontWeight' ? nativeFontWeight(value) : value;
  }
  if (Array.isArray(value)) {
    if (
      type === 'fontFamily' &&
      value.every((entry) => typeof entry === 'string')
    ) {
      return [...value] as string[];
    }
    if (type === 'shadow') {
      return value
        .map(materializeShadow)
        .filter((entry) => entry !== undefined);
    }
    return undefined;
  }
  if (!isObject(value)) return undefined;
  if (type === 'typography') {
    const fontFamilies = materializeTypographyMember(value.fontFamily);
    const fontSizes = materializeTypographyMember(value.fontSize);
    const fontWeight = materializeTypographyMember(value.fontWeight);
    const letterSpacing = materializeTypographyMember(value.letterSpacing);
    const lineHeight = materializeTypographyMember(value.lineHeight);
    if (
      (typeof fontFamilies !== 'string' && !Array.isArray(fontFamilies)) ||
      typeof fontSizes !== 'string' ||
      typeof fontWeight !== 'string' ||
      typeof letterSpacing !== 'string' ||
      typeof lineHeight !== 'string'
    ) {
      return undefined;
    }
    return {
      fontFamilies,
      fontSizes,
      fontWeight,
      letterSpacing,
      lineHeight,
      textCase: 'none',
      textDecoration: 'none',
    } satisfies TokenTypographyValueString;
  }
  if (type === 'color') {
    if (typeof value.hex === 'string') return value.hex;
    return srgbHex(value);
  }
  if (
    (type === 'dimension' || type === 'duration') &&
    typeof value.value === 'number' &&
    typeof value.unit === 'string'
  ) {
    return `${value.value}${value.unit}`;
  }
  return undefined;
}

function materializeTypographyMember(
  value: JsonValue | undefined,
): string | string[] | undefined {
  if (typeof value === 'string') return value;
  if (typeof value === 'number') return String(value);
  if (
    Array.isArray(value) &&
    value.every((entry) => typeof entry === 'string')
  ) {
    return [...value];
  }
  if (
    isObject(value) &&
    typeof value.value === 'number' &&
    typeof value.unit === 'string'
  ) {
    return `${value.value}${value.unit}`;
  }
  return undefined;
}

function materializeShadow(
  value: JsonValue,
): TokenShadowValueString | undefined {
  if (!isObject(value)) return undefined;
  const colorValue = isObject(value.color)
    ? materializeValue('color', value.color)
    : value.color;
  return {
    color: typeof colorValue === 'string' ? colorValue : '#000000',
    inset: String(value.inset ?? false),
    offsetX: materializeLength(value.offsetX),
    offsetY: materializeLength(value.offsetY),
    spread: materializeLength(value.spread),
    blur: materializeLength(value.blur),
  };
}

function materializeLength(value: JsonValue | undefined): string {
  if (typeof value === 'number') return String(value);
  if (typeof value === 'string') return value;
  if (
    isObject(value) &&
    typeof value.value === 'number' &&
    typeof value.unit === 'string'
  ) {
    return `${value.value}${value.unit}`;
  }
  return '0';
}

function srgbHex(value: JsonObject): string | undefined {
  const components = value.components;
  if (
    value.colorSpace !== 'srgb' ||
    !Array.isArray(components) ||
    components.length < 3 ||
    !components.slice(0, 3).every((component) => typeof component === 'number')
  ) {
    return undefined;
  }
  return `#${components
    .slice(0, 3)
    .map((component) =>
      Math.round(Math.max(0, Math.min(1, Number(component))) * 255)
        .toString(16)
        .padStart(2, '0'),
    )
    .join('')}`;
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

function findTokenNode(document: DtcgDocument, path: string): JsonObject {
  let current: JsonObject = document;
  for (const segment of path.split('.')) {
    const next = current[segment];
    if (!isObject(next)) throw new Error(`Missing DTCG token path ${path}.`);
    current = next;
  }
  if (!Object.hasOwn(current, '$value')) {
    throw new Error(`DTCG path ${path} is not a token.`);
  }
  return current;
}

function dematerializeValue(
  type: string,
  value: TokenValueString | TokenShadowValueString[],
  fallback: JsonValue,
): JsonValue {
  if (typeof value === 'string' && /^\{[^{}]+\}$/.test(value)) return value;
  if (type === 'color' && typeof value === 'string') {
    return dtcgColor(value) ?? fallback;
  }
  if (
    type === 'dimension' &&
    (typeof value === 'string' || typeof value === 'number')
  ) {
    return dtcgDimension(value) ?? fallback;
  }
  if (type === 'fontFamily') {
    if (
      Array.isArray(value) &&
      value.every((entry) => typeof entry === 'string')
    ) {
      return [...value];
    }
    if (typeof value === 'string') return value;
  }
  if (type === 'fontWeight') {
    if (typeof value === 'number') return value;
    if (typeof value === 'string' && Number.isFinite(Number(value))) {
      const numeric = Number(value);
      if (
        typeof fallback === 'number' &&
        value === nativeFontWeight(fallback)
      ) {
        return fallback;
      }
      return numeric;
    }
    if (typeof value === 'string') return value;
  }
  if (type === 'typography' && isTypographySnapshot(value)) {
    const materializedFallback = materializeValue(type, fallback);
    if (JSON.stringify(value) === JSON.stringify(materializedFallback)) {
      return cloneJson(fallback);
    }
    if (!isObject(fallback)) return cloneJson(fallback);
    return {
      fontFamily: dematerializeTypographyFamily(value.fontFamilies),
      fontSize: dematerializeTypographyDimension(value.fontSizes),
      fontWeight: dematerializeTypographyWeight(value.fontWeight),
      letterSpacing: dematerializeTypographyDimension(value.letterSpacing),
      lineHeight: dematerializeTypographyNumber(value.lineHeight),
    };
  }
  if (
    type === 'number' &&
    (typeof value === 'string' || typeof value === 'number')
  ) {
    const numeric = Number(value);
    return Number.isFinite(numeric) ? numeric : fallback;
  }
  if (type === 'shadow' && Array.isArray(value)) {
    const shadows = value.filter(isShadowSnapshot).map((shadow) => ({
      color: aliasOrColor(shadow.color),
      inset: shadow.inset === 'true',
      offsetX: dtcgDimension(shadow.offsetX) ?? { value: 0, unit: 'px' },
      offsetY: dtcgDimension(shadow.offsetY) ?? { value: 0, unit: 'px' },
      spread: dtcgDimension(shadow.spread) ?? { value: 0, unit: 'px' },
      blur: dtcgDimension(shadow.blur) ?? { value: 0, unit: 'px' },
    }));
    return shadows;
  }
  return cloneJson(fallback);
}

function validateTypographyValue(value: JsonValue, path: string): void {
  if (!isObject(value)) {
    throw new Error(`DTCG typography ${path} must be an object.`);
  }
  const required = [
    'fontFamily',
    'fontSize',
    'fontWeight',
    'letterSpacing',
    'lineHeight',
  ] as const;
  for (const property of required) {
    if (!Object.hasOwn(value, property)) {
      throw new Error(`DTCG typography ${path} is missing ${property}.`);
    }
  }
  validateTypographyMember('fontFamily', value.fontFamily, path);
  validateTypographyMember('fontSize', value.fontSize, path);
  validateTypographyMember('fontWeight', value.fontWeight, path);
  validateTypographyMember('letterSpacing', value.letterSpacing, path);
  validateTypographyMember('lineHeight', value.lineHeight, path);
}

function validateTypographyMember(
  property: string,
  value: JsonValue | undefined,
  path: string,
): void {
  if (typeof value === 'string' && /^\{[^{}]+\}$/.test(value)) return;
  if (property === 'fontFamily') {
    validateTokenValue('fontFamily', value as JsonValue, `${path}.${property}`);
    return;
  }
  if (property === 'fontSize' || property === 'letterSpacing') {
    validateTokenValue('dimension', value as JsonValue, `${path}.${property}`);
    return;
  }
  if (property === 'fontWeight') {
    validateTokenValue('fontWeight', value as JsonValue, `${path}.${property}`);
    return;
  }
  validateTokenValue('number', value as JsonValue, `${path}.${property}`);
}

function isTypographySnapshot(
  value: TokenValueString | TokenShadowValueString[],
): value is TokenTypographyValueString {
  return (
    isObject(value) &&
    (typeof value.fontFamilies === 'string' ||
      (Array.isArray(value.fontFamilies) &&
        value.fontFamilies.every((entry) => typeof entry === 'string'))) &&
    typeof value.fontSizes === 'string' &&
    typeof value.fontWeight === 'string' &&
    typeof value.letterSpacing === 'string' &&
    typeof value.lineHeight === 'string'
  );
}

function dematerializeTypographyFamily(value: string | string[]): JsonValue {
  return Array.isArray(value) ? [...value] : value;
}

function dematerializeTypographyDimension(value: string): JsonValue {
  if (/^\{[^{}]+\}$/.test(value)) return value;
  return dtcgDimension(value) ?? value;
}

function dematerializeTypographyWeight(value: string): JsonValue {
  if (/^\{[^{}]+\}$/.test(value)) return value;
  return Number.isFinite(Number(value)) ? Number(value) : value;
}

function dematerializeTypographyNumber(value: string): JsonValue {
  if (/^\{[^{}]+\}$/.test(value)) return value;
  return Number.isFinite(Number(value)) ? Number(value) : value;
}

function nativeFontWeight(value: number): string {
  const clamped = Math.max(100, Math.min(900, value));
  return String(Math.round(clamped / 100) * 100);
}

function isShadowSnapshot(value: unknown): value is TokenShadowValueString {
  return (
    isObject(value) &&
    ['color', 'inset', 'offsetX', 'offsetY', 'spread', 'blur'].every(
      (key) => typeof value[key] === 'string',
    )
  );
}

function aliasOrColor(value: string): JsonValue {
  if (/^\{[^{}]+\}$/.test(value)) return value;
  return dtcgColor(value) ?? value;
}

function dtcgColor(value: string): JsonObject | undefined {
  const match = value.match(/^#([0-9a-f]{6})([0-9a-f]{2})?$/i);
  if (!match) return undefined;
  const hex = `#${match[1].toLowerCase()}${match[2]?.toLowerCase() ?? ''}`;
  return {
    colorSpace: 'srgb',
    components: [
      Number.parseInt(match[1].slice(0, 2), 16) / 255,
      Number.parseInt(match[1].slice(2, 4), 16) / 255,
      Number.parseInt(match[1].slice(4, 6), 16) / 255,
    ],
    alpha: match[2] ? Number.parseInt(match[2], 16) / 255 : 1,
    hex,
  };
}

function dtcgDimension(value: string | number): JsonObject | undefined {
  const match = String(value)
    .trim()
    .match(/^(-?(?:\d+\.?\d*|\.\d+))([a-z%]+)?$/i);
  if (!match) return undefined;
  const unit = match[2] || 'px';
  if (unit !== 'px' && unit !== 'rem') return undefined;
  return { value: Number(match[1]), unit };
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
