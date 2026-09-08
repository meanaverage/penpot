# Motion proposal preview: DGX3 development review

## Scope and source

User-authorized repair of 9015's unsaved Motion assistant preview. No commits,
pushes, PRs, backend/database/model changes, authenticated customer-file writes,
or changes to the 8469 comparison were performed.

This focused worktree is `agent/motion-proposal-preview`, based on the retained
`real-web-preview` branch at `1341f1db6f9730516a1789e59a7b1a642c4a2151`.
The canonical `develop` clone is untouched. The accepted preview has substantial
uncommitted frontend/chrome/native-host changes: those frontend, test, docker
and boundary-document sources were copied here using patches, leaving the
reference checkout and its index untouched. The broad diff from HEAD is mostly
that inherited source, **not** the new fix. Project Intent discovery from this
checkout found no enrolled Penpot workstream (offline snapshot).

New work relative to the accepted source:

- `frontend/src/app/main/data/sayhi/motion_host/document_preview.cljs`:
  bounded, non-persisting document-preview adapter and controller replacement.
- `frontend/src/app/main/ui/sayhi/motion_studio.cljs`: versioned recipe routing,
  acknowledgement after actual controller replacement, reload cleanup/error.
- `frontend/test/frontend_tests/data/sayhi_web_materializer_test.cljs`: real
  controller deletion/replay/restore and invalid-proposal regression.
- `frontend/resources/fonts/gfonts.2025.11.28.json`: represents the deployed
  Geist 1.8.0 and Kallisto entries in source, preserving the font assets admitted
  through the old image's post-build catalog patches.
- `docker/images/Dockerfile.motion-preview` and its allowlisted build context:
  complete freshly built frontend, retaining the accepted independent importer
  bundles, font binaries, nginx routes and runtime configuration.

The native playback/math modules are retained extraction sources, not expanded
into another Motion engine. This is a small host adapter using the existing
compiler/player. Model prompts and proprietary editing policy stay in private
Studio (`agent/motion-context-agent`).

## Contract

The existing v1 `studio.preview.recipe` opaque object uses a separately versioned
`format: sayhi.motion.document-preview`, `formatVersion: 1.0` payload containing
exact file/page/selected-shape identity, response ID and DTCG document. Null
document restores canonical motion. Envelope component/revision still must
match. The current document owns tokens and unknown neighboring extensions;
they cannot be silently changed in preview. Partial/unrenderable plans fail.

Compilation is pure. Old animations are disposed, the controller is replaced,
and the requested response is selected before readiness is published. Only
successful replacement emits correlated `host.ack` for `studio.preview.recipe`.
No store event or undo transaction occurs. Saved document ownership and the
existing `studio.motion.write` path remain unchanged.

## Build and evidence

The worktree uses its own dependency trees in the pinned local
`penpotapp/devenv:latest` image (`01acfe84ad14`, arm64), mounted at `/workspace`
in container `sayhi-motion-proposal-build`. No legacy virtualenv or node_modules
were borrowed. Its sparse checkout was expanded to include plugins, MCP, docker,
docs and `.clj-kondo`; the initial missing plugin directory caused a recursive
postinstall attempt which was stopped, then dependencies installed successfully.

Production build (single process, including HTML/JS/assets/plugins/renderer):

```bash
sudo docker exec -w /workspace/frontend sayhi-motion-proposal-build \
  bash -lc './scripts/build motion-proposal-preview-20260907'
```

The final version verifier passed for
`motion-proposal-preview-20260907-1788797347`. Image packaging uses:

```bash
sudo docker build -f docker/images/Dockerfile.motion-preview \
  --build-arg VERSION_TAG=motion-proposal-preview-20260907-1788797347 \
  -t sayhi/penpot-frontend:motion-proposal-preview-20260907-parity .
```

Pinned parent image:
`87551f5876cf9de1e1c4df6928e629a674f36e2cac32a0fe7463e44320f9cbc3`.
New image:
`8d64593327d016611f601189720a5cd3954c7b353e1a2f2d53221d0bf85ee15c`.
Independent importer/font assets and effective `js/config.js` were byte-compared
against the old deployment. All matched. No split/resumed frontend packaging or
post-build JavaScript patch was used for this image.

Native host tests pass; native materializer/player tests: 18 tests / 115 assertions
pass. Full frontend suite: 519 tests / 1951 assertions, one failure in unchanged
`frontend-tests.plugins.tokens-test/token-value-update-normalizes-public-dtcg-values`.
The same test fails in isolation, and its implementation/test match base HEAD.
Do not report the full suite green. Full Clojure lint and touched formatting pass.

Private Studio's 279-test contract gate, contextual browser regression, track-menu
browser regression, and official DTCG fixture validation pass. Its explicit
`scripts/check-native-motion-preview.mjs` probe passed against the actual compiled
candidate and again after deployment: real WAAPI objects disappear from the
Previous Icons target, Next is unchanged, and Cancel restores interpolation.
It uses an isolated synthetic iframe, no account or customer-file writes. The
signed-in user journey remains a separate acceptance check.

Logs are in `/tmp/sayhi-motion-proposal-*`, `/tmp/sayhi-motion-preview-*` and
`/tmp/sayhi-motion-native-browser-live.log`; they are not committed evidence.

## Activation and rollback

Active container: `sayhi-penpot-motion-proposal-9015` (`b261c09b6b70`), loopback
9038 behind the unchanged tailnet 9015 proxy. It retains the original frontend
runtime variables, network, asset volume and restart policy. The two private
Studio preview services still read their existing motion-context worktree.
Refresh the full 9015 workspace to obtain the new host; old tabs cannot apply
this recipe and now time out instead of falsely reporting readiness.

The original container remains intact and stopped:
`sayhi-penpot-real-web-preview-87-geist-kallisto-9015-candidate` (`aa232c31a2ba`).
Frontend rollback (only these exact containers):

```bash
sudo docker stop sayhi-penpot-motion-proposal-9015
sudo docker start sayhi-penpot-real-web-preview-87-geist-kallisto-9015-candidate
```

Do not delete volumes or either source worktree. The current private adapter
fails closed against the old host. To also roll back the assistant experiment,
use the documented two-private-service rollback in Studio's
`docs/MOTION_CONTEXT_AGENT.md`. The standalone 8469 service remains unchanged.

## Content-fit Motion dock follow-up — 2026-09-07

The follow-up adds `frontend/src/app/main/ui/sayhi/motion_dock_sizing.cljs`,
small lifecycle/ref wiring in `motion_studio.cljs`, and co-located dock styling.
Private Studio reports intrinsic toolbar + visible workspace + status height,
not its current iframe height. Status/empty copy is 13px (previous status: 8px).
The content region scrolls; toolbar and status remain outside it.

The optional `io.sayhi.studio.motion-layout` / `1.0` `studio.bounds` envelope
contains only integer `payload.height` (64–4096). The host checks exact keys,
version, configured origin and actual iframe WindowProxy identity, then clamps
size to its available viewport. This is separate from the unchanged/frozen
Motion editing protocol. Older hosts ignore it and retain the fixed dock;
new hosts retain that fallback until they receive a measurement.

Automatic size fits content up to a 420px dock ceiling. Removing one of three
tracks frees exactly one 54px row. The existing bottom anchor stays fixed.
A top grip supports pointer capture, Arrow Up/Down in 24px steps, End for maximum
height, and double-click/Enter/Home to restore auto-fit. Escape cancels a drag.
Manual height is local to the open dock, not document state or persisted storage.
Collapse/reopen returns to automatic sizing. Layout cleanup removes listeners.

Build version `motion-sizing-20260907-1788800905` was produced by one complete
`frontend/scripts/build motion-sizing-20260907` process and passed the version
verifier. Packaged image:
`5b6ae87aa79811ce14f1d0f87592e09dce0376bc4b7785f8200d6fd340e2df67`.
All importer/plugin trees, retained Geist/Kallisto assets and effective runtime
config match the previous 9015 image byte-for-byte.

Validation: native layout/host tests 23/95 assertions pass; native materializer
and player 18/115 pass; full Clojure lint, touched CLJS formatting and SCSS lint
pass. Full native suite: 521 tests/1968 assertions, the same one unchanged
typography normalization failure documented above. Studio's 280-contract gate,
contextual browser regression and existing manual track-menu regression pass.
The status-font regression failed at 8px before the change. Evidence logs:
`/tmp/sayhi-motion-sizing-*`. The explicit Studio script
`scripts/check-native-motion-sizing.mjs` loads the compiled native sizing helper,
release CSS and actual private iframe in an isolated synthetic browser; no
authenticated file or customer-document writes are involved.

The candidate's compiled-host browser probe passed: three tracks 349px, two
tracks 295px, manual upward drag 385px with the same bottom edge, plus keyboard
resizing, reset, compact/empty states and a scrollable 12-track default. Browser
resize handlers are explicitly dispatched in the probe because DGX can defer
native resize delivery with paint; no animation clock or layout math is replaced.
The separate real-WAAPI proposal/prior playback probe also passed.

Active frontend is now `sayhi-penpot-motion-sizing-9015` (`de508e29eec0`) on the
same loopback 9038 / tailnet 9015 route. The immediately preceding working
frontend `sayhi-penpot-motion-proposal-9015` is retained intact and stopped.
Layout-only frontend rollback:

```bash
sudo docker stop sayhi-penpot-motion-sizing-9015
sudo docker start sayhi-penpot-motion-proposal-9015
```

Private frames keep their existing services/configuration and read the updated
Studio worktree. Refresh the full workspace. The previous native host ignores
the optional layout measurements on rollback; proposal playback still works.
Both compiled sizing and real-WAAPI probes passed again against live 9038 after
activation (`/tmp/sayhi-motion-sizing-live-{browser,playback}.log`). The builder
and temporary 9042 candidate were stopped; their images/containers are retained.
