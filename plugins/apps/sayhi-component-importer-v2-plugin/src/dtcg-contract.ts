import type {
  TokenProperty,
  TokenShadowValueString,
  TokenType,
  TokenValueString,
} from '@penpot/plugin-types';

export const DTCG_2025_10_SCHEMA =
  'https://www.designtokens.org/schemas/2025.10/format.json';
export const DTCG_RESOLVER_2025_10_SCHEMA =
  'https://www.designtokens.org/schemas/2025.10/resolver.json';

export const SAYHI_NAMESPACE = 'io.sayhi.studio';
export const SAYHI_COMPONENT_ID = 'sayhi.verification-method-selector';
export const SAYHI_COMPONENT_VERSION = '0.4.3-native-dtcg';

export type JsonPrimitive = boolean | number | string | null;
export type JsonValue = JsonPrimitive | JsonValue[] | JsonObject;
export type JsonObject = { [key: string]: JsonValue | undefined };

export interface DtcgDocument extends JsonObject {
  // The Format module does not define $schema. Its published JSON Schema
  // accepts the property as optional editor metadata, so importers must not
  // make it a conformance requirement.
  $schema?: string;
  $description?: string;
}

export interface DtcgReferenceObject extends JsonObject {
  $ref: string;
}

export type DtcgResolverSource = DtcgDocument | DtcgReferenceObject;

export interface DtcgResolverSet extends JsonObject {
  description?: string;
  sources: DtcgResolverSource[];
  $extensions?: JsonObject;
}

export interface DtcgResolverModifier extends JsonObject {
  description?: string;
  contexts: Record<string, DtcgResolverSource[]>;
  default?: string;
  $extensions?: JsonObject;
}

export interface DtcgResolverDocument extends JsonObject {
  $schema?: string;
  name?: string;
  version: '2025.10';
  description?: string;
  sets?: Record<string, DtcgResolverSet>;
  modifiers?: Record<string, DtcgResolverModifier>;
  resolutionOrder: DtcgReferenceObject[];
}

export type DtcgResolverInput = Record<string, string>;

export interface DtcgSetDocument {
  name: string;
  active: boolean;
  document: DtcgDocument;
}

export interface TokenThemeContract {
  group: string;
  name: string;
  sets: string[];
  active: boolean;
}

export interface ShapeTokenBinding {
  layer: string;
  token: string;
  type: TokenType;
  properties: TokenProperty[];
}

export interface NativeTokenProjection {
  token: string;
  type: TokenType;
}

export interface MotionPartContract {
  [part: string]: string[];
}

export interface SayHiComponentExtension {
  componentId: string;
  componentVersion: string;
  storyId: string;
  anatomy: JsonObject;
  runtime: JsonObject;
  tokenBindings: ShapeTokenBinding[];
  nativeTokenTypes: NativeTokenProjection[];
  motion: JsonObject;
}

export interface SayHiDtcgPackage {
  schemaName: 'io.sayhi.dtcg-package';
  schemaVersion: '0.1.0';
  sets: DtcgSetDocument[];
  themes: TokenThemeContract[];
  $extensions: {
    'io.sayhi.component': SayHiComponentExtension;
  };
}

export interface MaterializedToken {
  setName: string;
  name: string;
  dtcgType: string;
  penpotType?: TokenType;
  value?: TokenValueString | TokenShadowValueString[];
  sourceValue: JsonValue;
  description?: string;
  extensions?: JsonObject;
}

export interface MaterializedPackage {
  sets: Array<{
    name: string;
    active: boolean;
    tokens: MaterializedToken[];
  }>;
  themes: TokenThemeContract[];
  unsupported: MaterializedToken[];
}

export interface NativeTokenSnapshot {
  name: string;
  value: TokenValueString | TokenShadowValueString[];
  description: string;
}

export interface NativeSetSnapshot {
  name: string;
  active: boolean;
  tokens: NativeTokenSnapshot[];
}

export interface NativeThemeSnapshot {
  group: string;
  name: string;
  active: boolean;
  sets: string[];
}

export interface ImportResult {
  action: 'created' | 'updated' | 'unchanged';
  componentVersion: string;
  createdSets: number;
  createdThemes: number;
  createdTokens: number;
  unsupportedTokens: number;
  boundShapes: number;
  changes: string[];
}

export interface ProjectionFrame {
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface ProjectionTextSnapshot {
  characters: string;
  growType: string;
  fontId: string;
  fontFamily: string;
  fontVariantId: string;
  fontSize: string;
  fontWeight: string;
  fontStyle: string | null;
  lineHeight: string;
  letterSpacing: string;
  textTransform: string | null;
  textDecoration: string | null;
  direction: string | null;
  align: string | null;
  verticalAlign: string | null;
  bounds?: ProjectionFrame;
}

export interface ProjectionNodeSnapshot {
  sourceId: string;
  motionPart: string;
  name: string;
  type: string;
  frame: ProjectionFrame;
  bounds: ProjectionFrame;
  parentFrame?: { x: number; y: number };
  boardFrame?: { x: number; y: number };
  rotation: number;
  flipX: boolean;
  flipY: boolean;
  opacity: number;
  hidden: boolean;
  visible: boolean;
  blendMode: string;
  borderRadius: {
    topLeft: number;
    topRight: number;
    bottomRight: number;
    bottomLeft: number;
  };
  fills: JsonValue;
  strokes: JsonValue;
  shadows: JsonValue;
  clipContent?: boolean;
  showInViewMode?: boolean;
  pathData?: string;
  text?: ProjectionTextSnapshot;
  children: ProjectionNodeSnapshot[];
}

export interface PenpotProjectionSnapshot {
  schemaName: 'io.sayhi.penpot-projection-snapshot';
  schemaVersion: '0.1.0';
  componentId: string;
  componentVersion: string;
  storyId: string;
  root: ProjectionNodeSnapshot;
}

export type ImporterMessage =
  | { type: 'sayhi.import-verify' }
  | { type: 'sayhi.export-verify' }
  | { type: 'sayhi.export-projection' }
  | { type: 'sayhi.close' };

export type ImporterEvent =
  | {
      type: 'sayhi.ready';
      setCount: number;
      themeCount: number;
      bindingCount: number;
    }
  | { type: 'sayhi.importing' }
  | { type: 'sayhi.import-result'; result: ImportResult }
  | { type: 'sayhi.export-result'; json: string; filename: string }
  | { type: 'sayhi.projection-result'; json: string; filename: string }
  | { type: 'sayhi.import-error'; error: string };
