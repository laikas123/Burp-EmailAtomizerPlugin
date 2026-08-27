## 0.3.12 — 2026-08-22

- Fixes a request-construction bug where a successful logical body mutation could be labeled in Results while the reconstructed Montoya request retained the original body. Body-only mutations now use `withBody(ByteArray)` directly; mixed header/body rebuilds re-apply the intended body bytes.
- Adds fail-closed post-construction wire verification. A matrix/live mutation is not sent when the constructed body bytes or changed request-line/header fragment do not match the intended mutation.
- Fixes exact-target no-op accounting: unchanged replacements are no longer reported as mutations; `BASE-001` remains an explicit unchanged control.
- Fixes persistent Collaborator A/B row attribution so only account roots actually inserted by the current mutation/receiver role are bound to that row.
- CSV exports now include intended vs actual request-body hex, wire verification state, and selected target location/parameter/raw offset for mutation auditing.

## 0.3.11 — 2026-08-21

- Fixed Mutation Strings manual rendering so JSON wire-mode rows expose their actual `\\uXXXX` representation instead of duplicating the logical Unicode row.
- Added a Wire mode column to the manual generator.
- Prevented JSON copy modes from double-escaping wire-ready JSON Unicode/syntax-escape payloads.
- Added a separate context-menu action for copying the decoded logical Unicode mutation.
- Added regression tests for the `a -> ä`, `oo -> ꝏ`, and JSON `@`/`.`/`+` wire representations.
- Retains the 265-case catalog and the v0.3.10 no-op wire-mutation guard.

## 0.3.10 — 2026-08-21

- Fixed JSON-wire mutation cases that were incorrectly marked changed even when the rendered request text was byte-for-byte/textually identical to the original request.
- Non-applicable character/sequence mutations now skip instead of replaying the canonical account email.
- JSON transport escapes also skip when their target character is absent (for example `JSON-WIRE-009` on an address with no `+`).
- Added regression tests for an Abercrombie-style `SendTempPassword` request and for wire-change detection.
- Retains the 265-case catalog from v0.3.9, including the U+A74F LATIN SMALL LETTER OO digraph family.

## 0.3.9 — 2026-08-20

- Added IDNA2008-PVALID `oo -> ꝏ` (U+A74F LATIN SMALL LETTER OO) testing for Yahoo-style first labels.
- Added raw U-label, JSON-wire Unicode escape, and literal RFC 3492 A-label counterparts (`yahꝏ` / `\uA74F` / `xn--yah-pp3l`).
- Added the new digraph family to the existing **IDNA2008 bounty** preset automatically via its `IDNA2008 ` family prefix.
- Expanded catalog from 262 to **265** cases.
- Added regression coverage confirming `yahoo -> yahꝏ -> xn--yah-pp3l`.

## 0.3.8 — 2026-08-20

- Fixed raw-Unicode request corruption by emitting explicit UTF-8 request bytes through Montoya `ByteArray` construction.
- Added exact wire evidence to Results CSV: logical email UTF-8 hex and full request-body hex.
- Restricted normalization/domain-letter mutations to the first identity label so persistent Collaborator correlation suffixes are never mutated.
- Inapplicable STANDARD first-label replacements now skip instead of degenerating into fake baseline requests.
- Fixed SMTP transcript parsing that previously mistook any line beginning with `C` or `S` for a client/server prefix and could strip `Content-Type`, `Subject`, `color`, `cursor`, and similar content.
- Encoded raw DNS Collaborator bytes as hex/escaped ASCII and sanitized C0 controls in both Results and inbox CSV exports.
- Added 81 IDNA2008/PVALID-focused tests: 27 raw U-label candidates, 27 JSON-wire counterparts, and 27 literal A-label controls.
- Added Latin diacritic fold candidates relevant to `gmail`, `yahoo`, and `wearehackerone`-style labels, plus `ß/ss`, final sigma, and PVALID confusable controls.
- Added **IDNA2008 bounty** preset.
- Expanded catalog from 181 to **262** cases.
- Preserved v0.3.7 persistent-account evidence dual-write into Results + Collaborator inbox.

## 0.3.6 — 2026-08-20

- Fixed stale Collaborator availability text after successful client initialization.
- Redesigned persistent Collaborator Account A/B controls so generation buttons cannot be clipped off-screen.
- Added connected-state indicator, safe Retry Collaborator connection, and Generate both A + B.
- Disabled generation/poll controls only when Collaborator is actually unavailable.
- Preserved existing persistent-account correlations and the 181-case mutation catalog.

# Changelog

## 0.3.5 — 2026-08-20

- Added a **Collaborator Accounts** tab with persistent Account A / Account B addresses generated from Atomizer's own Collaborator client. Address generation sends no target traffic and does not start a matrix, so accounts can be created at the tester's pace.
- Added an **Atomizer Collaborator inbox** for persistent-account and Mutation Strings interactions that are not tied to a matrix result.
- Persistent account slots use separate Collaborator roots/correlation IDs and retain exact SMTP `RCPT TO`, DATA/message/body, DNS/HTTP details, timestamp, and client metadata.
- Mutation Strings Collaborator payloads are now registered for standalone correlation instead of being silently discarded by the results-only poller.
- Added manual **Poll now** and inbox clearing controls. Clearing displayed events does not invalidate the generated account correlations.
- Made `Retry-After` enforcement opt-in/off by default. Unchecking it now immediately bypasses a previously stored cooldown and updates resumed-run behavior instead of leaving the old checkpoint setting frozen.
- Retains the 181-case v0.3.4 mutation catalog and JSON-wire/IDNA additions.

## 0.3.4

- Expanded the built-in matrix to 181 cases.
- Added request-level JSON Unicode wire encoding and double-escaped controls.
- Added JSON duplicate-key and escaped-equivalent-key ambiguity mutations.
- Added additional Unicode compatibility, separator, and address-separator probes.
- Added raw RFC 3492 Punycode A-label counterpart generation.
- Added wire-mode visibility in results/CSV.
- Enforced Retry-After cooldowns across stopped-run Resume/Retry actions.

## 0.3.3 — 2026-08-18

- Added a Test Builder custom mutation textarea so arbitrary one-per-line payload sets can be pasted and replayed automatically.
- Added raw Unicode and `\uXXXX` decoding for pasted mutations plus reusable placeholders for canonical and receiver address components.
- Added high-priority Unicode compatibility-normalization probes for U+1D43 MODIFIER LETTER SMALL A (`ᵃ`) and U+2090 SUBSCRIPT SMALL A (`ₐ`).
- Added compatibility, width-folding, alternate-domain-separator, zero-width, IDNA-mapping, accent, and cross-script control matrices.
- Added a systematic 26-letter fullwidth-domain sweep.
- Added regression tests for the confirmed compatibility-differential payload shapes and custom mutation parsing.

## 0.3.2 — 2026-08-17

- Added a top-level **Mutation Strings** tab for manual copy/paste testing when automated replay is undesirable.
- Enter an identity email, choose mutation families, and render the existing 95-case mutation catalog without sending HTTP requests.
- Receiver-directed manual mutations can use fresh Burp Collaborator addresses (default) or a real controlled receiver mailbox.
- Added **Raw**, **JSON escaped value**, **JSON string literal**, **Form / query encoded**, **URL percent encoded**, and **Double URL encoded** output modes.
- Encoding changes do not regenerate the underlying payload set; pressing **Generate mutation strings** intentionally creates a fresh set of Collaborator values.
- Added copy-selected, copy-all, labeled copy-all, double-click copy, and row right-click copy actions.
- Added self-test coverage for manual rendering with fallback/controlled receivers plus JSON/URL copy-paste encoding.
- Retains v0.3.1 indexed occurrence selection, operation-aware passive discovery, live-run STOP, delivery sentinels, stop-text/status handling, null-response retry, and multi-event Collaborator/SMTP capture.

## 0.3.1 — 2026-08-17

- Simplified Test Builder to one unified indexed-occurrence model. Removed the redundant top-level scenario choices that overlapped with the dual-address mutation family.
- Primary and optional additional receiver fields are selected by numbered request occurrence (`#1`, `#2`, ...), so requests with multiple email fields use the same UI as single-field requests.
- Renamed **Two addresses in one field** to **Two competing addresses in one field**; it remains the 12-case `DUAL-*` family and is now clearly independent from request-field selection.
- Preserved repeated identical email values as separate detected occurrences so each can be selected independently.
- Passive discovery now classifies GraphQL/RPC operations using URL `operationName`, `X-Operation-Name`, JSON `operationName`, or a named GraphQL query/mutation fallback. Distinct operations sharing one path no longer collapse into one row.
- Added **Operation** and occurrence-index columns to passive discoveries. Structural de-duplication uses location/parameter/representation slots instead of unstable absolute raw offsets.
- Added prominent **STOP current run** buttons to Test Builder and Results. Cancellation stops after the current in-flight request/collection window and preserves the unsent tail for resume.
- Added regression coverage for repeated same-address occurrences, Abercrombie-style GraphQL operation separation, and JSON NUL transport escaping.
- Retains the 95-case mutation catalog, controlled receiver mode, delivery sentinels, stop-text/status handling, null-response retry, native request/response viewers, and multi-event Collaborator/SMTP capture.

## 0.3.0 — 2026-08-17

- Made the full **Test Builder configuration area vertically scrollable**, so the **What this run will do** summary cannot hide minimum delay, stop-text, sentinel, or other lower controls on smaller Burp windows.
- Extended **single-field parser testing** to support either **Burp Collaborator** (default) or a real **Email address I control** as the receiver target.
- Receiver-directed mutation generators now derive their local-part/domain from the selected receiver, preserving the existing Collaborator behavior while allowing the same parser strategies to target a controlled mailbox when a site only delivers to known/real accounts.
- Delivery sentinels continue to use Collaborator even when controlled-mailbox mode is selected, so optional mail-path health checks remain observable.
- Clarified UI terminology: **Receiver-directed** replaces the misleading **OAST-focused** preset label, and mutation-family counts now say **receiver-directed** rather than implying Collaborator is mandatory.
- Kept the three explicit test scenarios and the live **What this run will do** explanation introduced in v0.2.9.

## 0.2.9 — 2026-08-17

- Added configurable **response-body stop text** (case-insensitive literal substrings, one per line), defaulting to `tooManyAttempts`.
- Text-triggered stops use the same checkpoint flow as HTTP stop codes: **Resume remaining tests** or **Retry stopped case + continue**.
- Added optional **delivery-path sentinels**: fresh direct Collaborator controls at run start/end and optionally every N mutation tests.
- Sentinel results explicitly report SMTP success, partial OAST-only activity, or no-OAST-in-window warnings.
- Preserved v0.2.7 passive discovery while matrices run, native Request/Response viewers, right-click send-to actions, JSON-safe mutation transport, one automatic retry for null HTTP responses, multi-interaction Collaborator aggregation, and 95 mutation cases.


## 0.2.7 — 2026-08-17

- Fixed passive discovery being globally disabled while a matrix was running. Atomizer now suppresses only its own replay requests, so normal Proxy/Repeater/browser traffic can still be discovered concurrently.
- Added Burp-native Request/Response inspectors to Passive Discoveries.
- Replaced result request/response text areas with Burp-native HTTP message editors.
- Added result/discovery row right-click actions for Send to Repeater, Intruder, Organizer, and Email Atomizer Test Builder.
- Automatically retries a mutation once when Montoya returns no HTTP response.
- Fixed JSON transport serialization for mutation payloads containing quotes, backslashes, or control characters; these are now JSON-escaped without changing the value seen by the application parser.
- Added GraphQL `variables.identifier` passive-discovery regression coverage using an Instacart-style request.
- Retains multi-interaction Collaborator aggregation, configurable OAST collection window, stop-code retry/resume, and 95 mutation cases.

## 0.2.6 — 2026-08-16

- Collect all Collaborator interactions for each OAST-capable matrix case during a configurable post-request window (default 3000 ms) before sending the next request.
- Fixed interaction de-duplication: Collaborator `Interaction.id()` can identify the payload across multiple protocol events, so Atomizer now fingerprints each event using type/timestamp/client/protocol/details instead of dropping later DNS/SMTP events with the same interaction ID.
- Results now preserve interaction multiplicity and order (for example `DNS×2 + SMTP` and `DNS → DNS → SMTP`).
- CSV export adds `oast_summary`, `interaction_count`, and `interaction_sequence`; `collaborator_details` contains every captured interaction transcript.
- The configured request delay remains a minimum request-to-request spacing; time spent collecting OAST interactions counts toward that spacing rather than being added on top.
- Background Collaborator polling still continues after the collection window, so delayed interactions can enrich a result later.

## 0.2.5 — 2026-08-16

- Fixed Collaborator detail capture for DNS, SMTP, and HTTP interactions by correcting Montoya enum compatibility for `DnsQueryType`, `SmtpProtocol`, and `HttpProtocol`.
- Collaborator evidence is now persisted before an interaction is marked seen, preventing `OAST=DNS/SMTP/HTTP` rows with permanently missing raw details after a parsing failure.
- Protocol-detail parsing is isolated and fault tolerant; even if enrichment fails, the result retains a minimal interaction record plus the extraction error.
- Added **Retry stop-code case + continue** alongside **Resume remaining tests**. The retry path replays the exact mutation that received the configured stop code, then continues with the unsent tail.
- A stop-code case remains retryable even when it was the final test in the matrix.
- Preserved configurable stop codes (default `420,429`), request/response inspection, two-email role testing, and full Collaborator/SMTP evidence views.

## 0.2.4 — 2026-08-16

- Added a per-result **Collaborator** inspector that preserves the full correlated interaction evidence.
- SMTP evidence now shows interaction metadata, envelope `RCPT TO`, full SMTP conversation, extracted DATA/message content, and best-effort message-body extraction.
- DNS and HTTP Collaborator interactions now retain raw interaction details instead of only showing an interaction-type badge.
- Added separate correlation roles so a primary mutation payload and a second receiver occurrence can be distinguished in Collaborator evidence.
- Added **two-occurrence identity/receiver mode**: select one detected email occurrence as identity and independently replace a second occurrence with a unique Collaborator receiver or controlled mailbox.
- Added **Swap identity / receiver** for quick role reversal.
- Added 12 **Two addresses in one field** that place identity and controlled receiver addresses into a single value using comma, semicolon, whitespace, display-name, and address-list shapes.
- Mutation catalog increased from 83 to 95 cases.
- CSV export now includes receiver override, SMTP DATA/message, parsed body, and full Collaborator evidence.
- Preserved v0.2.3 request/response inspection and resumable matrix checkpoints.

## 0.2.3 — 2026-08-16

- Added full raw request and response inspection for every Results row.
- Added resumable matrix checkpoints after configured stop codes, manual cancellation, or interruption.
- Resume preserves the original request, selected email occurrence, baseline comparison, run settings, and exact unsent mutation tail.
- Results UI now reports how many tests remain and why the prior run stopped.
- Live-mode results also retain their mutated request/response text for inspection.

## 0.2.2

- Fixed matrix/live replay compatibility with current Montoya API: `HttpRequest.httpService()` now compiles against `burp.api.montoya.http.HttpService` rather than the incorrect `burp.api.montoya.http.message.HttpService` descriptor.
- Added packaging-time bytecode verification for the `HttpService` descriptor.
- No mutation-catalog changes; still 83 cases.


## 0.2.2 — 2026-08-16

- Fixed the silent/blank Results experience when a run is blocked before the first request.
- Added a visible Results **Run log** and mirrored run diagnostics to Burp extension output/error.
- Test Builder now visibly marks requests that are outside Burp Target scope.
- Removed the ambiguous context-menu one-click matrix runner; the supported flow is **Send to Email Atomizer -> configure -> Run**.
- Replaced the hard-coded 429 stop check with configurable stop-status codes; defaults are **420,429**.
- Added passive email discovery while browsing with no automatic replay.
- Added Discoveries tab with endpoint/location/parameter/value metadata and deduplication counters.
- Added literal, URL-encoded, and JSON Unicode-escaped passive detection.
- Added **Email Atomizer -> Send to Email Atomizer** context-menu workflow.
- Added Test Builder with detected-candidate selection.
- Added occurrence-aware mutation for literal and URL-encoded candidates.
- Added selectable matrix families and Conservative / PortSwigger Full / OAST-focused presets.
- Added configurable delay, maximum-test cap, matrix cancellation, and rate-limit handling.
- Added `Retry-After` parsing for rate-limit handling.
- Added Signal column for HTTP differentials, OAST/SMTP observations, and rate limiting.
- Retained all 83 v0.1 mutation cases, Collaborator correlation, SMTP `RCPT TO` extraction, live mode, and CSV export.

## 0.1.0 — 2026-08-16

- Initial Java/Montoya Burp extension.
- Exact canonical-email replacement in literal, URL-encoded, double-encoded, `%40`, `%2540`, and JSON Unicode-escape forms.
- Live mutation mode with scope-only and GET/HEAD safety defaults.
- Right-click sequential mutation matrix.
- 83 semantic mutation cases based on the technique families in PortSwigger Research's “Splitting the Email Atom”.
- Research-style UUCP, percent-routing, encoded-word/control-byte, UTF-7, Unicode-overflow, ORCPT and malformed-Punycode probes.
- Burp Collaborator payload generation with custom correlation IDs.
- Automatic Collaborator polling and SMTP `RCPT TO` extraction.
- Baseline HTTP status/body-length differential display.
- CSV export and standalone core self-test.
