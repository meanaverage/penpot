# Native image-component broker, development review

Deployment update, 2026-09-08: 9015 now runs
`sayhi-penpot-image-clock-9015`, image
`sha256:afccc0d565e09bffa569087b3c4eb73c46f6734c97a769455a5ac5d5a674fe85`.
The coherent frontend remains `prompt-layout-20260908-1788827499`. Its independently
built importer was corrected for SES clock availability; the sandbox intentionally
exposes Date/timers, not performance. `Dockerfile.image-plugin-overlay` copies only
the verified plugin and its metadata on top of the existing complete image.
All 1,004 other application files are byte-identical. The actual SES + native API
composition gate passes all modes, a 652-token fixture and stale-target rejection.
The older API-only harness below bypasses SES and cannot establish that property.
Current plugin SHA256:
`816d6c042eedc857bcb4fc20c92bcaf557dc623ad792b4c38807e5e34f67b8c3` (40,461 bytes).
Immediate rollback container: `sayhi-penpot-prompt-layout-9015`, retained stopped
(contains the preceding importer bug). Full reproduction/deployment notes live in
private Studio `docs/IMAGE_IMPORT_SANDBOX_CLOCK_FIX.md`. Historical rollout follows.

The private Studio prompt can now generate a component from pasted references.
The public frontend owns only a narrow first-party import broker:

- `main/data/sayhi/image_component.cljs`: separate closed
  `io.sayhi.studio.image-component/1.0` context/import envelope, exact file/page
  and write-permission checks, bounded requests/receipts, concurrent-import guard.
- `ui/sayhi/studio_chrome.cljs`: routes only messages from the actual configured
  private iframe WindowProxy and origin, alongside the unchanged v1 commands.
- `plugins/register.cljs`: internal temporary content/library permission entries,
  released after this plugin finishes; no permanent plugin/profile installation.

The fixed plugin path is `/plugins/sayhi-image-component/image-plugin.js`.
Only the native broker supplies its `imageComponent` extension: request JSON,
target-liveness predicate and completion callback. Plugin code, URLs and requested
permissions cannot come from a user/model message. Generation instructions,
provider routing and scene compilation source remain in private Studio/gateway.
The plugin itself is an independently built private gateway artifact; do not
check its generated code into the public fork.

The plugin creates native shapes/tokens/library component through the existing
V2 importer and preserves its portable web artifact. An acknowledged import is a
native edit, not a durability claim about asynchronous backend persistence.
Existing undo grouping applies; a failed import can leave token sets, so do not
advertise atomic rollback. The browser prompts the user to inspect/Undo on errors.

## Build and evidence

The existing `agent/motion-proposal-preview` dirty source baseline is preserved;
this is a follow-up, not a fresh reconstruction from clean develop. Its complete
frontend build ran in one process using its existing repository-owned builder:
`frontend/scripts/build image-component-20260907`.
Verified version: `image-component-20260907-1788805818`.

Stage the independently built plugin with
`frontend/scripts/stage-image-importer.mjs PATH SHA256`, then package through
`docker/images/Dockerfile.image-component`. The allowlisted build context keeps
credentials/state/private source out of the image. The retained parent preserves
all214 pre-existing plugin files and8 Geist/Kallisto assets, byte-compared during
this task; runtime config/auth/backend/storage and unrelated preview ports match.

Image `36e189bd0c0dbedff6c0e579b8464a6fdc5bd191938e1d1adc085c8068b9884c` is running
as `sayhi-penpot-image-component-9015`, loopback9038/tailnet9015. Plugin digest:
`268e023478a3900046b16d1ca7aa898609e03a43e9b711c9d880f1f8520e84c5`.

New host tests2/11 assertions pass; lint0 warnings/errors; formatting passes.
The full523-test native suite retains the pre-existing typography normalization
failure documented in `motion-proposal-preview-review.md`.

Optional `:image-import-check` is a separate test-only target. Its harness uses
real native proxies, events, tokens, library creation and provenance on synthetic
files; only renderer boundaries are faked and no persistence watcher is started.
All4 real model studies imported successfully. The harness explicitly takes an
external plugin and synthetic candidate, so ordinary public tests do not acquire
a private dependency. It normalizes Node SAX module interop to the real parser
export, matching what the release browser bundler already does.

The actual compiled native Motion proposal/prior/restore and bottom-anchored
content-fit sizing probes passed before and after activation. No authenticated
customer-file writes were used as test evidence.

## Rollback

The immediate predecessor `sayhi-penpot-motion-sizing-9015` remains intact and
stopped. Stop only `sayhi-penpot-image-component-9015` and start that predecessor.
Do not remove volumes or worktrees. Private chrome rollback is its new
40-image-component-review.conf drop-in; the complete development composition,
build commands, bounds and limitations are in private Studio's
`docs/IMAGE_COMPONENT_RECONSTRUCTION.md`.
