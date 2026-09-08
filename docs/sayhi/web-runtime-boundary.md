# SayHi Web Runtime host boundary

## Status

Penpot remains the source of editable design state and, during the first
extraction slice, the producer of `sayhi.portable-web-artifact` version `1.0`.
The separately hosted SayHi Web Runtime validates and renders that artifact
through `io.sayhi.penpot.web-runtime-host` version `1.0`.

The legacy in-process renderer remains the default and compatibility oracle.
The standalone runtime is first exercised in `shadow` mode and becomes visible
only through the explicit `standalone-v1` runtime switch.

## Penpot responsibilities

The public MPL-covered adapter owns:

- current file, page, selected component, stable root shape ID, and revision;
- creation of the portable artifact from current Penpot state;
- mounting the configured runtime iframe from an operator-controlled HTTP(S)
  origin;
- returning only the exact artifact requested for the current component and
  revision;
- receiving bounded preview state and fidelity reports; and
- retaining the existing renderer as the rollback path.

Penpot does not accept an executable runtime URL from document or plugin data.
It does not evaluate messages from unknown frames or origins.

## Standalone runtime responsibilities

The private runtime owns:

- strict validation of the host protocol and portable artifact;
- safe artifact presentation in a scriptless nested iframe;
- renderer-specific behavior and future runtime implementation choices; and
- bounded preview readiness, error, and fidelity reporting.

The direct host URL is infrastructure. It intentionally does not act as a
standalone artifact gallery or editor; normal use mounts it from Penpot with
`?embed=1`.

## Version `1.0` messages

Runtime to Penpot:

- `runtime.ready`
- `runtime.context.request`
- `runtime.artifact.request`
- `runtime.preview.state`

Penpot to runtime:

- `host.context`
- `host.artifact`
- `host.error`

All envelopes use exact keys, schema `io.sayhi.penpot.web-runtime-host`, and
schema version `1.0`. The Penpot inbound envelope is bounded to 1,050,000 bytes.
The private runtime independently validates and bounds both messages and
artifacts before rendering.

## Extraction rule

This boundary extracts runtime ownership without prematurely extracting the
Penpot materializer. Materialization remains behind the existing
`projection-v1`, `shadow`, and `portable-v2` provider modes until parity proves
which portions can move without losing Penpot layout and token fidelity.
