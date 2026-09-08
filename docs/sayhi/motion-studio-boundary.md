# SayHi Motion Studio host boundary

## Status

The Penpot fork is the primary SayHi authoring shell. Motion Studio is a
separate capability connected to that shell through the versioned
`io.sayhi.penpot.motion-host` protocol.

Version `1.0` freezes the smallest host boundary needed by the current Motion
Studio integration. New incompatible messages, payload shapes, or semantics
require a new protocol version rather than a silent change to version `1.0`.

## Penpot responsibilities

The public Penpot-side adapter owns:

- the current file, page, selection, stable shape IDs, and component identity;
- DTCG-backed motion document storage on the selected Penpot object;
- revision-checked motion writes, with each accepted write recorded as one
  Penpot undo transaction;
- mounting the bounded Motion Studio iframe and live web preview surface;
- forwarding preview controls and anatomy highlights;
- validating every inbound message before it reaches Penpot state; and
- publishing only capabilities that the adapter actually implements.

The adapter does not advertise history transactions. Version `1.0` has no
multi-message begin/commit/cancel history protocol; `studio.motion.write` is
the single transaction boundary.

## Motion Studio responsibilities

The standalone Motion capability owns the engine-neutral motion document,
timeline editor, playback clock, renderer planning, and concrete playback or
export adapters. The playback and materializer implementations currently
embedded in this fork are preserved extraction sources. They must not expand
into a second long-term Motion engine while the standalone boundary is being
established.

## Inbound version `1.0` messages

Penpot accepts only these Studio-to-host message types:

- `studio.ready`
- `studio.context.request`
- `studio.motion.read`
- `studio.motion.write`
- `studio.preview.recipe`
- `studio.preview.command`
- `studio.anatomy.highlight`

Each message must use exact envelope and payload keys. The serialized envelope
is bounded to 1,050,000 bytes; motion documents and preview recipes are bounded
to 1,000,000 bytes; anatomy highlights are bounded to 64 part IDs. Unknown,
malformed, oversized, or unsupported messages are ignored before they can
reach Penpot state.

A preview recipe is accepted only when both its `componentId` and `revision`
match the current selected motion context. A stale recipe receives a retryable
`motion_preview_context_changed` host error instead of being applied to a new
selection.

## Preview state compatibility

The embedded preview may use the internal state `unavailable`. The frozen
public version `1.0` vocabulary does not. Penpot maps internal `unavailable`
and any unknown internal status to public `empty`; all other supported states
pass through unchanged.

## Runtime configuration

The frontend entrypoint replaces each JavaScript runtime variable in
`config.js` at most once. Container restarts update the existing declaration
instead of appending another declaration whose ordering could change the
effective Motion Studio, preview, or materializer configuration.

## Migration rule

Artifact identity and capability attachments are the durable seam between
Penpot, legacy Studio, and standalone capabilities. Until that seam is
complete, Penpot host work should remain limited to selection, persistence,
preview mounting, and protocol correctness. Playback, planning, and editor
features belong to the standalone Motion capability rather than this adapter.
