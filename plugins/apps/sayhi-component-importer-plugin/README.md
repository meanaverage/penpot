# SayHi component importer vNext

This plugin is the isolated, Penpot-native successor to the retained SVG importer prototype. It imports only **SayHi Verify** while the fidelity contract is being proven. It does not replace or modify the legacy importer.

## Source-of-truth boundary

- A DTCG Resolver 2025.10 document is the canonical orchestration source. Its
  inline Format documents remain the canonical token sources.
- Penpot token sets, themes, aliases, and shape bindings are a native editable projection of those documents.
- The complete resolver is stored on the imported component as shared plugin data so unsupported DTCG values and `$extensions` survive a round trip. The prior package payload is retained only as a migration rollback.
- SayHi component anatomy, portable-web runtime metadata, and the engine-neutral SayHi Motion document live in `io.sayhi.component` on the Resolver's `Components` set.
- CSS custom properties from the legacy Studio are not canonical token names and are not imported.
- The legacy Studio and SVG importer are behavioral references only. New component-import architecture belongs here.

## Verify conformance fixture

The fixture creates four ordered token sets:

1. `Foundation`
2. `Semantic/Light`
3. `Semantic/Dark`
4. `Components`

Set names are composition and resolution boundaries, not token namespaces.
The shared `Components` set therefore stores Verify decisions under the
collision-safe `verify.*` token group. A portable source may remain
`components/verify.tokens.json`, but importing another component merges its
own component-qualified group into `Components` rather than creating one
always-active set per component. Canonical token names never include the SayHi
brand; SayHi ownership stays in `io.sayhi.*` extensions and generated CSS may
use a `--sayhi-` output prefix.

Light and Dark are contexts of the Resolver's `colorScheme` modifier. The
Penpot projection creates mutually exclusive native themes from those contexts,
plus editable shapes, stable source IDs, anatomy tags, live shape-token
bindings, portable-web runtime metadata, and the SayHi Motion v0.5
selection-response document.

Typography is projected at two levels. `Foundation` holds reusable font-family
and font-weight primitives. `Components` exposes Verify aliases plus composite
`typography` roles for the eyebrow, heading, body, side and active method copy,
hint, and cancel action. Text layers bind to those composites so family, size,
weight, line height, and letter spacing remain one editable decision instead of
an assortment of unbound canvas values.

The method icons likewise bind to semantic component-state dimensions. Side
cards use `verify.method.side.icon-size`; the active card uses
`verify.method.active.icon-size`. This preserves the original component's
state-dependent scale instead of flattening every icon to one imported size.

Penpot currently has no public token types for DTCG `duration` or `cubicBezier`. Those values remain canonical and lossless in the stored resolver and motion extension; they are reported as preserved-only rather than flattened into a fake Penpot token type.

## Safety model

- Imports are idempotent by canonical-resolver fingerprint.
- The prior vNext component is preserved under `SayHi vNext/Legacy` when a resolver update requires replacement.
- Token-type collisions fail closed instead of overwriting unrelated data.
- Shape, token, set, and theme mutations share one Penpot undo block.
- Failed imports roll back token/theme mutations and remove the partial drawing.
- The plugin rejects overlapping import requests.

## Font provisioning

- Canonical DTCG typography keeps the intended SayHi family stack, beginning
  with `Instrument Sans`.
- The importer never assigns an arbitrary family string or invents a variant
  ID. It resolves the family through `penpot.fonts` and applies the closest
  registered normal-weight variant.
- The SayHi-hosted Penpot runtime enables Penpot's existing Google Fonts
  provider. Instrument Sans is present in Penpot's bundled catalog and its
  files are fetched through Penpot's same-origin `/internal/gfonts` proxy when
  needed.
- If no preferred family is registered, import remains usable with Penpot's
  first registered font. Adding an arbitrary remote-font installer to the
  public plugin sandbox is intentionally out of scope; any future installer
  must be a host-owned, license-aware allowlist capability.

## Validation

From `plugins/`:

```bash
pnpm --filter sayhi-component-importer-plugin test
pnpm --filter sayhi-component-importer-plugin lint
pnpm --filter sayhi-component-importer-plugin build
```

The current comparison gate is Verify-only. Do not add more components or switch the default importer until browser fidelity, theme switching, DTCG export/re-import, web runtime, and motion playback all pass against this fixture.
