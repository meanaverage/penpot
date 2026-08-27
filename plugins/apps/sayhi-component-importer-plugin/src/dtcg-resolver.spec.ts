import { describe, expect, it } from 'vitest';
import type {
  DtcgDocument,
  DtcgResolverDocument,
  JsonObject,
} from './contract.js';
import {
  canonicalResolverJson,
  componentExtensionFromResolver,
  legacyPackageFromResolver,
  materializeResolver,
  roundTripResolver,
  validateDtcgResolver,
} from './dtcg-resolver.js';
import { materializePackage } from './dtcg.js';
import {
  VERIFY_DTCG_RESOLVER,
  VERIFY_LEGACY_DTCG_PACKAGE,
} from './verify-package.js';

describe('DTCG Resolver 2025.10 projection', () => {
  it('uses the official resolver model and stores SayHi metadata on a set extension', () => {
    expect(() => validateDtcgResolver(VERIFY_DTCG_RESOLVER)).not.toThrow();
    expect(VERIFY_DTCG_RESOLVER.version).toBe('2025.10');
    expect(VERIFY_DTCG_RESOLVER.$extensions).toBeUndefined();
    expect(
      VERIFY_DTCG_RESOLVER.sets?.Components.$extensions?.['io.sayhi.component'],
    ).toEqual(componentExtensionFromResolver(VERIFY_DTCG_RESOLVER));
  });

  it('projects exactly the same native Penpot plan as the preserved package path', () => {
    expect(materializeResolver(VERIFY_DTCG_RESOLVER)).toEqual(
      materializePackage(VERIFY_LEGACY_DTCG_PACKAGE),
    );
    expect(legacyPackageFromResolver(VERIFY_DTCG_RESOLVER)).toEqual(
      VERIFY_LEGACY_DTCG_PACKAGE,
    );
  });

  it('projects resolver modifiers into native sets and themes', () => {
    const light = materializeResolver(VERIFY_DTCG_RESOLVER);
    const dark = materializeResolver(VERIFY_DTCG_RESOLVER, {
      colorScheme: 'DARK',
    });

    expect(light.sets.map(({ name, active }) => ({ name, active }))).toEqual([
      { name: 'Foundation', active: true },
      { name: 'Semantic/Light', active: true },
      { name: 'Semantic/Dark', active: false },
      { name: 'Components', active: true },
    ]);
    expect(dark.sets.map(({ name, active }) => ({ name, active }))).toEqual([
      { name: 'Foundation', active: true },
      { name: 'Semantic/Light', active: false },
      { name: 'Semantic/Dark', active: true },
      { name: 'Components', active: true },
    ]);
    expect(dark.themes).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ name: 'Light', active: false }),
        expect.objectContaining({ name: 'Dark', active: true }),
      ]),
    );
  });

  it('round-trips native edits into inline resolver sources without losing motion metadata', () => {
    const materialized = materializeResolver(VERIFY_DTCG_RESOLVER);
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
      active: theme.name === 'Dark',
      sets: [...theme.sets],
    }));
    const heading = sets
      .find((set) => set.name === 'Components')
      ?.tokens.find((token) => token.name === 'verify.heading.font-size');
    if (!heading) throw new Error('Missing heading token fixture.');
    heading.value = '40px';

    const changed = roundTripResolver(VERIFY_DTCG_RESOLVER, sets, themes);
    const componentSource = changed.sets?.Components.sources[0] as DtcgDocument;
    expect(
      (
        ((componentSource.verify as JsonObject).heading as JsonObject)[
          'font-size'
        ] as JsonObject
      ).$value,
    ).toEqual({ value: 40, unit: 'px' });
    expect(changed.modifiers?.colorScheme.default).toBe('dark');
    expect(componentExtensionFromResolver(changed).motion).toEqual(
      componentExtensionFromResolver(VERIFY_DTCG_RESOLVER).motion,
    );
    expect(JSON.parse(canonicalResolverJson(changed))).toEqual(changed);
  });

  it('detects cross-token type mismatches and circular aliases after resolution', () => {
    const wrongType = structuredClone(VERIFY_DTCG_RESOLVER);
    const wrongTypeDocument = wrongType.sets?.Components
      .sources[0] as DtcgDocument;
    wrongTypeDocument.invalid = {
      $type: 'number',
      $value: '{color.base.panel}',
    };
    expect(() => materializeResolver(wrongType)).toThrow('targets');

    const circular = structuredClone(VERIFY_DTCG_RESOLVER);
    const circularDocument = circular.sets?.Components
      .sources[0] as DtcgDocument;
    circularDocument.loop = {
      $type: 'number',
      first: { $value: '{loop.second}' },
      second: { $value: '{loop.first}' },
    };
    expect(() => materializeResolver(circular)).toThrow('Circular DTCG alias');
  });

  it('fails closed for missing inputs, invalid defaults and external sources', () => {
    const missingInput = structuredClone(VERIFY_DTCG_RESOLVER);
    delete missingInput.modifiers?.colorScheme.default;
    expect(() => validateDtcgResolver(missingInput)).toThrow(
      'Missing resolver input colorScheme',
    );

    const badDefault = structuredClone(VERIFY_DTCG_RESOLVER);
    badDefault.modifiers!.colorScheme.default = 'sepia';
    expect(() => validateDtcgResolver(badDefault)).toThrow('unknown default');

    const external = structuredClone(
      VERIFY_DTCG_RESOLVER,
    ) as DtcgResolverDocument;
    external.sets!.Foundation.sources = [{ $ref: './foundation.tokens.json' }];
    expect(() => validateDtcgResolver(external)).toThrow(
      'cannot load external token source',
    );
  });
});
