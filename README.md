# Email Atomizer
## v0.3.12 verified request-body replacement

### v0.3.12 wire-integrity fix

v0.3.12 prevents the matrix from silently sending an unchanged body under a mutated result label. JSON/form body changes are applied as explicit UTF-8 bytes with `withBody(ByteArray)`, then verified against the intended body before send. A failed verification is recorded as **BLOCKED** and no target request is issued. CSV output includes both intended and actual body hex plus the selected occurrence metadata. Persistent Collaborator A/B attribution is now based only on values actually inserted into the current request.


- Fixed the **Mutation Strings** tab so JSON wire-mode rows now display and copy the actual intended wire representation instead of only the decoded logical Unicode value.
- Example: `PVALID-LATIN-J-003` now shows `laikas@y\\u00e4hoo...` while its logical counterpart remains `laikas@yähoo...`.
- The U+A74F digraph JSON case now shows `yah\\ua74f...` instead of looking identical to the raw `yahꝏ...` row.
- Added a **Wire mode** column so `JSON_UNICODE_ESCAPED`, syntax-escape, double-escaped, and standard rows are visually distinguishable.
- **JSON escaped value** and **JSON string literal** copy modes no longer double-escape an already wire-ready `\\uXXXX` mutation.
- The row context menu now includes **Copy selected logical Unicode mutation** when the decoded value is useful separately.
- Added regression tests for `a -> ä`, `oo -> ꝏ`, and JSON `@`/`.`/`+` syntax escapes in the manual generator.

- Fixed **raw Unicode request corruption** by constructing mutated requests from explicit UTF-8 bytes through Montoya's `ByteArray` API instead of the lossy String request constructor path.
- Raw U+FF41 now leaves Atomizer as UTF-8 `EF BD 81`; U+10040 leaves as `F0 90 81 80` rather than being narrowed into ASCII/NUL bytes.
- Domain normalization sweeps now mutate **only the first identity label**. They never search into a persistent Collaborator suffix such as `<payload>.oastify.com`; inapplicable STANDARD cases are skipped.
- Fixed SMTP transcript parsing so legitimate DATA lines beginning with `C` or `S` (`Content-Type`, `Subject`, CSS `color`/`cursor`, etc.) are no longer stripped.
- DNS binary evidence is exported as **hex + escaped ASCII**, and CSV export escapes embedded control bytes such as NUL instead of writing raw binary into CSV cells.
- Results CSV now includes `logical_email_utf8_hex` and `request_body_hex` for direct wire verification.
- Added a curated **IDNA2008/PVALID bounty matrix** with raw U-label, JSON-wire, and literal A-label counterparts.
- Added **`oo → ꝏ` (U+A74F LATIN SMALL LETTER OO)** as raw U-label, JSON-wire `\uA74F`, and literal A-label (`xn--`) counterparts for Yahoo-style/digraph-folding tests.
- Added high-value Latin diacritic candidates (for example `a→á/ä`, `o→ó/ö`, `y→ý`, `e→é`) that are IDNA2008-valid and can expose accidental accent-folding identity lookup.
- Added the two IDNA2008/UTS #46 transitional differentials: **`ß ↔ ss`** and **final sigma `ς ↔ σ`**.
- Added PVALID cross-script confusable controls and their literal A-label counterparts.
- Added an **IDNA2008 bounty** preset in Test Builder. PVALID means the Unicode code point is valid under IDNA2008; it does **not** guarantee acceptance by every registry/TLD or mixed-script policy.
- Catalog size is now **265 mutation cases**.

A Burp Suite Professional extension for controlled email-parser differential testing during authorized pentests and bug-bounty work.

The primary workflow in **v0.3.12** is:

> **Passive discovery -> Send to Email Atomizer -> choose one or more mutation matrices -> rate-limited replay -> HTTP/Collaborator/SMTP results**

The mutation strategy is based on PortSwigger Research's **Splitting the Email Atom** methodology and technique families.

## What v0.3.11 fixes

- Fixed **JSON-wire no-op mutations** that could replay the canonical/real email while being labeled as a mutation when the requested source character was absent from the first domain label.
- The mutator now treats a JSON-wire case as changed only when the actual outbound request text differs from the original request.
- Non-applicable JSON Unicode/IDNA cases and transport escapes (for example escaping `+` when the address has no plus sign) are skipped instead of sent.
- Added regression coverage using an Abercrombie-style `SendTempPassword` JSON request so absent-source PVALID cases cannot silently become canonical-email replays.
- Retains the U+A74F `oo -> ꝏ` raw/JSON/A-label family added in the prior release.

## What v0.3.11 adds

v0.3.11 keeps the 265-case v0.3.9 catalog (including U+A74F LATIN SMALL LETTER OO) and fixes applicability handling for JSON-wire cases. Character-targeted mutations whose source sequence is absent now skip instead of replaying the canonical address.

- Expanded the full catalog to **265 mutation cases**.
- Added true **JSON wire Unicode** cases. These preserve JSON Unicode escapes in the outbound HTTP body rather than merely changing copy/display formatting.
- Added raw-vs-JSON-wire counterparts for U+1D43, U+2090, fullwidth characters, Unicode domain separators, combining marks, and the U+10040 astral overflow case.
- Added double-escaped backslash-u controls to detect accidental second decoding.
- Added JSON syntax-escape controls for `@`, `.`, and `+`.
- Added **JSON parser ambiguity** cases with duplicate literal keys and escaped-equivalent keys such as `e\u006dail`.
- Added additional NFKC compatibility characters: circled a, feminine ordinal a, mathematical bold a, long s, and Kelvin sign.
- Added U+FE52 and U+2024 domain-separator probes plus fullwidth/small at-sign probes.
- Added raw RFC 3492 **A-label counterparts** for high-value Unicode labels so U-label/A-label behavior can be compared directly.
- Results and CSV export now expose the mutation's **wire mode** separately from the logical mutated email. The exact outbound request remains available in the Request inspector.
- Added optional `Retry-After` handling for stopped 429 runs. Enforcement is now **off by default**; if enabled, the checkpoint can gate Resume/Retry, and unchecking the option immediately returns cooldown control to the tester even for an existing checkpoint.


### Persistent Collaborator accounts and inbox

v0.3.11 includes the **Collaborator Accounts** tab for long-lived test identities:

- Generate independent **Account A** and **Account B** addresses from Atomizer's own `CollaboratorClient`.
- Configure the local part and one subdomain label (defaults: `laikas@gmail...` and `laikas@yahoo...`).
- Generated addresses remain unchanged until you explicitly regenerate them, giving you time to create the corresponding accounts in the target application before any fuzzing starts.
- Generating or copying an account address sends **no request to the target**.
- Each account uses a distinct Collaborator root/correlation ID, so SMTP/DNS/HTTP activity can be attributed to A or B.
- The **Atomizer Collaborator inbox** collects interactions for persistent accounts and for Collaborator payloads created by **Mutation Strings**, even when there is no matrix `AtomResult` row.
- When a matrix uses persistent Account A/B, the same SMTP/DNS/HTTP evidence is also copied into the linked **Results** row, so the normal Results CSV contains `smtp_recipient`, SMTP message/body, interaction sequence, and raw Collaborator details.
- The configured post-request Collaborator collection window is used for persistent accounts even when the mutation itself is not receiver-directed.
- Use **Poll now** to fetch immediately; background polling still continues automatically.
- The inbox preserves SMTP `RCPT TO`, SMTP DATA/message/body, DNS/HTTP details, source IP/port, timestamp, and raw interaction data, and can be exported independently with **Export inbox CSV**.
- Persistent account addresses are session-persistent: keep the extension loaded while creating/testing the accounts. Regenerating a slot creates a fresh address, while late events from old generated correlations can still be captured during the same loaded session.
- The Collaborator Accounts screen uses dedicated **Persistent accounts** and **Collaborator inbox** inner tabs so the inbox receives the full available viewport.

This preserves the lossless standalone inbox added in v0.3.5 while fixing the v0.3.6 gap where persistent-account evidence did not populate the actual matrix Results row/CSV.

### JSON-wire behavior

JSON-wire cases are intentionally request-format-specific. They only run against an exact email occurrence detected in a JSON body. On form/query/header targets they are skipped rather than silently degrading into a different test.

For example, the logical value `poc@gmᵃil.example.com` can now be tested in at least three distinct ways:

```text
raw UTF-8:        poc@gmᵃil.example.com
JSON wire escape: poc@gm\u1d43il.example.com
double escaped:   poc@gm\\u1d43il.example.com
```

The last form tests whether a later component performs an unexpected second decoding step.

## Test Builder model

The Test Builder now uses **one unified configuration** instead of overlapping scenario modes. Every detected email occurrence in the request is numbered (`#1`, `#2`, ...).

- **Primary email occurrence to test** chooses the exact field/value Atomizer mutates.
- **Additional receiver occurrence (optional)** is `None` by default. If a request contains multiple email fields, choose any other numbered occurrence and Atomizer will replace that second occurrence independently during the same run.
- **Alternate / receiver address used by tests** chooses either fresh Burp Collaborator addresses (default) or a real mailbox/account you control. The same receiver choice is used by receiver-directed mutations and, when selected, the additional receiver occurrence.
- **Two competing addresses in one field** is now only a mutation family. Selecting it deliberately places both the original identity address and the alternate/receiver address inside the **primary** field. It is no longer duplicated as a separate top-level scenario.

Examples:

- Primary `#1`, second occurrence `None`, Collaborator: baseline + general parser/receiver-directed variations against one field.
- Primary `#1`, second occurrence `None`, controlled mailbox: the same parser strategies target a real account/mailbox instead of OAST.
- Primary `#1`, second occurrence `#2`: mutations act on `#1` while `#2` is independently set to the selected receiver source.
- Select **Two competing addresses in one field** when you specifically want `A,B`, `B,A`, display-name/address-list, and similar two-address shapes inside the primary field.

The live **What this run will do** box spells out the chosen primary occurrence, optional second occurrence, receiver source, and whether two-address-in-one-field probes are selected before anything is sent.

## Mutation Strings — manual payload generator

v0.3.11 includes a top-level **Mutation Strings** tab for cases where an automated replay matrix is not the right workflow. Enter a starting/identity email, choose the mutation families, and generate copy/paste-ready strings without sending any HTTP requests.

Receiver-directed mutations can use either:

- **Fresh Burp Collaborator addresses** (default): each receiver-capable mutation gets a fresh payload/correlation value.
- **Email address I control**: the same receiver-aware mutation templates are built around a real mailbox/account you control.

Generated rows keep the mutation **ID**, **family**, **label**, receiver-directed flag, and rendered mutation visible. Output can be switched locally between:

- **Raw**
- **JSON escaped value** (for insertion inside existing JSON quotes)
- **JSON string literal** (includes surrounding quotes)
- **Form / query encoded** (spaces `+`)
- **URL percent encoded** (spaces `%20`)
- **Double URL encoded**

Changing the display/copy format does **not** regenerate Collaborator addresses. Click **Generate mutation strings** again when you intentionally want fresh payloads. Double-click or right-click rows to copy, or use **Copy all strings** / **Copy all with IDs / families**.

## What v0.3.11 includes

v0.3.11 retains the testing workflows learned from Unicode/IDNA password-reset research:

- **Custom pasted mutation runs** — paste one payload per line directly into Test Builder and run the whole set through the same replay, stop-code, rate-limit, result, and evidence pipeline. Raw Unicode and `\uXXXX` escapes are accepted. Templates can use `{EMAIL}`, `{LOCAL}`, `{DOMAIN}`, `{ALLOWED_DOMAIN}`, `{RECEIVER}`, `{RECEIVER_LOCAL}`, `{RECEIVER_DOMAIN}`, `{COLLAB_HOST}`, and `{CORRELATION}`.
- **Expanded Unicode/IDNA matrices** — adds compatibility-normalization, width-folding, alternate domain separator, IDNA mapping, zero-width, accent, and cross-script control probes. The high-priority set explicitly includes U+1D43 MODIFIER LETTER SMALL A (`ᵃ`) and U+2090 SUBSCRIPT SMALL A (`ₐ`), which are useful for detecting identity-lookup vs mail-routing normalization differentials.


- Added the top-level **Mutation Strings** manual payload generator; it never sends target requests.
- Mutation Strings can render any selected mutation families from a supplied identity email using fresh Collaborator payloads or a controlled receiver mailbox.
- Added copy/paste formats for raw, JSON-escaped value, quoted JSON string literal, form/query encoding, URL percent encoding, and double URL encoding.
- Mutation Strings preserves stable generated payloads while switching encodings and only creates fresh Collaborator values when **Generate mutation strings** is clicked again.
- Added per-row copy, multi-row copy, copy-all, and labeled copy-all output for manual Burp/browser testing.
- Replaced the overlapping one-field/two-address/two-field scenario radio buttons with a single indexed-occurrence UI.
- Every detected email occurrence is independently preserved and numbered, including repeated identical email values in the same request.
- Added an optional second receiver occurrence selector; choose any other numbered email occurrence without switching UI modes.
- Renamed the dual-address family to **Two competing addresses in one field** so it is clearly a mutation family, not another scenario.
- Passive discovery now distinguishes GraphQL/RPC operations sharing the same path using `operationName` / `X-Operation-Name`, and shows an **Operation** column.
- Passive discovery structural de-duplication no longer relies on raw byte offsets, so changing cookie/header lengths does not create fake unique entries.
- Added prominent **STOP current run** controls in both Test Builder and Results; cancellation checkpoints the unsent tail for later resume.
- Preserved JSON-safe transport escaping for raw control bytes such as NUL (`\u0000`).
- The entire Test Builder configuration pane is scrollable, fixing lower run controls being cut off when the scenario summary is visible.
- Single-field testing can now use either **Burp Collaborator** (default) or a **controlled receiver mailbox**. Receiver-directed mutation templates are receiver-aware, so controlled-mailbox mode targets the supplied mailbox/domain rather than generating OAST payloads.
- UI terminology now uses **receiver-directed** rather than **OAST** where a mutation can be run with either Collaborator or a controlled mailbox.

- Passive discovery now stays active while a matrix is running; only Atomizer's own exact replay requests are suppressed, so concurrent browsing is still observed.
- Passive **Discoveries** now have Burp-native **Request** and **Response** inspectors.
- Results use Burp-native HTTP message editors and row right-click actions for **Send to Repeater**, **Send to Intruder**, **Send to Organizer**, and **Send to Email Atomizer Test Builder**.
- A true null/no HTTP response is automatically retried once before the case is marked no-response.
- JSON-targeted mutations now escape quotes, backslashes, and control characters at the JSON transport layer so the application receives the intended email-parser payload instead of malformed JSON.
- Added a regression test for GraphQL `variables.identifier` email discovery (including `+` addressing).
- Passive request observation with **no mutation or replay**.
- Discovery table for email-like values seen while browsing.
- Detection of literal, URL-encoded, and JSON `\\u0040`-style addresses.
- Request/location metadata: host, method, path, body/header/query location, parameter name, representation, and observation count.
- **Email Atomizer -> Send to Email Atomizer** Burp context-menu action.
- Exact stored request is retained via Montoya's temporary-file request copy when available.
- Test Builder that lets you choose the detected email occurrence to mutate.
- Occurrence-aware mutation for literal and URL-encoded candidates so the same email in a cookie and JSON field does not have to be changed everywhere.
- Selectable mutation matrices/families rather than forcing the entire catalog.
- Presets:
  - Conservative
  - Full matrix
  - Receiver-directed
  - Select all / none
- Configurable minimum inter-request delay.
- Maximum-test cap.
- Prominent **STOP current run** buttons in Test Builder and Results.
- Configurable stop-status list, default **420,429**.
- `Retry-After` parsing for rate-limit handling.
- Visible Run Log plus Burp extension-output diagnostics for blocked runs and per-case failures.
- Out-of-scope requests are visibly marked in Test Builder.
- Result **Signal** column for HTTP differentials, OAST interactions, SMTP observations, and rate limiting.
- Dedicated **Collaborator** evidence tab for each result, including interaction ID/type/time/client, raw DNS/HTTP/SMTP details, full SMTP conversation, parsed envelope `RCPT TO`, extracted SMTP DATA/message, and best-effort message body.
- Two-email role testing:
  - **Two competing addresses in one field** deliberately place both the application identity and alternate receiver inside the selected primary value.
  - Any second detected email occurrence can optionally be selected by index and independently replaced with a Collaborator receiver or controlled mailbox.
  - Primary mutation and additional-receiver Collaborator payloads use separate correlation IDs so the evidence shows which occurrence triggered the interaction.

## Existing capabilities retained from v0.1

- 95 semantic mutation cases: the original 83 research-derived cases plus 12 dual-address identity/receiver role probes.
- PortSwigger-research-inspired families including quoted/comment probes, UUCP/bang paths, percent routing, RFC 2047 encoded words, control-byte split probes, UTF-7 layering, Unicode overflow, ORCPT ambiguity, and malformed Punycode.
- Unique Burp Collaborator payload/correlation ID per OAST-capable mutation.
- Automatic Collaborator polling.
- DNS/HTTP/SMTP interaction classification.
- SMTP conversation parsing and `RCPT TO` extraction.
- Canonical HTTP baseline with status/body-length differential reporting.
- Live Autorize-style mutation mode for an explicitly configured canonical email.
- CSV export.

## Requirements

- Burp Suite Professional for built-in Collaborator functionality.
- JDK 21 to build from source.
- Montoya API `2026.7`.

## Build

```bash
./build.sh
```

If Gradle is not installed on macOS:

```bash
brew install gradle
./build.sh
```

The JAR is written to `build/libs/email-atomizer-0.3.12.jar`.

## Load in Burp

1. Open **Extensions -> Installed -> Add**.
2. Choose **Java**.
3. Select `email-atomizer-0.3.12.jar`.
4. Open the **Email Atomizer** top-level tab.
5. Leave passive discovery enabled and browse normally.
6. When an interesting request appears, either:
   - select it in Burp and choose **Email Atomizer -> Send to Email Atomizer**, or
   - open **Discoveries** and send/double-click a passive discovery.
7. In **Test Builder**, select the exact email candidate, choose matrix families, set the rate limit, and click **Run selected matrices**.
8. Review **Results** while Collaborator polling continues.

## Rate-limiting defaults

- Minimum spacing: **1000 ms** between requests.
- Collaborator post-request collection window: **3000 ms** for OAST-capable cases.
- Maximum tests: **0** (all selected).
- Stop-status codes: **420,429** by default; edit the field to add/remove codes.
- `Retry-After`: parsed for rate-limit handling.
- Only send/mutate Burp-scope requests: enabled.

Matrix mode can trigger real email/account actions. Use only on systems you are authorized to test and tune the rate for the endpoint's side effects.

## Research basis

Primary methodology:

- https://portswigger.net/research/splitting-the-email-atom
- https://github.com/PortSwigger/splitting-the-email-atom

The extension independently implements the mutation generators and Burp workflow. See `docs/RESEARCH_MAPPING.md` and `docs/USAGE.md`.

### Result request/response inspector

Selecting a row in **Results** now populates full raw **Request** and **Response** tabs beneath the table. This preserves the exact mutated request string passed to Montoya and the full response returned by Burp, making it much easier to understand HTTP differentials without hunting through Proxy history.

### Resume after a stop condition or cancellation

If a run stops on a configured HTTP code or response-body text match, is cancelled, or is interrupted, Atomizer stores a checkpoint containing the original request, selected email occurrence, baseline, configuration, and the unsent tail of the matrix. **Resume remaining tests** continues at the next unsent mutation instead of restarting the matrix.

### Stop-condition recovery

When a configured stop condition is hit (for example HTTP `420`/`429` or response text such as `tooManyAttempts`), v0.3.11 preserves two recovery choices:

- **Resume remaining tests** skips the rate-limited request and continues with the first unsent mutation.
- **Retry stopped case + continue** replays the mutation that hit the stop condition, then continues with the unsent tail. If the retry hits a stop condition again, the checkpoint is preserved again.

### Collaborator evidence integrity

v0.3.11 stores each correlated interaction before marking it consumed. When **Burp Collaborator** is the receiver source, receiver-directed OAST cases are actively polled for a configurable 3000 ms post-request collection window before the next request is sent. Controlled-mailbox cases do not wait for OAST that cannot occur. Multiple DNS/HTTP/SMTP events from one payload are retained separately (for example DNS → DNS → SMTP). DNS/HTTP/SMTP detail extraction is best-effort; an enrichment failure is retained as evidence rather than silently producing an OAST badge with an empty Collaborator pane.


## v0.3.11 delivery guards

The Test Builder can stop on both HTTP status codes and **case-insensitive response-body text**. The default text pattern is:

```text
tooManyAttempts
```

Add additional literal substrings one per line. A text stop uses the same checkpoint model as a 420/429 stop: you can either resume the unsent tail or retry the stopped case and continue.

Optional **delivery-path sentinels** send a fresh direct Collaborator recipient at the start and end of a run, and optionally every N mutation tests. These rows use the `Delivery sentinel` family and are scored as:

- `SENTINEL OK: SMTP observed`
- `SENTINEL PARTIAL: ...`
- `SENTINEL WARNING: no OAST in window`

Sentinels are disabled by default because they generate additional real delivery attempts. Enable them when you want to distinguish a parser-negative mutation from a mail path that became throttled or unavailable during the matrix.

# Burp-EmailAtomizerPlugin
