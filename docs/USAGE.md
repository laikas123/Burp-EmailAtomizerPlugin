# Email Atomizer v0.3.8 usage

## Choosing email occurrences and receiver behavior

There is one Test Builder UI for both single- and multi-email requests. Atomizer numbers every detected email occurrence in request order.

1. Choose **Primary email occurrence to test** (`#1`, `#2`, ...). This is the field the selected mutation families act on.
2. Leave **Additional receiver occurrence** at **None** for ordinary single-field testing. If the request contains another email field you want controlled independently, select its index.
3. Choose the alternate/receiver source:
   - **Burp Collaborator** — fresh unique addresses and automatic DNS/HTTP/SMTP evidence.
   - **Email address I control** — receiver-directed mutations target that real mailbox/account instead.
4. Choose mutation families. **Two competing addresses in one field** is just one family: only those cases deliberately place both identity and receiver addresses inside the primary field.

If an additional receiver occurrence is selected, Atomizer still runs the same families against the primary occurrence and also replaces that second occurrence on each normal test. With Collaborator, the two roles receive separate correlation IDs.

## 1. Load it

In Burp Suite:

1. **Extensions -> Installed -> Add**
2. Extension type: **Java**
3. Select `email-atomizer-0.3.8.jar`
4. Open the **Email Atomizer** top-level tab

## Manual copy/paste: Mutation Strings

Open the **Mutation Strings** top-level sub-tab when you want payloads without an automated run.

1. Enter the **Identity / starting email**, or click **Use selected Email Atomizer email**.
2. Choose the alternate/receiver source:
   - **Fresh Burp Collaborator addresses** (default), or
   - **Email address I control**.
3. Select mutation families. All families are selected by default; **Receiver-directed only** is available as a quick filter.
4. Click **Generate mutation strings**. This generates values only; it does not send any HTTP request.
5. Choose a copy/paste format:
   - Raw
   - JSON escaped value
   - JSON string literal (includes quotes)
   - Form / query encoded (spaces `+`)
   - URL percent encoded (spaces `%20`)
   - Double URL encoded
6. Double-click/right-click rows to copy individual values, or use **Copy all strings** / **Copy all with IDs / families**.

Encoding changes reuse the exact same generated values. If Collaborator is selected, click **Generate mutation strings** again only when you intentionally want a fresh set of Collaborator payloads.


## Persistent Collaborator test accounts

Open **Collaborator Accounts** before creating the target accounts when you want stable A/B OAST-backed identities.

1. Account A defaults to local part `laikas` and label `gmail`; Account B defaults to `laikas` and `yahoo`. Change either before generation if needed.
2. Click **Generate / regenerate A** and **Generate / regenerate B**. This only creates Burp Collaborator payload roots; it sends no request to the target.
3. Copy the generated addresses and create the two target accounts. Atomizer does not start a timer or matrix, so take as long as needed while the extension remains loaded.
4. Use **Use as canonical** or **Use as controlled receiver** when you are ready to test.
5. SMTP/DNS/HTTP events for the generated roots appear in the **Atomizer Collaborator inbox**. Select an event for full evidence or click **Poll now** for an immediate fetch.

Each slot gets a distinct Collaborator root. This is useful for IDN/U-label/A-label tests because mutated labels can remain underneath the same controlled OAST root while the inbox still tells you whether activity belongs to Account A or Account B.

**Session note:** the generated addresses are persistent until explicit regeneration during the current loaded Atomizer/Burp session. Avoid unloading/reloading the extension in the middle of an account-creation experiment if you want Atomizer to keep polling the same Collaborator client context.

Mutation Strings payloads created with Burp Collaborator are also registered into this inbox, so delayed SMTP for a manually copied mutation is no longer discarded just because there is no matrix result row.

## 2. Browse normally

Passive discovery is enabled by default. It observes requests but does not modify or replay them.

The **Discoveries** tab records detected email-like values along with:

- host
- method
- GraphQL/RPC operation (when detected)
- occurrence index (`#`)
- path
- location (query/path, header, JSON body, form body, body)
- inferred parameter/header name
- normalized email value
- representation (literal, URL-encoded, JSON Unicode escaped)
- number of times the endpoint/value was seen

By default passive discovery is restricted to Burp scope.

## 3. Send a request to Atomizer

Either:

- right-click a request in Burp and choose **Email Atomizer -> Send to Email Atomizer**, or
- select/double-click a row in **Discoveries**.

Atomizer opens **Test Builder** and lists every detected email candidate from that request.

If the same canonical address exists in multiple places, such as a Cookie header and JSON body, choose the exact candidate you want. Literal and URL-encoded candidates are occurrence-aware so the selected occurrence is mutated without automatically replacing every duplicate.

## 4. Choose matrices

The builder exposes mutation families as selectable matrices.

Presets:

- **Conservative**: baseline, RFC parser probes, legacy routing, and basic encoded-word probes.
- **Full matrix**: all 95 cases (83 research-derived mutations plus 12 dual-address role probes).
- **Receiver-directed**: families whose parser probes can target either Collaborator or a controlled receiver mailbox.
- **Select all / none**: manual starting points.

A canonical HTTP baseline is automatically included for differential comparison even if you do not explicitly select the Baseline family.


### Two-email identity/receiver testing

There are two independent concepts, both available from the same UI:

- **Two competing addresses in one field** is a mutation family. Those 12 `DUAL-*` cases place the original identity address and alternate receiver address together inside the selected **primary occurrence** (`A,B`, `B,A`, display-name/mailbox shapes, and angle-bracket lists). Other mutation families do not automatically contain two complete addresses.
- **Additional receiver occurrence** controls a second email field that already exists elsewhere in the HTTP request. Leave it at **None** unless you explicitly want Atomizer to replace that second request occurrence during every normal test.

These can be used separately or together. For example, primary `#1` + second `None` + **Two competing addresses in one field** tests two-address parser ambiguity in one field. Primary `#1` + second `#2` + ordinary parser families mutates `#1` while independently filling `#2` with the chosen receiver source.

With Collaborator, primary receiver-directed payloads and an additional receiver occurrence use separate correlation IDs so the evidence identifies which role caused DNS/SMTP/HTTP activity.

## 5. Configure rate limits

Defaults:

```text
Minimum delay between requests: 1000 ms
Maximum tests: 0 (all selected)
Stop on HTTP 420/429 (configurable): enabled
Fallback 429 pause: 10000 ms
Respect Retry-After if continuing: disabled by default (opt-in)
```

Use **Maximum tests** for a quick subset and **STOP current run** to cancel after the currently executing request / Collaborator collection window.

## 6. Run and inspect Results

Each result includes:

- mutation ID and family
- submitted/mutated email
- HTTP status
- response body length
- delta from the canonical baseline
- signal classification
- Collaborator interaction type
- SMTP `RCPT TO`
- receiver override used for two-occurrence role testing
- notes

The **Signal** column is triage, not a vulnerability verdict:

- `HTTP differential` means the application handled the mutation differently.
- `OAST: ...` means a Collaborator interaction occurred.
- `HIGH: SMTP observed` means downstream SMTP behavior was observed for a non-baseline mutation.
- `RATE LIMITED` means the endpoint returned HTTP 420/429.

For email-parser research, the strongest observation is normally an SMTP conversation where the `RCPT TO` interpretation differs materially from the address accepted/authorized by the application.

## 7. Live mode

The original v0.1 live mutation mode remains available under **Live Mode**. It is off by default.

Set the selected/canonical email, choose one mutation, enable live mode, and browse. Exact canonical matches and common encoded forms are rewritten automatically. This mode is useful for stateful flows, but the passive -> Test Builder workflow is the recommended default for v0.3.

## 8. Export

The **Results** tab can export CSV including correlation IDs, HTTP differentials, signal classification, interactions, SMTP recipients, and notes.


## Inspecting and resuming results

Select any Results row to see its complete mutated request and HTTP response in the Request/Response tabs. If a configured stop code such as 420 or 429 halts a matrix, click **Resume remaining tests** after the target is ready again; Atomizer continues with the unsent tail and retains the original baseline for differential comparisons.


## Collaborator evidence

Select any Results row and open the **Collaborator** detail tab. Atomizer preserves every correlated interaction for that mutation. SMTP evidence includes the envelope recipient(s), full SMTP conversation, extracted DATA/message content, and a best-effort extraction of the message body. DNS and HTTP interactions also retain their raw details. This is intentionally separate from Burp's regular Collaborator tab because the extension uses its own `CollaboratorClient` context.

## Custom copy/paste mutation sets

Test Builder now includes a **Custom copy/paste mutation set** box. Paste one mutation per line; blank lines and lines beginning with `#` are ignored. The lines are appended to the checked automated matrices when **Include custom pasted mutations** is enabled.

You can paste concrete values directly:

```text
poc@gm\u1d43il.example.test
poc@gm\u2090il.example.test
```

Or use templates such as:

```text
{LOCAL}@gm\u1d43il.{DOMAIN}
{RECEIVER}
```

The parser accepts raw Unicode as well as Java-style `\uXXXX` escapes. Custom cases use the same request delay, stop conditions, resume controls, request/response capture, and CSV output as built-in cases.

## Unicode / IDNA normalization matrices

The built-in catalog includes dedicated families for compatibility normalization, width folding, Unicode domain separators, IDNA mapping differences, zero-width characters, accents, and cross-script confusable controls. The U+1D43 (`ᵃ`) and U+2090 (`ₐ`) domain replacements are marked as high-priority differential probes.

## Persistent Collaborator accounts in Results (v0.3.8)

When a matrix run uses Persistent Account A or B, Atomizer links that account root to the active mutation row before the request is sent. SMTP/DNS/HTTP received during the configured post-request Collaborator collection window is written both to the Results row and to the standalone Collaborator inbox. The normal Results CSV therefore includes `smtp_recipient`, message/body, interaction sequence, and raw Collaborator details for those events.

Persistent account roots are intentionally reused while you test many mutations. After that window Atomizer clears the row binding; the inbox is therefore the lossless record for delayed interactions and prevents them from being attributed to a later mutation that reuses the same root. Use **Export inbox CSV** when you want every persistent-account event regardless of row attribution.
