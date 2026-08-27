import type {
  TokenProperty,
  TokenShadowValueString,
  TokenType,
  TokenValueString,
} from '@penpot/plugin-types';
import type {
  DtcgResolverDocument,
  JsonObject,
  JsonValue,
} from './dtcg-contract.js';

export const PROJECTION_SCHEMA_NAME = 'io.sayhi.component-projection';
export const PROJECTION_SCHEMA_VERSION = '0.2.0';
export const PROJECTION_NAMESPACE = 'io.sayhi.projection-v2';
export const SAYHI_STUDIO_NAMESPACE = 'io.sayhi.studio';

export type Metric = number | { token: string };
export type ColorValue = string | { token: string };

export interface ProjectionFrame {
  x: Metric;
  y: Metric;
  width: Metric;
  height: Metric;
}

export interface ProjectionFill {
  color?: ColorValue;
  opacity?: number;
  gradient?: {
    type: 'linear';
    angle?: number;
    stops: Array<{ offset: number; color: string; opacity?: number }>;
  };
}

export interface ProjectionStroke {
  color: ColorValue;
  opacity?: number;
  width: Metric;
  alignment?: 'inner' | 'center' | 'outer';
}

export interface ProjectionShadow {
  color: string;
  opacity: number;
  offsetX: Metric;
  offsetY: Metric;
  blur: Metric;
  spread: Metric;
}

export interface ProjectionTextStyle {
  fontFamily: string | { token: string };
  fontSize: Metric;
  fontWeight: number | string | { token: string };
  /** Unitless multiplier, matching DTCG typography lineHeight semantics. */
  lineHeight: number;
  letterSpacing: Metric;
  color: ColorValue;
  align?: 'left' | 'center' | 'right' | 'justify';
  verticalAlign?: 'top' | 'center' | 'bottom';
  textTransform?: 'none' | 'uppercase' | 'lowercase' | 'capitalize';
}

interface ProjectionNodeBase {
  id: string;
  name: string;
  frame: ProjectionFrame;
  opacity?: number;
  rotation?: number;
  hidden?: boolean;
  motionPart?: string;
  tokenBindings?: Array<{
    token: string;
    type: TokenType;
    properties: TokenProperty[];
  }>;
  children?: ProjectionNode[];
}

export interface ProjectionBoardNode extends ProjectionNodeBase {
  type: 'board';
  clipContent?: boolean;
  showInViewMode?: boolean;
  fills?: ProjectionFill[];
}

export interface ProjectionRectangleNode extends ProjectionNodeBase {
  type: 'rectangle';
  radius?: Metric;
  fills?: ProjectionFill[];
  strokes?: ProjectionStroke[];
  shadows?: ProjectionShadow[];
}

export interface ProjectionEllipseNode extends ProjectionNodeBase {
  type: 'ellipse';
  fills?: ProjectionFill[];
}

export interface ProjectionTextNode extends ProjectionNodeBase {
  type: 'text';
  characters: string;
  style: ProjectionTextStyle;
}

export interface ProjectionSvgNode extends ProjectionNodeBase {
  type: 'svg';
  markup: string;
}

export type ProjectionNode =
  | ProjectionBoardNode
  | ProjectionRectangleNode
  | ProjectionEllipseNode
  | ProjectionTextNode
  | ProjectionSvgNode;

export interface ComponentProjectionManifest {
  schemaName: typeof PROJECTION_SCHEMA_NAME;
  schemaVersion: typeof PROJECTION_SCHEMA_VERSION;
  component: {
    id: string;
    version: string;
    name: string;
    libraryPath: string;
    storyId?: string;
  };
  resolver: DtcgResolverDocument;
  root: ProjectionBoardNode;
  $extensions?: {
    'io.sayhi.component'?: JsonObject;
    'io.sayhi.penpot'?: JsonObject;
  };
}

export interface NativeTokenPlan {
  setName: string;
  name: string;
  type: TokenType;
  value: TokenValueString | TokenShadowValueString[];
  description?: string;
  sourceValue: JsonValue;
}

export interface ProjectionImportResult {
  componentId: string;
  componentVersion: string;
  createdNodes: number;
  createdSets: number;
  createdThemes: number;
  createdTokens: number;
  boundNodes: number;
  unsupportedTokens: number;
}

export type ImporterMessage =
  | { type: 'sayhi.v2.import' }
  | { type: 'sayhi.v2.export' }
  | { type: 'sayhi.v2.close' };

export type ImporterEvent =
  | {
      type: 'sayhi.v2.ready';
      nodeCount: number;
      setCount: number;
      bindingCount: number;
    }
  | { type: 'sayhi.v2.importing' }
  | { type: 'sayhi.v2.import-result'; result: ProjectionImportResult }
  | { type: 'sayhi.v2.export-result'; json: string; filename: string }
  | { type: 'sayhi.v2.error'; error: string };

export function countProjectionNodes(node: ProjectionNode): number {
  return (
    1 +
    (node.children ?? []).reduce(
      (total, child) => total + countProjectionNodes(child),
      0,
    )
  );
}

export function countProjectionBindings(node: ProjectionNode): number {
  return (
    (node.tokenBindings?.length ?? 0) +
    (node.children ?? []).reduce(
      (total, child) => total + countProjectionBindings(child),
      0,
    )
  );
}
