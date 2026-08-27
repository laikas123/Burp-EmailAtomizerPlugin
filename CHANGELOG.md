# Email Atomizer Changelog

This changelog consolidates the version history represented by the supplied README files. Repeated inherited functionality has been removed from later entries so each release focuses on what changed in that version.

The supplied history contains dedicated release notes for **v0.3.0 through v0.3.3**, plus a retrospective list of capabilities retained from **v0.1**. No dedicated v0.2 release notes were present in the supplied material.

## v0.3.3

### Added

- **Custom pasted mutation runs** in Test Builder.
  - Paste one payload per line and run the entire set through the normal replay, rate-limit, stop-condition, result, and evidence pipeline.
  - Supports raw Unicode and `\uXXXX` escapes.
  - Supports templates:
    - `{EMAIL}`
    - `{LOCAL}`
    - `{DOMAIN}`
    - `{ALLOWED_DOMAIN}`
    - `{RECEIVER}`
    - `{RECEIVER_LOCAL}`
    - `{RECEIVER_DOMAIN}`
    - `{COLLAB_HOST}`
    - `{CORRELATION}`
- **Expanded Unicode/IDNA mutation matrices** covering:
  - compatibility normalization
  - width folding
  - alternate domain separators
  - IDNA mapping
  - zero-width characters
  - accent variants
  - cross-script control probes
- Added high-priority normalization probes including:
  - U+1D43 MODIFIER LETTER SMALL A (`ᵃ`)
  - U+2090 SUBSCRIPT SMALL A (`ₐ`)

These additions are aimed at detecting identity-lookup versus mail-routing normalization differentials.

## v0.3.2

### Added

- New top-level **Mutation Strings** manual payload generator that does not send target HTTP requests.
- Generate selected mutation families from a supplied identity email.
- Receiver-directed strings can use either:
  - fresh Burp Collaborator payloads, or
  - a controlled receiver mailbox.
- Added output formats for:
  - raw strings
  - JSON-escaped values
  - quoted JSON string literals
  - form/query encoding
  - URL percent encoding
  - double URL encoding
- Switching display/copy encoding keeps the generated Collaborator values stable.
- Fresh Collaborator values are generated only when **Generate mutation strings** is clicked again.
- Added individual-row, multi-row, copy-all, and labeled copy-all workflows for manual Burp/browser testing.

## v0.3.1

### Changed

- Replaced the overlapping scenario radio-button model with a **single indexed-occurrence Test Builder**.
- Every detected email occurrence is independently preserved and numbered (`#1`, `#2`, ...), including repeated identical values.
- Added **Primary email occurrence to test** selection.
- Added optional **Additional receiver occurrence** selection for a second email field.
- Unified receiver selection so receiver-directed mutations and the optional second occurrence can use either Burp Collaborator or a controlled mailbox.
- Renamed the dual-address family to **Two competing addresses in one field** and made it a mutation family rather than a separate scenario.

### Discovery improvements

- Passive discovery now distinguishes GraphQL/RPC operations sharing the same path using `operationName` / `X-Operation-Name`.
- Added an **Operation** column.
- Structural de-duplication no longer relies on raw byte offsets, preventing changing cookie/header lengths from creating false unique discoveries.

### Run controls and transport handling

- Added prominent **STOP current run** controls in both Test Builder and Results.
- Cancellation checkpoints the unsent tail for later resume.
- Preserved JSON-safe transport escaping for raw control bytes such as NUL (`\u0000`).

## v0.3.0

v0.3.0 established the main passive-discovery → Test Builder → replay → evidence workflow and added the bulk of the current operational controls.

### Test Builder and receiver workflows

- Added a scrollable Test Builder configuration pane.
- Added single-field testing with either:
  - Burp Collaborator, or
  - a controlled receiver mailbox.
- Receiver-directed mutation templates became aware of the selected mailbox/domain instead of assuming OAST-only behavior.
- Updated UI terminology from **OAST** to **receiver-directed** where either Collaborator or a controlled mailbox can be used.
- Added two-email role testing:
  - two addresses in one field
  - two separate email fields
- Primary mutation and secondary receiver Collaborator payloads use separate correlation IDs.

### Passive discovery and request handling

- Passive discovery remains active while a matrix is running.
- Atomizer suppresses only its own exact replay requests, allowing concurrent browsing to continue being observed.
- Added passive **Discoveries** with Burp-native **Request** and **Response** inspectors.
- Added detection of:
  - literal email addresses
  - URL-encoded email addresses
  - JSON `\\u0040`-style email addresses
- Added request/location metadata including host, method, path, location, parameter name, representation, and observation count.
- Added **Email Atomizer → Send to Email Atomizer** to Burp's context menu.
- Retains the exact stored request through Montoya's temporary-file request copy when available.
- Added occurrence-aware mutation so one email occurrence can be changed without changing every copy of the same address in the request.
- Added regression coverage for GraphQL `variables.identifier` discovery, including `+` addressing.

### Matrix selection and replay controls

- Added selectable mutation families instead of always running the entire catalog.
- Added presets:
  - Conservative
  - Full matrix
  - Receiver-directed
  - Select all / none
- Added configurable minimum inter-request delay.
- Added maximum-test cap.
- Added cancellation controls.
- Added configurable stop-status codes, defaulting to `420,429`.
- Added `Retry-After` parsing.
- Added visible Run Log and extension-output diagnostics for blocked runs and per-case failures.
- Out-of-scope requests are visibly marked in Test Builder.
- A true null/no HTTP response is automatically retried once before being marked no-response.
- JSON-targeted mutations escape quotes, backslashes, and control characters at the JSON transport layer so the application receives the intended parser payload rather than malformed JSON.

### Results and evidence

- Added a result **Signal** column for HTTP differentials, OAST interactions, SMTP observations, and rate limiting.
- Added Burp-native HTTP message editors to Results.
- Added result-row actions for:
  - Send to Repeater
  - Send to Intruder
  - Send to Organizer
  - Send to Email Atomizer Test Builder
- Selecting a result populates the full raw **Request** and **Response** beneath the table.
- Added a dedicated **Collaborator** evidence tab containing, where available:
  - interaction ID/type/time/client
  - raw DNS/HTTP/SMTP details
  - full SMTP conversation
  - parsed envelope `RCPT TO`
  - extracted SMTP DATA/message
  - best-effort message body
- Correlated interactions are stored before being marked consumed.
- Multiple events from one payload are retained separately, such as DNS → DNS → SMTP.
- Receiver-directed Collaborator cases are actively polled during a configurable post-request collection window.
- Controlled-mailbox cases do not wait for Collaborator interactions that cannot occur.
- Evidence enrichment failures are retained instead of silently producing an empty OAST result.

### Checkpoint and recovery

- Added checkpointing when a run:
  - hits a configured HTTP status
  - hits a configured response-body text match
  - is cancelled
  - is interrupted
- A checkpoint retains the original request, selected email occurrence, baseline, configuration, and unsent matrix tail.
- Added **Resume remaining tests** to skip the stopped case and continue with unsent mutations.
- Added **Retry stopped case + continue** to replay the stopped mutation before continuing.
- If the retried case hits the stop condition again, the checkpoint is preserved.

### Delivery guards

- Added case-insensitive response-body stop conditions.
- Default response-body stop text:

```text
tooManyAttempts
```

- Added optional **delivery-path sentinels** at the start/end of a run and optionally every N mutation tests.
- Added sentinel classifications:
  - `SENTINEL OK: SMTP observed`
  - `SENTINEL PARTIAL: ...`
  - `SENTINEL WARNING: no OAST in window`
- Sentinels are disabled by default because they create additional real delivery attempts.

## v0.1

The supplied README history does not contain a dedicated v0.1 release section, but later versions explicitly identify the following as capabilities retained from v0.1:

- 95 semantic mutation cases: 83 research-derived cases plus 12 dual-address identity/receiver role probes.
- PortSwigger-research-inspired families including:
  - quoted/comment probes
  - UUCP/bang paths
  - percent routing
  - RFC 2047 encoded words
  - control-byte split probes
  - UTF-7 layering
  - Unicode overflow
  - ORCPT ambiguity
  - malformed Punycode
- Unique Burp Collaborator payload/correlation ID per OAST-capable mutation.
- Automatic Collaborator polling.
- DNS/HTTP/SMTP interaction classification.
- SMTP conversation parsing and `RCPT TO` extraction.
- Canonical HTTP baseline with status/body-length differential reporting.
- Live Autorize-style mutation mode for an explicitly configured canonical email.
- CSV export.

## v0.2

No dedicated v0.2 release notes were present in the supplied README material, so no changes are inferred here.
