import type {
  DtcgResolverDocument,
  MaterializedPackage,
  NativeSetSnapshot,
  NativeThemeSnapshot,
  SayHiComponentExtension,
  SayHiDtcgPackage,
} from './contract.js';
import {
  canonicalResolverJson,
  componentExtensionFromResolver,
  materializeResolver,
  roundTripResolver,
} from './dtcg-resolver.js';
import {
  canonicalPackageJson,
  materializePackage,
  roundTripPackage,
} from './dtcg.js';
import {
  VERIFY_DTCG_RESOLVER,
  VERIFY_LEGACY_DTCG_PACKAGE,
} from './verify-package.js';

export type VerifyTokenSourceMode = 'legacy-package' | 'resolver-2025.10';

// One rollback point for the token-orchestration boundary. Resolver is the
// default only after comparison tests prove the same native Penpot plan.
export const VERIFY_TOKEN_SOURCE_MODE: VerifyTokenSourceMode =
  'resolver-2025.10';

export function materializeVerifyTokenSource(
  mode: VerifyTokenSourceMode = VERIFY_TOKEN_SOURCE_MODE,
): MaterializedPackage {
  return mode === 'resolver-2025.10'
    ? materializeResolver(VERIFY_DTCG_RESOLVER)
    : materializePackage(VERIFY_LEGACY_DTCG_PACKAGE);
}

export function canonicalVerifyTokenSource(
  mode: VerifyTokenSourceMode = VERIFY_TOKEN_SOURCE_MODE,
): string {
  return mode === 'resolver-2025.10'
    ? canonicalResolverJson(VERIFY_DTCG_RESOLVER)
    : canonicalPackageJson(VERIFY_LEGACY_DTCG_PACKAGE);
}

export function verifyComponentExtension(): SayHiComponentExtension {
  return componentExtensionFromResolver(VERIFY_DTCG_RESOLVER);
}

export function legacyVerifyPackageJson(): string {
  return canonicalPackageJson(VERIFY_LEGACY_DTCG_PACKAGE);
}

export function roundTripStoredVerifyTokenSource(
  stored: string,
  nativeSets: NativeSetSnapshot[],
  nativeThemes: NativeThemeSnapshot[],
  mode: VerifyTokenSourceMode,
): string {
  if (mode === 'resolver-2025.10') {
    const source = JSON.parse(stored) as DtcgResolverDocument;
    return canonicalResolverJson(
      roundTripResolver(source, nativeSets, nativeThemes),
    );
  }
  const source = JSON.parse(stored) as SayHiDtcgPackage;
  return canonicalPackageJson(
    roundTripPackage(source, nativeSets, nativeThemes),
  );
}
