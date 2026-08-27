import { describe, expect, it } from 'vitest';
import { VERIFY_PROJECTION_MANIFEST } from './fixtures/verify.js';
import {
  canonicalManifestJson,
  motionDocumentFromResolver,
  projectionCoverage,
  validateProjectionManifest,
} from './materializer.js';

describe('component projection manifest V2', () => {
  it('validates a DTCG Resolver-backed component graph', () => {
    expect(() =>
      validateProjectionManifest(VERIFY_PROJECTION_MANIFEST),
    ).not.toThrow();

    expect(projectionCoverage(VERIFY_PROJECTION_MANIFEST)).toEqual({
      nodes: 34,
      sets: 4,
      bindings: 3,
    });
  });

  it('keeps audited component coordinates in the fixture manifest', () => {
    const manifest = canonicalManifestJson(VERIFY_PROJECTION_MANIFEST);
    expect(manifest).toContain('494.421875');
    expect(manifest).toContain('172.799988');
    expect(manifest).toContain('io.sayhi.component-projection');
    expect(manifest).toContain('2025.10');
  });

  it('writes one standalone DTCG motion document with its referenced tokens', () => {
    const document = motionDocumentFromResolver(VERIFY_PROJECTION_MANIFEST);
    expect(document).toMatchObject({
      motion: {
        duration: {
          selection: {
            $type: 'duration',
            $value: { value: 420, unit: 'ms' },
          },
        },
      },
      verify: {
        method: {
          active: {
            width: {
              $type: 'dimension',
              $value: { value: 194.400024, unit: 'px' },
            },
          },
        },
      },
      $extensions: {
        'io.sayhi.motion': {
          schemaVersion: '0.5.0',
          tokenRevision: 'verify-projection-v2.4',
        },
      },
    });
  });

  it('preserves the audited Studio geometry instead of approximating it', () => {
    const nodes = new Map<
      string,
      (typeof VERIFY_PROJECTION_MANIFEST)['root']
    >();
    const pending = [VERIFY_PROJECTION_MANIFEST.root];
    while (pending.length) {
      const node = pending.shift();
      if (!node) continue;
      nodes.set(node.id, node as (typeof VERIFY_PROJECTION_MANIFEST)['root']);
      pending.push(
        ...((node.children ??
          []) as (typeof VERIFY_PROJECTION_MANIFEST)['root'][]),
      );
    }

    expect(nodes.get('verify.root')?.frame).toEqual({
      x: 0,
      y: 0,
      width: { token: 'verify.panel.width' },
      height: { token: 'verify.panel.height' },
    });
    expect(nodes.get('verify.heading')?.frame).toEqual({
      x: { token: 'verify.header.x' },
      y: { token: 'verify.header.y' },
      width: { token: 'verify.header.width' },
      height: { token: 'verify.header.height' },
    });
    expect(nodes.get('verify.selector')?.frame).toEqual({
      x: { token: 'verify.selector.x' },
      y: { token: 'verify.selector.y' },
      width: { token: 'verify.selector.width' },
      height: { token: 'verify.selector.height' },
    });
    expect(nodes.get('verify.method.passkey')?.frame).toEqual({
      x: { token: 'verify.method.active.x' },
      y: { token: 'verify.method.active.y' },
      width: { token: 'verify.method.active.width' },
      height: { token: 'verify.method.active.height' },
    });
    expect(nodes.get('verify.method.passkey.icon')?.frame).toEqual({
      x: 250.559998,
      y: 208.798126,
      width: { token: 'verify.method.active.icon' },
      height: { token: 'verify.method.active.icon' },
    });
    expect(nodes.get('verify.pagination')?.frame).toEqual({
      x: 21,
      y: 366.078125,
      width: 498,
      height: 8,
    });
    expect(nodes.get('verify.hint')?.frame).toEqual({
      x: 21,
      y: 388.078125,
      width: 498,
      height: 16.109375,
    });
    expect(nodes.get('verify.cancel')?.frame).toEqual({
      x: 219.5,
      y: 435.421875,
      width: 101,
      height: 30,
    });

    const title = nodes.get('verify.title');
    expect(title?.type).toBe('text');
    if (title?.type === 'text') {
      expect(title.style.fontSize).toBe(42.4);
      expect(title.style.lineHeight).toBe(1);
    }
    const eyebrow = nodes.get('verify.eyebrow');
    expect(eyebrow?.type).toBe('text');
    if (eyebrow?.type === 'text') {
      expect(eyebrow.style.lineHeight).toBeCloseTo(1.4, 8);
    }

    const email = nodes.get('verify.method.email');
    expect(email?.rotation).toBe(-2.4);
    expect(email?.frame.width).toEqual({
      token: 'verify.method.side.width',
    });
    expect(email?.frame.height).toEqual({
      token: 'verify.method.side.height',
    });
    expect(
      rotatedBounds(
        email && {
          ...email.frame,
          width: 136.8,
          height: 117.04,
        },
        -2.4,
      ),
    ).toEqual({
      x: 38.396935,
      y: 215.745162,
      width: 141.58113,
      height: 122.665925,
    });
    const authenticator = nodes.get('verify.method.authenticator');
    expect(authenticator?.rotation).toBe(2.4);
    expect(
      rotatedBounds(
        authenticator && {
          ...authenticator.frame,
          width: 136.8,
          height: 117.04,
        },
        2.4,
      ),
    ).toEqual({
      x: 360.006325,
      y: 215.745162,
      width: 141.58113,
      height: 122.665925,
    });

    for (const method of ['email', 'passkey', 'authenticator']) {
      expect(nodes.get(`verify.method.${method}.ring`)).toMatchObject({
        motionPart: 'active-ring',
        opacity: method === 'passkey' ? 1 : 0,
      });
      expect(nodes.get(`verify.method.${method}.active-face`)).toMatchObject({
        motionPart: 'active-face',
        opacity: method === 'passkey' ? 1 : 0,
      });
    }
  });

  it('rejects duplicate stable node IDs', () => {
    const duplicate = structuredClone(VERIFY_PROJECTION_MANIFEST);
    const first = duplicate.root.children?.[0];
    const second = duplicate.root.children?.[1];
    expect(first).toBeDefined();
    expect(second).toBeDefined();
    if (!first || !second) return;
    second.id = first.id;

    expect(() => validateProjectionManifest(duplicate)).toThrow(
      /Duplicate projection node/,
    );
  });
});

function rotatedBounds(
  frame:
    { x: unknown; y: unknown; width: unknown; height: unknown } | undefined,
  degrees: number,
): { x: number; y: number; width: number; height: number } | undefined {
  if (
    !frame ||
    typeof frame.x !== 'number' ||
    typeof frame.y !== 'number' ||
    typeof frame.width !== 'number' ||
    typeof frame.height !== 'number'
  ) {
    return undefined;
  }
  const radians = (degrees * Math.PI) / 180;
  const width =
    Math.abs(frame.width * Math.cos(radians)) +
    Math.abs(frame.height * Math.sin(radians));
  const height =
    Math.abs(frame.width * Math.sin(radians)) +
    Math.abs(frame.height * Math.cos(radians));
  return {
    x: round(frame.x + frame.width / 2 - width / 2),
    y: round(frame.y + frame.height / 2 - height / 2),
    width: round(width),
    height: round(height),
  };
}

function round(value: number): number {
  return Number(value.toFixed(6));
}
