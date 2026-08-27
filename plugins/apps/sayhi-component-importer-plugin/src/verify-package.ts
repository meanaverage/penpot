import {
  DTCG_2025_10_SCHEMA,
  DTCG_RESOLVER_2025_10_SCHEMA,
  SAYHI_COMPONENT_ID,
  SAYHI_COMPONENT_VERSION,
  type DtcgDocument,
  type DtcgResolverDocument,
  type JsonObject,
  type NativeTokenProjection,
  type SayHiComponentExtension,
  type SayHiDtcgPackage,
  type ShapeTokenBinding,
} from './contract.js';

const color = (hex: string): JsonObject => ({
  colorSpace: 'srgb',
  components: [
    Number.parseInt(hex.slice(1, 3), 16) / 255,
    Number.parseInt(hex.slice(3, 5), 16) / 255,
    Number.parseInt(hex.slice(5, 7), 16) / 255,
  ],
  alpha: 1,
  hex,
});

const px = (value: number): JsonObject => ({ value, unit: 'px' });
const ms = (value: number): JsonObject => ({ value, unit: 'ms' });
const verifyToken = (name: string): string => `verify.${name}`;

export const VERIFY_METHOD_ICON_SIZES = Object.freeze({
  side: 36 * 0.76,
  active: 36 * 1.08,
});

const document = (
  description: string,
  tokens: Record<string, unknown>,
): DtcgDocument => ({
  $schema: DTCG_2025_10_SCHEMA,
  $description: description,
  ...tokens,
});

export const VERIFY_TOKEN_BINDINGS: ShapeTokenBinding[] = [
  {
    layer: 'Panel-face',
    token: verifyToken('panel.background'),
    type: 'color',
    properties: ['fill'],
  },
  {
    layer: 'Panel-face',
    token: verifyToken('panel.border-radius'),
    type: 'borderRadius',
    properties: [
      'borderRadiusTopLeft',
      'borderRadiusTopRight',
      'borderRadiusBottomRight',
      'borderRadiusBottomLeft',
    ],
  },
  {
    layer: 'Panel-face',
    token: verifyToken('panel.border-color'),
    type: 'color',
    properties: ['strokeColor'],
  },
  {
    layer: 'Method-face',
    token: verifyToken('method.background'),
    type: 'color',
    properties: ['fill'],
  },
  {
    layer: 'Method-face',
    token: verifyToken('method.border-radius'),
    type: 'borderRadius',
    properties: [
      'borderRadiusTopLeft',
      'borderRadiusTopRight',
      'borderRadiusBottomRight',
      'borderRadiusBottomLeft',
    ],
  },
  {
    layer: 'Method-face',
    token: verifyToken('method.border-color'),
    type: 'color',
    properties: ['strokeColor'],
  },
  {
    layer: 'Active-method-face',
    token: verifyToken('method.background'),
    type: 'color',
    properties: ['fill'],
  },
  {
    layer: 'Active-method-face',
    token: verifyToken('method.active.border-radius'),
    type: 'borderRadius',
    properties: [
      'borderRadiusTopLeft',
      'borderRadiusTopRight',
      'borderRadiusBottomRight',
      'borderRadiusBottomLeft',
    ],
  },
  {
    layer: 'Heading-copy',
    token: verifyToken('text.primary'),
    type: 'color',
    properties: ['fill'],
  },
  {
    layer: 'Side-method-label',
    token: verifyToken('text.primary'),
    type: 'color',
    properties: ['fill'],
  },
  {
    layer: 'Active-method-label',
    token: verifyToken('text.primary'),
    type: 'color',
    properties: ['fill'],
  },
  {
    layer: 'Body-copy',
    token: verifyToken('text.muted'),
    type: 'color',
    properties: ['fill'],
  },
  {
    layer: 'Side-method-description',
    token: verifyToken('text.muted'),
    type: 'color',
    properties: ['fill'],
  },
  {
    layer: 'Active-method-description',
    token: verifyToken('text.muted'),
    type: 'color',
    properties: ['fill'],
  },
  {
    layer: 'Hint-copy',
    token: verifyToken('text.muted'),
    type: 'color',
    properties: ['fill'],
  },
  {
    layer: 'Eyebrow-copy',
    token: verifyToken('text.accent'),
    type: 'color',
    properties: ['fill'],
  },
  {
    layer: 'Cancel-action',
    token: verifyToken('text.accent'),
    type: 'color',
    properties: ['fill'],
  },
  {
    layer: 'Eyebrow-copy',
    token: verifyToken('typography.eyebrow'),
    type: 'typography',
    properties: ['typography'],
  },
  {
    layer: 'Heading-copy',
    token: verifyToken('typography.heading'),
    type: 'typography',
    properties: ['typography'],
  },
  {
    layer: 'Body-copy',
    token: verifyToken('typography.body'),
    type: 'typography',
    properties: ['typography'],
  },
  {
    layer: 'Side-method-label',
    token: verifyToken('typography.method.side.label'),
    type: 'typography',
    properties: ['typography'],
  },
  {
    layer: 'Side-method-description',
    token: verifyToken('typography.method.side.description'),
    type: 'typography',
    properties: ['typography'],
  },
  {
    layer: 'Active-method-label',
    token: verifyToken('typography.method.active.label'),
    type: 'typography',
    properties: ['typography'],
  },
  {
    layer: 'Active-method-description',
    token: verifyToken('typography.method.active.description'),
    type: 'typography',
    properties: ['typography'],
  },
  {
    layer: 'Hint-copy',
    token: verifyToken('typography.hint'),
    type: 'typography',
    properties: ['typography'],
  },
  {
    layer: 'Cancel-action',
    token: verifyToken('typography.cancel'),
    type: 'typography',
    properties: ['typography'],
  },
  {
    layer: 'Email-icon',
    token: verifyToken('method.side.icon-size'),
    type: 'sizing',
    properties: ['width', 'height'],
  },
  {
    layer: 'Authenticator-icon',
    token: verifyToken('method.side.icon-size'),
    type: 'sizing',
    properties: ['width', 'height'],
  },
  {
    layer: 'Passkey-icon',
    token: verifyToken('method.active.icon-size'),
    type: 'sizing',
    properties: ['width', 'height'],
  },
  {
    layer: 'Panel-face',
    token: verifyToken('panel.width'),
    type: 'sizing',
    properties: ['width'],
  },
  {
    layer: 'Panel-face',
    token: verifyToken('panel.height'),
    type: 'sizing',
    properties: ['height'],
  },
];

export const VERIFY_NATIVE_TOKEN_TYPES: NativeTokenProjection[] = [
  ...[
    'panel.border-radius',
    'method.border-radius',
    'method.active.border-radius',
  ].map((token) => ({
    token: verifyToken(token),
    type: 'borderRadius' as const,
  })),
  ...[
    'canvas.width',
    'canvas.height',
    'panel.width',
    'panel.height',
    'method.side.width',
    'method.side.height',
    'method.side.icon-size',
    'method.active.width',
    'method.active.height',
    'method.active.icon-size',
  ].map((token) => ({
    token: verifyToken(token),
    type: 'sizing' as const,
  })),
  ...[
    'eyebrow.font-size',
    'heading.font-size',
    'body.font-size',
    'method.font-size',
    'method.description.font-size',
    'method.active.font-size',
    'method.active.description.font-size',
    'hint.font-size',
    'cancel.font-size',
  ].map((token) => ({
    token: verifyToken(token),
    type: 'fontSizes' as const,
  })),
  ...['eyebrow.letter-spacing', 'heading.letter-spacing'].map((token) => ({
    token: verifyToken(token),
    type: 'letterSpacing' as const,
  })),
  ...['layout.panel-inset', 'layout.card-gap'].map((token) => ({
    token: verifyToken(token),
    type: 'spacing' as const,
  })),
  {
    token: verifyToken('font.family'),
    type: 'fontFamilies',
  },
];

const primitives = document('Reusable primitive values.', {
  color: {
    base: {
      white: { $type: 'color', $value: color('#ffffff') },
      ink: { $type: 'color', $value: color('#171a17') },
      muted: { $type: 'color', $value: color('#697169') },
      green: { $type: 'color', $value: color('#315f47') },
      line: { $type: 'color', $value: color('#dce1da') },
      canvas: { $type: 'color', $value: color('#ffffff') },
      panel: { $type: 'color', $value: color('#eef1eb') },
      darkInk: { $type: 'color', $value: color('#eef1ed') },
      darkMuted: { $type: 'color', $value: color('#aab2aa') },
      darkGreen: { $type: 'color', $value: color('#8bb39a') },
      darkLine: { $type: 'color', $value: color('#3b423c') },
      darkCanvas: { $type: 'color', $value: color('#101210') },
      darkPanel: { $type: 'color', $value: color('#202520') },
    },
  },
  font: {
    family: {
      sans: {
        $type: 'fontFamily',
        $value: ['Instrument Sans', 'Inter', 'sans-serif'],
      },
    },
    weight: {
      regular: { $type: 'fontWeight', $value: 400 },
      medium: { $type: 'fontWeight', $value: 560 },
      strong: { $type: 'fontWeight', $value: 710 },
    },
  },
  motion: {
    duration: {
      quick: { $type: 'duration', $value: ms(220) },
      selection: { $type: 'duration', $value: ms(420) },
    },
    easing: {
      selection: {
        $type: 'cubicBezier',
        $value: [0.22, 1, 0.36, 1],
      },
    },
  },
});

const light = document('Semantic light-theme values.', {
  color: {
    background: {
      canvas: { $type: 'color', $value: '{color.base.canvas}' },
      panel: { $type: 'color', $value: '{color.base.panel}' },
      control: { $type: 'color', $value: '{color.base.white}' },
    },
    text: {
      primary: { $type: 'color', $value: '{color.base.ink}' },
      muted: { $type: 'color', $value: '{color.base.muted}' },
      accent: { $type: 'color', $value: '{color.base.green}' },
    },
    border: {
      subtle: { $type: 'color', $value: '{color.base.line}' },
    },
  },
});

const dark = document('Semantic dark-theme values.', {
  color: {
    background: {
      canvas: { $type: 'color', $value: '{color.base.darkCanvas}' },
      panel: { $type: 'color', $value: '{color.base.darkPanel}' },
      control: { $type: 'color', $value: '{color.base.darkPanel}' },
    },
    text: {
      primary: { $type: 'color', $value: '{color.base.darkInk}' },
      muted: { $type: 'color', $value: '{color.base.darkMuted}' },
      accent: { $type: 'color', $value: '{color.base.darkGreen}' },
    },
    border: {
      subtle: { $type: 'color', $value: '{color.base.darkLine}' },
    },
  },
});

const verify = document('Verify component decisions.', {
  verify: {
    canvas: {
      background: { $type: 'color', $value: '{color.background.canvas}' },
      width: { $type: 'dimension', $value: px(580) },
      height: { $type: 'dimension', $value: px(500) },
    },
    panel: {
      background: { $type: 'color', $value: '{color.background.panel}' },
      'border-color': { $type: 'color', $value: '{color.border.subtle}' },
      'border-radius': { $type: 'dimension', $value: px(24) },
      width: { $type: 'dimension', $value: px(578) },
      height: { $type: 'dimension', $value: px(498) },
    },
    method: {
      background: { $type: 'color', $value: '{color.background.control}' },
      'border-color': { $type: 'color', $value: '{color.border.subtle}' },
      'border-radius': { $type: 'dimension', $value: px(17) },
      'font-size': { $type: 'dimension', $value: px(12.16) },
      description: {
        'font-size': { $type: 'dimension', $value: px(8.75) },
      },
      side: {
        width: { $type: 'dimension', $value: px(137) },
        height: { $type: 'dimension', $value: px(117) },
        'icon-size': {
          $type: 'dimension',
          $value: px(VERIFY_METHOD_ICON_SIZES.side),
        },
      },
      active: {
        'border-radius': { $type: 'dimension', $value: px(23) },
        width: { $type: 'dimension', $value: px(192) },
        height: { $type: 'dimension', $value: px(166) },
        'icon-size': {
          $type: 'dimension',
          $value: px(VERIFY_METHOD_ICON_SIZES.active),
        },
        'font-size': { $type: 'dimension', $value: px(17) },
        description: {
          'font-size': { $type: 'dimension', $value: px(12) },
        },
        shadow: {
          $type: 'shadow',
          $value: [
            {
              color: color('#28372b'),
              offsetX: px(0),
              offsetY: px(10),
              blur: px(28),
              spread: px(0),
              inset: false,
            },
          ],
        },
      },
    },
    text: {
      primary: { $type: 'color', $value: '{color.text.primary}' },
      muted: { $type: 'color', $value: '{color.text.muted}' },
      accent: { $type: 'color', $value: '{color.text.accent}' },
    },
    font: {
      family: { $type: 'fontFamily', $value: '{font.family.sans}' },
      weight: {
        regular: { $type: 'fontWeight', $value: '{font.weight.regular}' },
        medium: { $type: 'fontWeight', $value: '{font.weight.medium}' },
        strong: { $type: 'fontWeight', $value: '{font.weight.strong}' },
      },
    },
    eyebrow: {
      'font-size': { $type: 'dimension', $value: px(11) },
      'letter-spacing': { $type: 'dimension', $value: px(1.3) },
    },
    heading: {
      'font-size': { $type: 'dimension', $value: px(38) },
      'letter-spacing': { $type: 'dimension', $value: px(-1.8) },
    },
    body: { 'font-size': { $type: 'dimension', $value: px(16) } },
    hint: { 'font-size': { $type: 'dimension', $value: px(11.5) } },
    cancel: { 'font-size': { $type: 'dimension', $value: px(13) } },
    typography: {
      eyebrow: {
        $type: 'typography',
        $value: {
          fontFamily: '{verify.font.family}',
          fontSize: '{verify.eyebrow.font-size}',
          fontWeight: '{verify.font.weight.strong}',
          letterSpacing: '{verify.eyebrow.letter-spacing}',
          lineHeight: 2,
        },
      },
      heading: {
        $type: 'typography',
        $value: {
          fontFamily: '{verify.font.family}',
          fontSize: '{verify.heading.font-size}',
          fontWeight: '{verify.font.weight.medium}',
          letterSpacing: '{verify.heading.letter-spacing}',
          lineHeight: 1.2631578947,
        },
      },
      body: {
        $type: 'typography',
        $value: {
          fontFamily: '{verify.font.family}',
          fontSize: '{verify.body.font-size}',
          fontWeight: '{verify.font.weight.regular}',
          letterSpacing: px(0),
          lineHeight: 1.75,
        },
      },
      method: {
        side: {
          label: {
            $type: 'typography',
            $value: {
              fontFamily: '{verify.font.family}',
              fontSize: '{verify.method.font-size}',
              fontWeight: '{verify.font.weight.strong}',
              letterSpacing: px(0),
              lineHeight: 1.7269736842,
            },
          },
          description: {
            $type: 'typography',
            $value: {
              fontFamily: '{verify.font.family}',
              fontSize: '{verify.method.description.font-size}',
              fontWeight: '{verify.font.weight.medium}',
              letterSpacing: px(0),
              lineHeight: 2.0571428571,
            },
          },
        },
        active: {
          label: {
            $type: 'typography',
            $value: {
              fontFamily: '{verify.font.family}',
              fontSize: '{verify.method.active.font-size}',
              fontWeight: '{verify.font.weight.strong}',
              letterSpacing: px(0),
              lineHeight: 1.4117647059,
            },
          },
          description: {
            $type: 'typography',
            $value: {
              fontFamily: '{verify.font.family}',
              fontSize: '{verify.method.active.description.font-size}',
              fontWeight: '{verify.font.weight.medium}',
              letterSpacing: px(0),
              lineHeight: 1.8333333333,
            },
          },
        },
      },
      hint: {
        $type: 'typography',
        $value: {
          fontFamily: '{verify.font.family}',
          fontSize: '{verify.hint.font-size}',
          fontWeight: '{verify.font.weight.regular}',
          letterSpacing: px(0),
          lineHeight: 2.4347826087,
        },
      },
      cancel: {
        $type: 'typography',
        $value: {
          fontFamily: '{verify.font.family}',
          fontSize: '{verify.cancel.font-size}',
          fontWeight: '{verify.font.weight.strong}',
          letterSpacing: px(0),
          lineHeight: 1.8461538462,
        },
      },
    },
    layout: {
      'panel-inset': { $type: 'dimension', $value: px(1) },
      'card-gap': { $type: 'dimension', $value: px(4) },
    },
  },
});

const anatomy: JsonObject = {
  root: 'SayHi-Verify',
  parts: {
    panel: ['Panel-face'],
    heading: ['Eyebrow-copy', 'Heading-copy', 'Body-copy'],
    selector: ['Verification-methods'],
    method: ['Email-code', 'Passkey-selected', 'Authenticator'],
    'method-face': ['Method-face'],
    'method-icon': ['Email-icon', 'Passkey-icon', 'Authenticator-icon'],
    'method-label': ['Side-method-label', 'Active-method-label'],
    'method-description': [
      'Side-method-description',
      'Active-method-description',
    ],
    pagination: ['Pagination'],
    'active-ring': ['Active-ring'],
    help: ['Hint-copy'],
    cancel: ['Cancel-action'],
  },
};

const motion: JsonObject = {
  schemaName: 'sayhi.motion',
  schemaVersion: '0.5.0',
  revision: 0,
  programs: [
    {
      id: 'verify-selection-response',
      label: 'Verify selection response',
      reducedMotion: 'skip',
      trigger: { type: 'event', event: 'selection.change' },
      responses: [
        {
          id: 'previous',
          label: 'Previous method',
          direction: 'previous',
          match: { direction: 'previous' },
          steps: [
            {
              id: 'previous-carousel-flow',
              driver: 'css-transition',
              targetPart: 'selector',
              timelineKind: 'triggered',
              at: 0,
              duration: '{motion.duration.selection}',
              ease: '{motion.easing.selection}',
              stagger: 0.025,
              from: { x: -18, rotation: -1.4, autoAlpha: 0.72 },
              to: { x: 0, rotation: 0, autoAlpha: 1 },
            },
            {
              id: 'previous-icons',
              driver: 'gsap',
              targetPart: 'method-icon',
              timelineKind: 'timed',
              at: 0.06,
              duration: 0.28,
              ease: 'power2.out',
              stagger: 0.035,
              from: { y: 7, scale: 0.92, autoAlpha: 0.45 },
              to: { y: 0, scale: 1, autoAlpha: 1 },
            },
            {
              id: 'previous-pagination',
              driver: 'waapi',
              targetPart: 'pagination',
              timelineKind: 'timed',
              at: 0.1,
              duration: '{motion.duration.quick}',
              ease: 'power2.out',
              stagger: 0,
              from: { x: -6, autoAlpha: 0.55 },
              to: { x: 0, autoAlpha: 1 },
            },
          ],
        },
        {
          id: 'next',
          label: 'Next method',
          direction: 'next',
          match: { direction: 'next' },
          steps: [
            {
              id: 'next-carousel-flow',
              driver: 'css-transition',
              targetPart: 'selector',
              timelineKind: 'triggered',
              at: 0,
              duration: '{motion.duration.selection}',
              ease: '{motion.easing.selection}',
              stagger: 0.025,
              from: { x: 18, rotation: 1.4, autoAlpha: 0.72 },
              to: { x: 0, rotation: 0, autoAlpha: 1 },
            },
            {
              id: 'next-icons',
              driver: 'gsap',
              targetPart: 'method-icon',
              timelineKind: 'timed',
              at: 0.08,
              duration: 0.32,
              ease: 'power2.out',
              stagger: 0.035,
              from: { y: 7, scale: 0.92, autoAlpha: 0.45 },
              to: { y: 0, scale: 1, autoAlpha: 1 },
            },
            {
              id: 'next-pagination',
              driver: 'waapi',
              targetPart: 'pagination',
              timelineKind: 'timed',
              at: 0.12,
              duration: 0.24,
              ease: 'power2.out',
              stagger: 0,
              from: { x: 6, autoAlpha: 0.55 },
              to: { x: 0, autoAlpha: 1 },
            },
          ],
        },
      ],
    },
  ],
};

export const VERIFY_COMPONENT_EXTENSION: SayHiComponentExtension = {
  componentId: SAYHI_COMPONENT_ID,
  componentVersion: SAYHI_COMPONENT_VERSION,
  storyId: 'story.verify-methods-standalone',
  anatomy,
  runtime: {
    kind: 'portable-web-component',
    route: '/verify/',
    projection: 'native-penpot/v2',
  },
  tokenBindings: VERIFY_TOKEN_BINDINGS,
  nativeTokenTypes: VERIFY_NATIVE_TOKEN_TYPES,
  motion,
};

/**
 * DTCG Resolver 2025.10 is the canonical orchestration document. Token Format
 * documents remain inline sources; color scheme is a real modifier; SayHi's
 * component contract is vendor metadata on the Components set, where the
 * Resolver specification permits it.
 */
export const VERIFY_DTCG_RESOLVER: DtcgResolverDocument = {
  $schema: DTCG_RESOLVER_2025_10_SCHEMA,
  name: 'SayHi Verify',
  version: '2025.10',
  description: 'Portable design tokens for the SayHi Verify component.',
  sets: {
    Foundation: {
      description: 'Raw reusable values shared by SayHi components.',
      sources: [primitives],
    },
    Components: {
      description: 'Component-scoped aliases and values for SayHi Verify.',
      sources: [verify],
      $extensions: {
        'io.sayhi.component':
          VERIFY_COMPONENT_EXTENSION as unknown as JsonObject,
      },
    },
  },
  modifiers: {
    colorScheme: {
      description: 'Color scheme applied to semantic color aliases.',
      contexts: {
        light: [light],
        dark: [dark],
      },
      default: 'light',
      $extensions: {
        'io.sayhi.penpot': {
          setPrefix: 'Semantic',
          themeGroup: 'Color scheme',
        },
      },
    },
  },
  resolutionOrder: [
    { $ref: '#/sets/Foundation' },
    { $ref: '#/modifiers/colorScheme' },
    { $ref: '#/sets/Components' },
  ],
};

/**
 * Preserved v1 fixture for comparison and rollback while Resolver projection
 * is proven. New importer calls must use VERIFY_DTCG_RESOLVER.
 */
export const VERIFY_LEGACY_DTCG_PACKAGE: SayHiDtcgPackage = {
  schemaName: 'io.sayhi.dtcg-package',
  schemaVersion: '0.1.0',
  sets: [
    { name: 'Foundation', active: true, document: primitives },
    { name: 'Semantic/Light', active: true, document: light },
    { name: 'Semantic/Dark', active: false, document: dark },
    // Component aliases depend on primitive and semantic values. Keep
    // provisioning dependency order separate from the Tokens panel's display
    // preference, which selects the component set explicitly.
    { name: 'Components', active: true, document: verify },
  ],
  themes: [
    {
      group: 'Color scheme',
      name: 'Light',
      sets: ['Foundation', 'Semantic/Light', 'Components'],
      active: true,
    },
    {
      group: 'Color scheme',
      name: 'Dark',
      sets: ['Foundation', 'Semantic/Dark', 'Components'],
      active: false,
    },
  ],
  $extensions: {
    'io.sayhi.component': VERIFY_COMPONENT_EXTENSION,
  },
};

/** @deprecated Use VERIFY_DTCG_RESOLVER for new importer paths. */
export const VERIFY_DTCG_PACKAGE = VERIFY_LEGACY_DTCG_PACKAGE;
