import { describe, expect, it } from 'vitest';
import {
  canonicalPackageJson,
  materializePackage,
  roundTripPackage,
  validateDtcgDocument,
} from './dtcg.js';
import {
  DTCG_2025_10_SCHEMA,
  type DtcgDocument,
  type SayHiDtcgPackage,
} from './contract.js';
import {
  VERIFY_DTCG_PACKAGE,
  VERIFY_NATIVE_TOKEN_TYPES,
  VERIFY_TOKEN_BINDINGS,
} from './verify-package.js';

describe('Verify DTCG package', () => {
  it('uses only DTCG 2025.10 documents and preserves the package losslessly', () => {
    for (const set of VERIFY_DTCG_PACKAGE.sets) {
      expect(() => validateDtcgDocument(set.document)).not.toThrow();
      expect(set.document.$schema).toBe(DTCG_2025_10_SCHEMA);
    }

    const serialized = canonicalPackageJson(VERIFY_DTCG_PACKAGE);
    const parsed = JSON.parse(serialized) as SayHiDtcgPackage;
    expect(parsed).toEqual(VERIFY_DTCG_PACKAGE);
    expect(parsed.$extensions['io.sayhi.component'].motion).toBeDefined();
  });

  it('treats Format $schema as optional tooling metadata', () => {
    const source = structuredClone(VERIFY_DTCG_PACKAGE.sets[0].document);
    delete source.$schema;
    expect(() => validateDtcgDocument(source)).not.toThrow();

    source.$schema = 'https://example.com/not-dtcg.json';
    expect(() => validateDtcgDocument(source)).toThrow(
      'Expected DTCG 2025.10 schema',
    );
  });

  it('materializes native Penpot sets, themes, aliases and binding type hints', () => {
    const result = materializePackage(VERIFY_DTCG_PACKAGE);
    const sets = new Map(result.sets.map((set) => [set.name, set]));
    const light = sets.get('Semantic/Light');
    const verify = sets.get('Components');

    expect(result.sets).toHaveLength(4);
    expect(result.themes).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ name: 'Light', active: true }),
        expect.objectContaining({ name: 'Dark', active: false }),
      ]),
    );
    expect(
      light?.tokens.find((token) => token.name === 'color.background.panel')
        ?.value,
    ).toBe('{color.base.panel}');
    expect(
      verify?.tokens.find(
        (token) => token.name === 'verify.panel.border-radius',
      ),
    ).toEqual(
      expect.objectContaining({ penpotType: 'borderRadius', value: '24px' }),
    );
    expect(
      verify?.tokens.find((token) => token.name === 'verify.heading.font-size'),
    ).toEqual(
      expect.objectContaining({ penpotType: 'fontSizes', value: '38px' }),
    );
    expect(
      verify?.tokens.find(
        (token) => token.name === 'verify.font.weight.medium',
      ),
    ).toEqual(
      expect.objectContaining({
        penpotType: 'fontWeights',
        value: '{font.weight.medium}',
      }),
    );
    expect(
      verify?.tokens.find(
        (token) => token.name === 'verify.typography.heading',
      ),
    ).toEqual(
      expect.objectContaining({
        penpotType: 'typography',
        value: expect.objectContaining({
          fontFamilies: '{verify.font.family}',
          fontSizes: '{verify.heading.font-size}',
          fontWeight: '{verify.font.weight.medium}',
          letterSpacing: '{verify.heading.letter-spacing}',
        }),
      }),
    );
    expect(
      sets
        .get('Foundation')
        ?.tokens.filter((token) => token.dtcgType === 'fontWeight'),
    ).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          name: 'font.weight.medium',
          penpotType: 'fontWeights',
          sourceValue: 560,
          value: '600',
        }),
        expect.objectContaining({
          name: 'font.weight.strong',
          penpotType: 'fontWeights',
          sourceValue: 710,
          value: '700',
        }),
      ]),
    );
    expect(
      verify?.tokens.find(
        (token) => token.name === 'verify.method.active.width',
      ),
    ).toEqual(
      expect.objectContaining({ penpotType: 'sizing', value: '192px' }),
    );
    expect(
      verify?.tokens.find(
        (token) => token.name === 'verify.method.side.icon-size',
      ),
    ).toEqual(
      expect.objectContaining({ penpotType: 'sizing', value: '27.36px' }),
    );
    expect(
      verify?.tokens.find(
        (token) => token.name === 'verify.method.active.icon-size',
      ),
    ).toEqual(
      expect.objectContaining({ penpotType: 'sizing', value: '38.88px' }),
    );
    expect(
      verify?.tokens.find(
        (token) => token.name === 'verify.heading.letter-spacing',
      ),
    ).toEqual(
      expect.objectContaining({ penpotType: 'letterSpacing', value: '-1.8px' }),
    );
    expect(result.unsupported.map((token) => token.dtcgType)).toEqual(
      expect.arrayContaining(['duration', 'cubicBezier']),
    );
  });

  it('has a token for every native shape binding', () => {
    const result = materializePackage(VERIFY_DTCG_PACKAGE);
    const materializedNames = new Set(
      result.sets.flatMap((set) => set.tokens.map((token) => token.name)),
    );

    for (const binding of VERIFY_TOKEN_BINDINGS) {
      expect(materializedNames.has(binding.token), binding.token).toBe(true);
    }
  });

  it('round-trips native Penpot values without losing unsupported DTCG values or extensions', () => {
    const materialized = materializePackage(VERIFY_DTCG_PACKAGE);
    const sets = materialized.sets.map((set) => ({
      name: set.name,
      active: set.active,
      tokens: set.tokens.map((token) => ({
        name: token.name,
        value: structuredClone(token.value!),
        description: token.description ?? '',
      })),
    }));
    const themes = materialized.themes.map((theme) => ({
      group: theme.group,
      name: theme.name,
      active: theme.active,
      sets: [...theme.sets],
    }));

    expect(roundTripPackage(VERIFY_DTCG_PACKAGE, sets, themes)).toEqual(
      VERIFY_DTCG_PACKAGE,
    );

    const primitiveSet = sets.find((set) => set.name === 'Foundation');
    const medium = primitiveSet?.tokens.find(
      (token) => token.name === 'font.weight.medium',
    );
    const strong = primitiveSet?.tokens.find(
      (token) => token.name === 'font.weight.strong',
    );
    expect(medium?.value).toBe('600');
    expect(strong?.value).toBe('700');

    const panel = primitiveSet?.tokens.find(
      (token) => token.name === 'color.base.panel',
    );
    if (!panel) throw new Error('Missing panel token fixture.');
    panel.value = '#ff00aa';
    const changed = roundTripPackage(VERIFY_DTCG_PACKAGE, sets, themes);
    const changedPrimitives = changed.sets.find(
      (set) => set.name === 'Foundation',
    );
    const value = (
      changedPrimitives?.document as unknown as {
        color: { base: { panel: { $value: { hex: string } } } };
      }
    ).color.base.panel.$value;
    expect(value.hex).toBe('#ff00aa');
    expect(changed.$extensions['io.sayhi.component'].motion).toEqual(
      VERIFY_DTCG_PACKAGE.$extensions['io.sayhi.component'].motion,
    );

    if (!medium) throw new Error('Missing medium weight token fixture.');
    medium.value = '500';
    const changedWeight = roundTripPackage(VERIFY_DTCG_PACKAGE, sets, themes);
    const changedWeightPrimitives = changedWeight.sets.find(
      (set) => set.name === 'Foundation',
    );
    expect(
      (
        changedWeightPrimitives?.document as unknown as {
          font: { weight: { medium: { $value: number } } };
        }
      ).font.weight.medium.$value,
    ).toBe(500);
  });

  it('preserves DTCG dimension units while adapting values to Penpot canvas strings', () => {
    const source = structuredClone(VERIFY_DTCG_PACKAGE);
    const componentSet = source.sets.find((set) => set.name === 'Components');
    if (!componentSet) throw new Error('Missing Verify component set.');
    const verify = componentSet.document as unknown as {
      verify: {
        heading: { 'font-size': { $value: unknown } };
        body: { 'font-size': { $value: unknown } };
      };
    };
    verify.verify.heading['font-size'].$value = {
      value: 2.375,
      unit: 'rem',
    };
    verify.verify.body['font-size'].$value = '{verify.heading.font-size}';

    const materialized = materializePackage(source);
    const componentTokens = materialized.sets.find(
      (set) => set.name === 'Components',
    )?.tokens;
    expect(
      componentTokens?.find(
        (token) => token.name === 'verify.heading.font-size',
      )?.value,
    ).toBe('2.375rem');
    expect(
      componentTokens?.find((token) => token.name === 'verify.body.font-size')
        ?.value,
    ).toBe('{verify.heading.font-size}');

    const sets = materialized.sets.map((set) => ({
      name: set.name,
      active: set.active,
      tokens: set.tokens.map((token) => ({
        name: token.name,
        value: structuredClone(token.value!),
        description: token.description ?? '',
      })),
    }));
    const themes = materialized.themes.map((theme) => ({
      group: theme.group,
      name: theme.name,
      active: theme.active,
      sets: [...theme.sets],
    }));
    expect(roundTripPackage(source, sets, themes)).toEqual(source);

    const heading = sets
      .find((set) => set.name === 'Components')
      ?.tokens.find((token) => token.name === 'verify.heading.font-size');
    if (!heading) throw new Error('Missing heading-size token fixture.');
    heading.value = '40px';
    const edited = roundTripPackage(source, sets, themes);
    const editedVerify = edited.sets.find((set) => set.name === 'Components')
      ?.document as unknown as {
      verify: { heading: { 'font-size': { $value: unknown } } };
    };
    expect(editedVerify.verify.heading['font-size'].$value).toEqual({
      value: 40,
      unit: 'px',
    });
  });

  it('rejects non-standard dimension and duration units', () => {
    for (const value of [
      { value: 38, unit: 'em' },
      { value: 38, unit: '%' },
      38,
    ]) {
      const source = structuredClone(VERIFY_DTCG_PACKAGE);
      const componentSet = source.sets.find((set) => set.name === 'Components');
      if (!componentSet) throw new Error('Missing Verify component set.');
      const verify = componentSet.document as unknown as {
        verify: { heading: { 'font-size': { $value: unknown } } };
      };
      verify.verify.heading['font-size'].$value = value;
      expect(() => materializePackage(source)).toThrow('px or rem');
    }

    const source = structuredClone(VERIFY_DTCG_PACKAGE);
    const primitiveSet = source.sets.find((set) => set.name === 'Foundation');
    if (!primitiveSet) throw new Error('Missing primitive token set.');
    const motion = primitiveSet.document as unknown as {
      motion: { duration: { selection: { $value: unknown } } };
    };
    motion.motion.duration.selection.$value = { value: 300, unit: 'frames' };
    expect(() => materializePackage(source)).toThrow('ms or s');
  });

  it('rejects invalid mixed token/group nodes and unresolved aliases', () => {
    const mixed = {
      $schema: DTCG_2025_10_SCHEMA,
      broken: {
        $type: 'color',
        $value: '#fff',
        child: { $value: '#000' },
      },
    } as DtcgDocument;
    expect(() => validateDtcgDocument(mixed)).toThrow(
      'cannot contain child tokens',
    );

    const source = structuredClone(VERIFY_DTCG_PACKAGE);
    const componentSet = source.sets.find((set) => set.name === 'Components');
    if (!componentSet) throw new Error('Missing Verify component set.');
    componentSet.document.orphan = {
      $type: 'color',
      $value: '{missing.alias}',
    };
    expect(() => materializePackage(source)).toThrow('Unresolved DTCG aliases');
  });

  it('declares a native projection for every specialized Verify dimension', () => {
    expect(VERIFY_NATIVE_TOKEN_TYPES).toEqual(
      expect.arrayContaining([
        {
          token: 'verify.panel.border-radius',
          type: 'borderRadius',
        },
        {
          token: 'verify.canvas.width',
          type: 'sizing',
        },
        {
          token: 'verify.body.font-size',
          type: 'fontSizes',
        },
        {
          token: 'verify.eyebrow.letter-spacing',
          type: 'letterSpacing',
        },
        {
          token: 'verify.layout.card-gap',
          type: 'spacing',
        },
      ]),
    );
  });
});
