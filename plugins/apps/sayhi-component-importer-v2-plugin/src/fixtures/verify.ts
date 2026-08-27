import type { JsonObject } from '../dtcg-contract.js';
import {
  DTCG_2025_10_SCHEMA,
  DTCG_RESOLVER_2025_10_SCHEMA,
} from '../dtcg-contract.js';
import type {
  ComponentProjectionManifest,
  Metric,
  ProjectionFrame,
  ProjectionNode,
} from '../projection-contract.js';

const px = (value: number): JsonObject => ({ value, unit: 'px' });
const ms = (value: number): JsonObject => ({ value, unit: 'ms' });
const ref = (token: string): { token: string } => ({ token });
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

const document = (
  description: string,
  tokens: Record<string, unknown>,
): JsonObject => ({
  $schema: DTCG_2025_10_SCHEMA,
  $description: description,
  ...tokens,
});

const foundation = document('Reusable primitive values.', {
  color: {
    white: { $type: 'color', $value: color('#ffffff') },
    ink: { $type: 'color', $value: color('#171a17') },
    muted: { $type: 'color', $value: color('#697169') },
    green: { $type: 'color', $value: color('#315f47') },
    line: { $type: 'color', $value: color('#dce1da') },
    panel: { $type: 'color', $value: color('#eef1eb') },
    darkInk: { $type: 'color', $value: color('#eef1ed') },
    darkMuted: { $type: 'color', $value: color('#aab2aa') },
    darkGreen: { $type: 'color', $value: color('#8bb39a') },
    darkLine: { $type: 'color', $value: color('#3b423c') },
    darkPanel: { $type: 'color', $value: color('#202520') },
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
      quick: {
        $type: 'cubicBezier',
        $value: [0, 0, 0.58, 1],
      },
      selection: {
        $type: 'cubicBezier',
        $value: [0.22, 1, 0.36, 1],
      },
    },
  },
});

const light = document('Light color semantics.', {
  color: {
    background: {
      panel: { $type: 'color', $value: '{color.panel}' },
      control: { $type: 'color', $value: '{color.white}' },
    },
    text: {
      primary: { $type: 'color', $value: '{color.ink}' },
      muted: { $type: 'color', $value: '{color.muted}' },
      accent: { $type: 'color', $value: '{color.green}' },
    },
    border: { subtle: { $type: 'color', $value: '{color.line}' } },
  },
});

const dark = document('Dark color semantics.', {
  color: {
    background: {
      panel: { $type: 'color', $value: '{color.darkPanel}' },
      control: { $type: 'color', $value: '{color.darkPanel}' },
    },
    text: {
      primary: { $type: 'color', $value: '{color.darkInk}' },
      muted: { $type: 'color', $value: '{color.darkMuted}' },
      accent: { $type: 'color', $value: '{color.darkGreen}' },
    },
    border: { subtle: { $type: 'color', $value: '{color.darkLine}' } },
  },
});

const components = document('Verify component decisions and geometry.', {
  verify: {
    panel: {
      width: { $type: 'dimension', $value: px(540) },
      height: { $type: 'dimension', $value: px(494.421875) },
      background: { $type: 'color', $value: '{color.background.panel}' },
      border: { $type: 'color', $value: '{color.border.subtle}' },
      radius: { $type: 'dimension', $value: px(24) },
    },
    header: {
      x: { $type: 'dimension', $value: px(21) },
      y: { $type: 'dimension', $value: px(35) },
      width: { $type: 'dimension', $value: px(498) },
      height: { $type: 'dimension', $value: px(105.078125) },
    },
    selector: {
      x: { $type: 'dimension', $value: px(-60) },
      y: { $type: 'dimension', $value: px(162.078125) },
      width: { $type: 'dimension', $value: px(660) },
      height: { $type: 'dimension', $value: px(204) },
    },
    method: {
      background: { $type: 'color', $value: '{color.background.control}' },
      border: { $type: 'color', $value: '{color.border.subtle}' },
      side: {
        width: { $type: 'dimension', $value: px(136.8) },
        height: { $type: 'dimension', $value: px(117.04) },
        radius: { $type: 'dimension', $value: px(16.72) },
        icon: { $type: 'dimension', $value: px(27.36) },
      },
      active: {
        x: { $type: 'dimension', $value: px(172.799988) },
        y: { $type: 'dimension', $value: px(169.918121) },
        width: { $type: 'dimension', $value: px(194.400024) },
        height: { $type: 'dimension', $value: px(166.320007) },
        radius: { $type: 'dimension', $value: px(23.76) },
        icon: { $type: 'dimension', $value: px(38.880005) },
      },
    },
    text: {
      primary: { $type: 'color', $value: '{color.text.primary}' },
      muted: { $type: 'color', $value: '{color.text.muted}' },
      accent: { $type: 'color', $value: '{color.text.accent}' },
    },
    font: {
      family: { $type: 'fontFamily', $value: '{font.family.sans}' },
      regular: { $type: 'fontWeight', $value: '{font.weight.regular}' },
      medium: { $type: 'fontWeight', $value: '{font.weight.medium}' },
      strong: { $type: 'fontWeight', $value: '{font.weight.strong}' },
    },
    typography: {
      eyebrow: {
        $type: 'typography',
        $value: {
          fontFamily: '{verify.font.family}',
          fontSize: px(11.2),
          fontWeight: 700,
          letterSpacing: px(1.344),
          lineHeight: 1.4,
        },
      },
      heading: {
        $type: 'typography',
        $value: {
          fontFamily: '{verify.font.family}',
          fontSize: px(42.4),
          fontWeight: 570,
          letterSpacing: px(-2.2048),
          lineHeight: 1,
        },
      },
      body: {
        $type: 'typography',
        $value: {
          fontFamily: '{verify.font.family}',
          fontSize: px(16),
          fontWeight: 400,
          letterSpacing: px(0),
          lineHeight: 1.5,
        },
      },
      methodLabel: {
        $type: 'typography',
        $value: {
          fontFamily: '{verify.font.family}',
          fontSize: px(16),
          fontWeight: 710,
          letterSpacing: px(-0.4),
          lineHeight: 1.014341375,
        },
      },
      methodDescription: {
        $type: 'typography',
        $value: {
          fontFamily: '{verify.font.family}',
          fontSize: px(11.52),
          fontWeight: 560,
          letterSpacing: px(0),
          lineHeight: 1.125,
        },
      },
      hint: {
        $type: 'typography',
        $value: {
          fontFamily: '{verify.font.family}',
          fontSize: px(11.52),
          fontWeight: 400,
          letterSpacing: px(0),
          lineHeight: 1.4,
        },
      },
      cancel: {
        $type: 'typography',
        $value: {
          fontFamily: '{verify.font.family}',
          fontSize: px(13.12),
          fontWeight: 650,
          letterSpacing: px(0),
          lineHeight: 1.4,
        },
      },
    },
  },
});

const motion: JsonObject = {
  schemaName: 'sayhi.motion',
  schemaVersion: '0.5.0',
  revision: 0,
  tokenRevision: 'verify-projection-v2.4',
  programs: [
    {
      id: 'verify-selection-response',
      label: 'Verify selection response',
      trigger: { type: 'event', event: 'selection.change' },
      reducedMotion: 'skip',
      responses: [
        {
          id: 'previous',
          label: 'Previous method',
          direction: 'previous',
          match: { direction: 'previous' },
          steps: [
            {
              id: 'previous-coverflow',
              driver: 'css-transition',
              targetPart: 'method',
              operation: {
                type: 'cycle',
                groupId: 'verification-methods',
                slotAppearance: {
                  activeParts: ['active-ring', 'active-face'],
                },
              },
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
              ease: '{motion.easing.quick}',
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
              ease: '{motion.easing.quick}',
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
              id: 'next-coverflow',
              driver: 'css-transition',
              targetPart: 'method',
              operation: {
                type: 'cycle',
                groupId: 'verification-methods',
                slotAppearance: {
                  activeParts: ['active-ring', 'active-face'],
                },
              },
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
              ease: '{motion.easing.quick}',
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
              ease: '{motion.easing.quick}',
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

const componentExtension: JsonObject = {
  componentId: 'sayhi.verification-method-selector',
  componentVersion: '0.5.4-projection-v2',
  storyId: 'story.verify-methods-standalone',
  anatomy: {
    root: 'Verify-root',
    parts: {
      panel: ['Panel-face'],
      heading: ['Heading'],
      selector: ['Verification-methods'],
      method: ['Email-method', 'Passkey-method', 'Authenticator-method'],
      'method-icon': ['Email-icon', 'Passkey-icon', 'Authenticator-icon'],
      pagination: ['Pagination'],
      help: ['Hint-copy'],
      cancel: ['Cancel-action'],
      'active-ring': [
        'Email-active-ring',
        'Passkey-active-ring',
        'Authenticator-active-ring',
      ],
      'active-face': [
        'Email-active-face',
        'Passkey-active-face',
        'Authenticator-active-face',
      ],
    },
  },
  runtime: {
    kind: 'portable-web-component',
    route: '/verify/',
    component: 'sayhi.verification-method-selector',
    story: 'story.verify-methods-standalone',
  },
  tokenBindings: [],
  nativeTokenTypes: [],
  motion,
};

const emailSvg =
  '<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 32 32" fill="none" stroke="#171a17" stroke-width="1.8"><rect width="32" height="32" fill="#000" fill-opacity="0" stroke="none"/><rect x="4" y="7" width="24" height="18" rx="3"/><path d="m6 10 10 8 10-8"/></svg>';
const passkeySvg =
  '<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 32 32" fill="none" stroke="#171a17" stroke-width="1.8" stroke-linecap="round"><rect width="32" height="32" fill="#000" fill-opacity="0" stroke="none"/><path d="M16 5c-6 0-10 4.3-10 10.1M16 8c-4.2 0-7 3-7 7.2 0 5-1.2 7.3-2.4 9M16 11c-2.6 0-4 1.8-4 4.5 0 5.8-1.1 8.3-2.2 10.5M16 14c1.5 0 2 1 2 2.5 0 5.4-.8 8.1-1.6 10.5M20 12.5c1.4 1.3 2 3 2 5.2 0 4-.5 6.7-1.2 8.8M23 9.5c2.1 2 3 4.6 3 8.2 0 2.9-.3 5.1-.7 7"/></svg>';
const authenticatorSvg =
  '<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 32 32" fill="#171a17"><rect width="32" height="32" fill="#000" fill-opacity="0"/><circle cx="9" cy="9" r="2"/><circle cx="16" cy="9" r="2"/><circle cx="23" cy="9" r="2"/><circle cx="9" cy="16" r="2"/><circle cx="16" cy="16" r="2"/><circle cx="23" cy="16" r="2"/><circle cx="9" cy="23" r="2"/><circle cx="16" cy="23" r="2"/><circle cx="23" cy="23" r="2"/></svg>';

const metric = (token: string): Metric => ref(token);

const textNode = (
  id: string,
  name: string,
  characters: string,
  frame: [number, number, number, number],
  style: {
    size: number;
    weight: number;
    lineHeightPx: number;
    tracking?: number;
    color?: string;
    transform?: 'none' | 'uppercase';
  },
  motionPart?: string,
): ProjectionNode => ({
  id,
  name,
  type: 'text',
  frame: {
    x: frame[0],
    y: frame[1],
    width: frame[2],
    height: frame[3],
  },
  characters,
  motionPart,
  style: {
    fontFamily: ref('verify.font.family'),
    fontSize: style.size,
    fontWeight: style.weight,
    lineHeight: style.lineHeightPx / style.size,
    letterSpacing: style.tracking ?? 0,
    color: ref(style.color ?? 'verify.text.primary'),
    align: 'center',
    verticalAlign: 'center',
    textTransform: style.transform ?? 'none',
  },
});

const activeRingNode = (
  id: string,
  name: string,
  frame: ProjectionFrame,
  radius: Metric,
  opacity: number,
): ProjectionNode => ({
  id,
  name,
  type: 'rectangle',
  motionPart: 'active-ring',
  frame,
  radius,
  opacity,
  fills: [
    {
      gradient: {
        type: 'linear',
        angle: 32,
        stops: [
          { offset: 0, color: '#55e6f0' },
          { offset: 0.22, color: '#8069ff' },
          { offset: 0.48, color: '#f15ac8' },
          { offset: 0.72, color: '#f6ca5b' },
          { offset: 1, color: '#58d891' },
        ],
      },
    },
  ],
});

const activeFaceNode = (
  id: string,
  name: string,
  frame: ProjectionFrame,
  radius: Metric,
  opacity: number,
): ProjectionNode => ({
  id,
  name,
  type: 'rectangle',
  motionPart: 'active-face',
  frame,
  radius,
  opacity,
  fills: [{ color: ref('verify.method.background') }],
  shadows: [
    {
      color: '#28372b',
      opacity: 0.12,
      offsetX: 0,
      offsetY: 10,
      blur: 28,
      spread: 0,
    },
  ],
});

export const VERIFY_PROJECTION_MANIFEST: ComponentProjectionManifest = {
  schemaName: 'io.sayhi.component-projection',
  schemaVersion: '0.2.0',
  component: {
    id: 'sayhi.verification-method-selector',
    version: '0.5.4-projection-v2',
    name: 'SayHi Verify V2',
    libraryPath: 'SayHi Projection V2',
    storyId: 'story.verify-methods-standalone',
  },
  resolver: {
    $schema: DTCG_RESOLVER_2025_10_SCHEMA,
    name: 'Verify projection V2',
    version: '2025.10',
    description: 'DTCG sources used by the manifest-driven Verify projection.',
    sets: {
      Foundation: { sources: [foundation] },
      Components: {
        sources: [components],
        $extensions: {
          'io.sayhi.component': componentExtension,
          'io.sayhi.penpot': { nativeSetPrefix: 'Projection V2' },
        },
      },
    },
    modifiers: {
      colorScheme: {
        contexts: { light: [light], dark: [dark] },
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
  },
  $extensions: {
    'io.sayhi.component': componentExtension,
    'io.sayhi.penpot': { absoluteFrames: true },
  },
  root: {
    id: 'verify.root',
    name: 'Verify-root',
    type: 'board',
    frame: {
      x: 0,
      y: 0,
      width: metric('verify.panel.width'),
      height: metric('verify.panel.height'),
    },
    clipContent: false,
    showInViewMode: true,
    fills: [{ color: '#ffffff', opacity: 0 }],
    children: [
      {
        id: 'verify.panel.face',
        name: 'Panel-face',
        type: 'rectangle',
        motionPart: 'panel',
        frame: {
          x: 0,
          y: 0,
          width: metric('verify.panel.width'),
          height: metric('verify.panel.height'),
        },
        radius: metric('verify.panel.radius'),
        fills: [{ color: ref('verify.panel.background') }],
        strokes: [
          {
            color: ref('verify.panel.border'),
            width: 1,
            opacity: 0.82,
            alignment: 'inner',
          },
        ],
        shadows: [
          {
            color: '#171916',
            opacity: 0.12,
            offsetX: 0,
            offsetY: 24,
            blur: 72,
            spread: 0,
          },
        ],
        tokenBindings: [
          {
            token: 'verify.panel.background',
            type: 'color',
            properties: ['fill'],
          },
          {
            token: 'verify.panel.radius',
            type: 'borderRadius',
            properties: [
              'borderRadiusTopLeft',
              'borderRadiusTopRight',
              'borderRadiusBottomRight',
              'borderRadiusBottomLeft',
            ],
          },
          {
            token: 'verify.panel.border',
            type: 'color',
            properties: ['strokeColor'],
          },
        ],
      },
      {
        id: 'verify.heading',
        name: 'Heading',
        type: 'board',
        motionPart: 'heading',
        frame: {
          x: metric('verify.header.x'),
          y: metric('verify.header.y'),
          width: metric('verify.header.width'),
          height: metric('verify.header.height'),
        },
        clipContent: false,
        fills: [{ color: '#ffffff', opacity: 0 }],
        children: [
          textNode(
            'verify.eyebrow',
            'Eyebrow-copy',
            'SAYHI VERIFY',
            [21, 35, 498, 15.671875],
            {
              size: 11.2,
              weight: 700,
              lineHeightPx: 15.68,
              tracking: 1.344,
              color: 'verify.text.accent',
              transform: 'uppercase',
            },
            'eyebrow',
          ),
          textNode(
            'verify.title',
            'Heading-copy',
            'Verify it’s you',
            [21, 61.671875, 498, 42.40625],
            {
              size: 42.4,
              weight: 570,
              lineHeightPx: 42.4,
              tracking: -2.2048,
            },
            'heading',
          ),
          textNode(
            'verify.subtitle',
            'Body-copy',
            'Choose how you’d like to verify.',
            [21, 116.078125, 498, 24],
            {
              size: 16,
              weight: 400,
              lineHeightPx: 24,
              color: 'verify.text.muted',
            },
            'heading',
          ),
        ],
      },
      {
        id: 'verify.selector',
        name: 'Verification-methods',
        type: 'board',
        motionPart: 'selector',
        frame: {
          x: metric('verify.selector.x'),
          y: metric('verify.selector.y'),
          width: metric('verify.selector.width'),
          height: metric('verify.selector.height'),
        },
        clipContent: false,
        fills: [{ color: '#ffffff', opacity: 0 }],
        children: [
          {
            id: 'verify.method.email',
            name: 'Email-method',
            type: 'board',
            motionPart: 'method',
            frame: {
              x: 40.7875,
              y: 218.558125,
              width: metric('verify.method.side.width'),
              height: metric('verify.method.side.height'),
            },
            opacity: 0.66,
            rotation: -2.4,
            clipContent: false,
            fills: [{ color: '#ffffff', opacity: 0 }],
            children: [
              activeRingNode(
                'verify.method.email.ring',
                'Email-active-ring',
                {
                  x: 37.7475,
                  y: 215.518125,
                  width: 142.88,
                  height: 123.12,
                },
                19.76,
                0,
              ),
              {
                id: 'verify.method.email.face',
                name: 'Email-method-face',
                type: 'rectangle',
                frame: {
                  x: 40.7875,
                  y: 218.558125,
                  width: metric('verify.method.side.width'),
                  height: metric('verify.method.side.height'),
                },
                radius: metric('verify.method.side.radius'),
                fills: [
                  { color: ref('verify.method.background'), opacity: 0.88 },
                ],
                strokes: [
                  {
                    color: ref('verify.method.border'),
                    width: 1,
                    alignment: 'inner',
                  },
                ],
              },
              activeFaceNode(
                'verify.method.email.active-face',
                'Email-active-face',
                {
                  x: 40.7875,
                  y: 218.558125,
                  width: metric('verify.method.side.width'),
                  height: metric('verify.method.side.height'),
                },
                metric('verify.method.side.radius'),
                0,
              ),
              {
                id: 'verify.method.email.icon',
                name: 'Email-icon',
                type: 'svg',
                motionPart: 'method-icon',
                frame: {
                  x: 95.507507,
                  y: 245.918115,
                  width: metric('verify.method.side.icon'),
                  height: metric('verify.method.side.icon'),
                },
                markup: emailSvg,
              },
              textNode(
                'verify.method.email.label',
                'Email-label',
                'Email code',
                [40.7875, 275.938132, 136.8, 20.52],
                {
                  size: 12.16,
                  weight: 710,
                  lineHeightPx: 16.229462,
                  tracking: -0.304,
                },
                'method-label',
              ),
              textNode(
                'verify.method.email.description',
                'Email-description',
                'Send a code',
                [40.7875, 296.63814, 136.8, 13.68],
                {
                  size: 8.7552,
                  weight: 560,
                  lineHeightPx: 11.271149,
                  color: 'verify.text.muted',
                },
                'method-description',
              ),
            ],
          },
          {
            id: 'verify.method.authenticator',
            name: 'Authenticator-method',
            type: 'board',
            motionPart: 'method',
            frame: {
              x: 362.39689,
              y: 218.558125,
              width: metric('verify.method.side.width'),
              height: metric('verify.method.side.height'),
            },
            opacity: 0.66,
            rotation: 2.4,
            clipContent: false,
            fills: [{ color: '#ffffff', opacity: 0 }],
            children: [
              activeRingNode(
                'verify.method.authenticator.ring',
                'Authenticator-active-ring',
                {
                  x: 359.35689,
                  y: 215.518125,
                  width: 142.88,
                  height: 123.12,
                },
                19.76,
                0,
              ),
              {
                id: 'verify.method.authenticator.face',
                name: 'Authenticator-method-face',
                type: 'rectangle',
                frame: {
                  x: 362.39689,
                  y: 218.558125,
                  width: metric('verify.method.side.width'),
                  height: metric('verify.method.side.height'),
                },
                radius: metric('verify.method.side.radius'),
                fills: [
                  { color: ref('verify.method.background'), opacity: 0.88 },
                ],
                strokes: [
                  {
                    color: ref('verify.method.border'),
                    width: 1,
                    alignment: 'inner',
                  },
                ],
              },
              activeFaceNode(
                'verify.method.authenticator.active-face',
                'Authenticator-active-face',
                {
                  x: 362.39689,
                  y: 218.558125,
                  width: metric('verify.method.side.width'),
                  height: metric('verify.method.side.height'),
                },
                metric('verify.method.side.radius'),
                0,
              ),
              {
                id: 'verify.method.authenticator.icon',
                name: 'Authenticator-icon',
                type: 'svg',
                motionPart: 'method-icon',
                frame: {
                  x: 417.116836,
                  y: 245.918118,
                  width: metric('verify.method.side.icon'),
                  height: metric('verify.method.side.icon'),
                },
                markup: authenticatorSvg,
              },
              textNode(
                'verify.method.authenticator.label',
                'Authenticator-label',
                'Authenticator',
                [362.39689, 275.938132, 136.8, 20.52],
                {
                  size: 12.16,
                  weight: 710,
                  lineHeightPx: 16.812744,
                  tracking: -0.304,
                },
                'method-label',
              ),
              textNode(
                'verify.method.authenticator.description',
                'Authenticator-description',
                'Use your app',
                [362.39689, 296.63814, 136.8, 13.68],
                {
                  size: 8.7552,
                  weight: 560,
                  lineHeightPx: 11.433777,
                  color: 'verify.text.muted',
                },
                'method-description',
              ),
            ],
          },
          {
            id: 'verify.method.passkey',
            name: 'Passkey-method',
            type: 'board',
            motionPart: 'method',
            frame: {
              x: metric('verify.method.active.x'),
              y: metric('verify.method.active.y'),
              width: metric('verify.method.active.width'),
              height: metric('verify.method.active.height'),
            },
            clipContent: false,
            fills: [{ color: '#ffffff', opacity: 0 }],
            children: [
              activeRingNode(
                'verify.method.passkey.ring',
                'Passkey-active-ring',
                {
                  x: 168.479988,
                  y: 165.598121,
                  width: 203.040024,
                  height: 174.960007,
                },
                27,
                1,
              ),
              {
                id: 'verify.method.passkey.face',
                name: 'Passkey-method-face',
                type: 'rectangle',
                frame: {
                  x: metric('verify.method.active.x'),
                  y: metric('verify.method.active.y'),
                  width: metric('verify.method.active.width'),
                  height: metric('verify.method.active.height'),
                },
                radius: metric('verify.method.active.radius'),
                fills: [
                  {
                    color: ref('verify.method.background'),
                    opacity: 0.88,
                  },
                ],
                strokes: [
                  {
                    color: ref('verify.method.border'),
                    width: 1,
                    alignment: 'inner',
                  },
                ],
              },
              activeFaceNode(
                'verify.method.passkey.active-face',
                'Passkey-active-face',
                {
                  x: metric('verify.method.active.x'),
                  y: metric('verify.method.active.y'),
                  width: metric('verify.method.active.width'),
                  height: metric('verify.method.active.height'),
                },
                metric('verify.method.active.radius'),
                1,
              ),
              {
                id: 'verify.method.passkey.icon',
                name: 'Passkey-icon',
                type: 'svg',
                motionPart: 'method-icon',
                frame: {
                  x: 250.559998,
                  y: 208.798126,
                  width: metric('verify.method.active.icon'),
                  height: metric('verify.method.active.icon'),
                },
                markup: passkeySvg,
              },
              textNode(
                'verify.method.passkey.label',
                'Passkey-label',
                'Passkey',
                [172.799988, 252.478115, 194.400024, 27.12],
                {
                  size: 17.28,
                  weight: 710,
                  lineHeightPx: 19.440002,
                  tracking: -0.432,
                },
                'method-label',
              ),
              textNode(
                'verify.method.passkey.description',
                'Passkey-description',
                'Continue',
                [172.799988, 280.558128, 194.400024, 21.6],
                {
                  size: 12.4416,
                  weight: 560,
                  lineHeightPx: 12.959991,
                  color: 'verify.text.muted',
                },
                'method-description',
              ),
            ],
          },
        ],
      },
      {
        id: 'verify.pagination',
        name: 'Pagination',
        type: 'board',
        motionPart: 'pagination',
        frame: { x: 21, y: 366.078125, width: 498, height: 8 },
        clipContent: false,
        fills: [{ color: '#ffffff', opacity: 0 }],
        children: [
          {
            id: 'verify.pagination.0',
            name: 'Pagination-dot',
            type: 'ellipse',
            frame: { x: 247, y: 366.078125, width: 6, height: 6 },
            opacity: 0.42,
            fills: [{ color: ref('verify.text.muted') }],
          },
          {
            id: 'verify.pagination.1',
            name: 'Pagination-active',
            type: 'rectangle',
            frame: { x: 261, y: 366.078125, width: 18, height: 6 },
            radius: 999,
            fills: [{ color: ref('verify.text.accent') }],
          },
          {
            id: 'verify.pagination.2',
            name: 'Pagination-dot',
            type: 'ellipse',
            frame: { x: 287, y: 366.078125, width: 6, height: 6 },
            opacity: 0.42,
            fills: [{ color: ref('verify.text.muted') }],
          },
        ],
      },
      textNode(
        'verify.hint',
        'Hint-copy',
        'Tab or use ← → to move between methods. Enter to continue.',
        [21, 388.078125, 498, 16.109375],
        {
          size: 11.52,
          weight: 400,
          lineHeightPx: 16.128,
          color: 'verify.text.muted',
        },
        'help',
      ),
      textNode(
        'verify.cancel',
        'Cancel-action',
        'Cancel sign-in',
        [219.5, 435.421875, 101, 30],
        {
          size: 13.12,
          weight: 650,
          lineHeightPx: 18.368,
          color: 'verify.text.accent',
        },
        'cancel',
      ),
    ],
  },
};
