# SayHi Studio chrome host boundary

The Penpot fork is a first-class SayHi authoring surface, but the SayHi prompt,
application navigation, Qwen client, and artifact destinations are not Penpot
features. They are hosted by the private SayHi Studio surface chrome and mounted
through the versioned `io.sayhi.studio.surface-chrome` adapter.

The public Penpot adapter owns only:

- deciding when the focused Studio canvas mode is active;
- reporting the current bounded selection, theme, locale, and visible palette
  inset;
- reporting whether Motion Studio is available or open;
- mounting and resizing the configured external chrome iframe; and
- accepting the bounded `motion.toggle` and `penpot.controls.toggle` commands.

The external host owns app labels and destinations, the prompt and response UI,
Qwen capability discovery and requests, and future SayHi-global navigation.
Penpot never receives model credentials and the adapter does not expose file
contents, token documents, conversations, or arbitrary host commands.

`internal-v1` temporarily preserves the earlier in-fork prompt as a rollback
implementation. `external-v1` is the extraction candidate. After the external
host passes the same canvas, palette, Motion, and navigation tests, the internal
implementation and switch should be removed rather than maintained in parallel.
