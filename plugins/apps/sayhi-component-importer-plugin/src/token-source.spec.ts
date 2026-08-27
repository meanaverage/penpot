import { describe, expect, it } from 'vitest';
import {
  VERIFY_TOKEN_SOURCE_MODE,
  canonicalVerifyTokenSource,
  materializeVerifyTokenSource,
  verifyComponentExtension,
} from './token-source.js';

describe('Verify token source switch', () => {
  it('serves the official resolver path by default behind one switch point', () => {
    expect(VERIFY_TOKEN_SOURCE_MODE).toBe('resolver-2025.10');
    expect(JSON.parse(canonicalVerifyTokenSource())).toEqual(
      expect.objectContaining({ version: '2025.10' }),
    );
  });

  it('keeps the legacy path contract-compatible for rollback', () => {
    expect(materializeVerifyTokenSource('resolver-2025.10')).toEqual(
      materializeVerifyTokenSource('legacy-package'),
    );
    expect(verifyComponentExtension()).toEqual(
      expect.objectContaining({
        componentId: 'sayhi.verification-method-selector',
      }),
    );
  });
});
