# Security Policy

## Supported Versions

Security fixes are provided for the latest stable release line only.

| Version | Supported |
| --- | --- |
| `2.4.x` | Yes |
| `< 2.4.0` | No |
| `beta` / `alpha` / `snapshot` | No |

## Reporting a Vulnerability

If you discover a security vulnerability, please do not open a public issue, discussion, or publish a proof of concept before contacting us privately.

Please report security issues through one of the following channels:

- GitHub Security Advisories / private vulnerability report
- Email: `mail@sunnyhmz.top`

When possible, include:

- affected version(s)
- vulnerability type and impact
- reproduction steps or a minimal proof of concept
- suggested remediation details, if available

## Response Process

- Reports will be reviewed and validated as soon as possible.
- Confirmed security issues will be prioritized for a fix.
- Exploitable technical details will not be disclosed publicly before a fix is available.
- Once a fix is released, the relevant release notes will document the security-related changes when appropriate.

## Scope

This policy mainly covers security issues in:

- backend APIs and permission enforcement
- public page rendering
- admin console interactions and data handling
- Finder API and public REST API responses
- code directly maintained in this repository

Third-party dependency vulnerabilities may not always receive a separate public disclosure, but they will be updated or mitigated as compatibility allows.
