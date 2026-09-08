# Motion dock collapse — development review, 2026-09-07

The private Motion timeline's collapse toggle now controls the outer dock size,
not just the embedded contents. The native helper receives the optional bounded
`io.sayhi.studio.motion-layout/1.0` `studio.presentation` envelope with the closed
payload `{collapsed: boolean}`. The original `studio.bounds` payload and Motion
editing protocol are unchanged. Exact origin and frame checks apply to both.

Collapsed presentation temporarily overrides (but preserves) manual height and
uses a compact minimum. The iframe remains mounted; its playback/expand control
stays available. Native CSS contributes Close in the space reserved by the private
toolbar, hides redundant header/resize controls, and retains the bottom anchor.
It does not use the native outer collapsed state, hide the component preview,
or change document/player behavior. Expanding restores manual height or auto-fit.

Source changes are confined to `motion_dock_sizing.cljs`, `motion_studio.scss`
and the existing `sayhi_motion_studio_test.cljs`. The already-dirty
`agent/motion-proposal-preview` baseline and all earlier image/Motion work remain
intact. No commits/pushes/production deployment. Project Intent discovery found
no matching enrolled assignment; none was invented.

Validation: focused native Motion25 tests/110 assertions pass; full frontend525
tests/1994 assertions retains the previously documented typography-normalization
failure, with zero test errors. CLJS lint and touched CLJS/SCSS format/SCSS lint
pass. Actual compiled browser sizing checks cover auto/manual collapse, restoring
height, unchanged bottom, refresh while collapsed, narrow layouts, reachable Close,
untrusted origin/window rejection, Settings, resize and scrolling. 349px auto or
385px manually resized collapses to66px. Existing native playback/prior/restore
and Settings effect probes pass.

Complete build: `frontend/scripts/build motion-collapse-20260907`; verified tag
`motion-collapse-20260907-1788820275`. The image plugin was restaged unchanged
after build, SHA256
`268e023478a3900046b16d1ca7aa898609e03a43e9b711c9d880f1f8520e84c5`.
All224 retained plugin/font files and effective public config match the previous
frontend. Image packaging uses the existing `Dockerfile.image-component`.

Active image:
`sha256:950cc291d3cdee901cb35ea21e539d58f685fcd08f8f85b3489ac94fcf5611c0`,
container `sayhi-penpot-motion-collapse-9015`, loopback9038/tailnet9015.
Private Studio remains `agent/prompt-image-attachments` on Chrome8464/Motion8468.
All other services, auth/backend/storage and ports are unchanged. Full private
composition/evidence is in Studio `docs/MOTION_MENU_COLLAPSE.md`.

Rollback: stop only `sayhi-penpot-motion-collapse-9015`, then start retained
`sayhi-penpot-image-component-9015`. Keep both worktrees and all volumes.
The old frontend safely ignores the new optional presentation message.
