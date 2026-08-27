# SayHi component importer V2

This is a separately installable, manifest-driven component importer. It does
not replace, rename, or route through `sayhi-component-importer-plugin`.

The canonical boundary is:

```text
DTCG 2025.10 Format sources
        +
DTCG 2025.10 Resolver
        +
io.sayhi.component metadata
        +
component projection graph
        |
        v
Penpot native shapes, tokens, themes, bindings, anatomy, and motion metadata
```

`src/materializer.ts` contains no Verify-specific coordinates. The Verify
fixture lives in `src/fixtures/verify.ts` and is the first conformance case.
Absolute projection frames are expressed relative to the root. Frame members
may reference DTCG dimension tokens, allowing layout portability to be tested
rather than ruled out in advance.

V1 remains an independent comparison baseline until V2 passes visual,
token-resolution, theme, anatomy, motion, export, and re-import checks.

## Validation

From `plugins/`:

```bash
pnpm --filter sayhi-component-importer-v2-plugin test
pnpm --filter sayhi-component-importer-v2-plugin lint
pnpm --filter sayhi-component-importer-v2-plugin build
pnpm --filter plugin-api-test-suite test:sayhi-importer-v2
```

The browser gate imports and reimports the component into the real Penpot
plugin runtime, rejects any plugin validation error, verifies Motion Studio
eligibility, checks the independent `Projection V2` token sets, and captures a
visual proof at `/tmp/sayhi-component-importer-v2-browser.png`.

The embedded Resolver is also valid against the pinned official DTCG 2025.10
Resolver schema. Layout dimensions remain canonical DTCG values where the
standard can express them; the projection graph supplies structural and
render-order semantics that design tokens do not define.
