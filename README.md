# Email Atomizer

A Burp Suite Professional extension for controlled email-parser differential testing during authorized pentests and bug-bounty work.

The normal workflow is:

> **Browse normally → find an email-bearing request → send it to Email Atomizer → choose what to mutate → run a rate-limited matrix → review HTTP / Collaborator / SMTP evidence**

The mutation strategy is inspired by PortSwigger Research's **Splitting the Email Atom** methodology.

## Requirements

- Burp Suite Professional
- JDK 21 if building from source
- Montoya API `2026.7`

## Build

```bash
./build.sh
```

If Gradle is not installed on macOS:

```bash
brew install gradle
./build.sh
```

For the latest version represented by these docs, the JAR is written to:

```text
build/libs/email-atomizer-0.3.3.jar
```

## Load the extension in Burp

1. Open **Extensions → Installed → Add**.
2. Choose **Java**.
3. Select the Email Atomizer JAR.
4. Open the **Email Atomizer** top-level tab.
5. Leave passive discovery enabled and browse the target normally.

Passive discovery observes requests without mutating or replaying them.

## Run tests against a request

### 0. Enable email request disocvery 

Make sure **Passive email discovery** is checked in the discoveries tab of the extension. This will allow the tool to auto discover requests that contain email addresses.

### 1. Send a request to Email Atomizer

After making an interesting request containing an email address, open the **Discoveries** tab of the extension then choose **Send selected to Test Builder**.

The exact stored request is retained so the Test Builder can replay mutations against the original request structure.

### 2. Choose the email occurrence to test

Every detected email occurrence is numbered (`#1`, `#2`, ...), including repeated copies of the same email in different places.

In **Test Builder**:

- **Primary email occurrence to test** — the exact occurrence Atomizer will mutate.
- **Additional receiver occurrence (optional)** — optionally choose a different occurrence that should be independently replaced with the selected receiver address.
- **Alternate / receiver address used by tests** — choose either:
  - **Burp Collaborator** for fresh OAST receiver addresses.
  - **Email address I control** for a real mailbox or test account you control.

The **What this run will do** panel summarizes the selected primary occurrence, optional secondary occurrence, receiver source, and dual-address behavior before anything is sent.

### Common setups

#### Test one email field with Collaborator

Use:

- Primary: the email occurrence you want to mutate.
- Additional receiver occurrence: `None`.
- Receiver: **Burp Collaborator**.

This is the normal parser/OAST workflow.

#### Test one email field with a mailbox you control

Use:

- Primary: the email occurrence you want to mutate.
- Additional receiver occurrence: `None`.
- Receiver: **Email address I control**.

Receiver-directed mutations will be built around that mailbox instead of a Collaborator address.

#### Test two separate email fields

If a request contains two email fields, select one as the **Primary** and the other as the **Additional receiver occurrence**.

Atomizer mutates the primary occurrence while independently replacing the secondary occurrence with the selected Collaborator or controlled-mailbox receiver.

#### Put two competing addresses in one field

Select the **Two competing addresses in one field** mutation family.

This generates shapes such as competing address-list, display-name, and ordering variants that place both the original identity address and the alternate receiver inside the primary field.

## Choose mutation families

Select only the mutation families you want to run, or use one of the presets:

- **Conservative**
- **Full matrix**
- **Receiver-directed**
- **Select all / none**

The catalog includes parser and routing families such as quoted/comment probes, UUCP/bang paths, percent routing, RFC 2047 encoded words, control-byte splits, UTF-7 layering, Unicode/IDNA cases, ORCPT ambiguity, malformed Punycode, and dual-address probes.

## Run custom pasted payloads

For cases where you already have a payload list, Test Builder can run custom mutations through the normal replay and evidence pipeline.

1. Paste one payload per line into the custom mutation area.
2. Use raw Unicode or `\uXXXX` escapes as needed.
3. Run the set through Test Builder.

Templates can reference:

```text
{EMAIL}
{LOCAL}
{DOMAIN}
{ALLOWED_DOMAIN}
{RECEIVER}
{RECEIVER_LOCAL}
{RECEIVER_DOMAIN}
{COLLAB_HOST}
{CORRELATION}
```

Custom runs use the same rate limiting, stop conditions, result collection, and evidence handling as built-in matrices.

## Configure rate limiting and stop conditions

The documented defaults are:

- Minimum spacing: **1000 ms** between requests.
- Collaborator post-request collection window: **3000 ms** for OAST-capable cases.
- Maximum tests: **0**, meaning all selected tests.
- Stop-status codes: **420,429**.
- Only send/mutate Burp-scope requests: enabled.

`Retry-After` is parsed when present.

### Stop on response text

Atomizer can also stop when the response body contains configured text, case-insensitively.

The default text pattern is:

```text
tooManyAttempts
```

Add additional literal substrings one per line.

### Delivery-path sentinels

Optional sentinels can send a fresh direct Collaborator recipient at the start and end of a run, and optionally every N mutation tests.

Use these when you need to tell the difference between:

- a parser-negative mutation, and
- a mail path that became throttled or unavailable during the run.

Sentinel outcomes include:

- `SENTINEL OK: SMTP observed`
- `SENTINEL PARTIAL: ...`
- `SENTINEL WARNING: no OAST in window`

Sentinels are disabled by default because they create additional real delivery attempts.

## Start and stop a run

After configuring the test:

1. Click **Run selected matrices**.
2. Watch the **Run Log** and **Results** tabs.
3. Use **STOP current run** if you need to cancel.

A true null/no HTTP response is automatically retried once before being marked as no-response.

## Resume after a stop or cancellation

If a run stops because of a configured HTTP status, response-text match, cancellation, or interruption, Atomizer keeps a checkpoint with the unsent portion of the matrix.

You can then choose:

- **Resume remaining tests** — skip the stopped case and continue with the first unsent mutation.
- **Retry stopped case + continue** — replay the mutation that triggered the stop, then continue with the remaining tests.

If the retried case hits a stop condition again, the checkpoint is preserved again.

## Review results

Select a row in **Results** to inspect the full raw **Request** and **Response** beneath the table.

The **Signal** column highlights useful differentials such as:

- HTTP status/body-length changes
- Collaborator interactions
- SMTP observations
- Rate limiting
- Sentinel outcomes

Result rows can be sent to:

- **Repeater**
- **Intruder**
- **Organizer**
- **Email Atomizer Test Builder**

Results can also be exported to CSV.

## Review Collaborator / SMTP evidence

For Collaborator-backed cases, the dedicated **Collaborator** evidence tab can show correlated:

- DNS interactions
- HTTP interactions
- SMTP interactions
- Interaction ID, type, time, and client
- Raw protocol details
- Full SMTP conversation
- Parsed envelope `RCPT TO`
- Extracted SMTP DATA/message
- Best-effort message body

Each OAST-capable mutation uses a unique Collaborator payload/correlation value. Primary mutations and additional receiver occurrences use separate correlation IDs so you can tell which role caused the interaction.

Multiple events from one payload are retained separately, such as DNS → DNS → SMTP.

## Generate mutation strings without replaying requests

Use the top-level **Mutation Strings** tab when you want copy/paste-ready payloads for Repeater, Intruder, browser testing, or another manual workflow.

1. Enter the starting/identity email.
2. Choose the mutation families.
3. Choose the receiver source:
   - Fresh **Burp Collaborator** addresses, or
   - **Email address I control**.
4. Click **Generate mutation strings**.
5. Copy individual rows or use **Copy all strings** / **Copy all with IDs / families**.

You can switch generated output between:

- **Raw**
- **JSON escaped value**
- **JSON string literal**
- **Form / query encoded**
- **URL percent encoded**
- **Double URL encoded**

Changing the display/copy format does **not** regenerate Collaborator addresses. Click **Generate mutation strings** again only when you intentionally want fresh payloads.

## Scope and side effects

Matrix mode can trigger real email, password-reset, signup, account, or delivery actions. Use the extension only on systems you are authorized to test, keep relevant requests in Burp scope, and tune delays and stop conditions for the endpoint's side effects and rate limits.

## Research basis

Primary methodology:

- https://portswigger.net/research/splitting-the-email-atom
- https://github.com/PortSwigger/splitting-the-email-atom

The extension independently implements the mutation generators and Burp workflow.
