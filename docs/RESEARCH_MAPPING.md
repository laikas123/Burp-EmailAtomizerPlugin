# Research mapping

Email Atomizer implements the *testing strategy* from Gareth Heyes' PortSwigger Research article
“Splitting the Email Atom: exploiting parsers to bypass access controls” and independently implements
payload generators suitable for a Burp extension.

The core workflow is:

1. **Probe** whether the target accepts a parser edge case.
2. **Observe** downstream interpretation, with SMTP interactions being especially useful.
3. **Encode** blocked parser metacharacters using transformations the downstream parser understands.
4. **Exploit / validate impact** only after the discrepancy is understood.

## Mutation families

| Family | What Atomizer tests |
|---|---|
| RFC parser probes | Quoted local parts, comments/CFWS, quoted-pair handling |
| Legacy routing | UUCP bang paths, percent hack/source-route behavior, address-literal variation |
| Encoded-word probes | Q and Base64 encoded-word decoding with common charsets |
| Encoded-word split | `@` / `>` plus the control-byte and separator set used by the published Turbo Intruder strategy; both compact `x` and `iso-8859-1` variants |
| UTF-7 | Layered UTF-7 + Q/Base64 encoded-word transformations, including published terminator perturbations |
| Unicode overflow | Code points whose low byte becomes a blocked ASCII character under lossy modulo-256 conversion |
| SMTP ambiguity | ORCPT-style optional-parameter ambiguity through quoted/escaped local-part parsing |
| Malformed Punycode | Known malformed-IDN shapes used to probe buggy Punycode decoders |
| Two competing addresses in one field | **Atomizer-specific extension**: pairs the application identity address with a controlled receiver address in separator/display-name/address-list shapes to test identity-vs-delivery parser disagreement |

## Observation model

Every OAST-capable mutation receives a distinct Burp Collaborator payload and an alphanumeric custom
correlation ID. Email Atomizer polls the Collaborator client and associates interactions back to the
result row. For SMTP interactions it preserves the full conversation, extracts all observed `RCPT TO` values, extracts the SMTP DATA/message and best-effort body, and shows those artifacts in the result inspector so you can compare **submitted address** vs **mailer-interpreted recipient**.

When two-occurrence receiver mode is enabled, the primary mutation payload and the independently controlled receiver occurrence receive separate correlation IDs. The evidence view labels which role caused each interaction.

DNS-only interactions are retained as weak signals rather than treated as proof of a successful email
split. An SMTP `RCPT TO` showing an unexpected recipient is a much stronger parser-differential signal.

## Sources

- PortSwigger Research: https://portswigger.net/research/splitting-the-email-atom
- Research materials: https://github.com/PortSwigger/splitting-the-email-atom

The extension does not copy the PortSwigger Turbo Intruder implementation; the Java generators and
correlation workflow here are an independent implementation based on the published technique families.
