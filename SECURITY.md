# Security Policy

## Reporting a vulnerability

Do not disclose exploitable vulnerabilities, credentials, tokens, private keys, personal data, or other sensitive security information in public issues, discussions, pull requests, or logs.

If GitHub private vulnerability reporting is enabled for this repository, use it as the preferred reporting channel. If it is not available, contact the repository owner through a private channel and provide only the information required to reproduce and assess the issue.

A useful report should include:

- affected component and version or commit when known;
- impact and realistic attack scenario;
- minimal reproduction steps;
- relevant logs or payloads with secrets removed;
- suggested mitigation when available.

## Handling security issues

Security fixes should be prioritized according to impact and exploitability. A confirmed vulnerability should be fixed in a focused branch and validated before disclosure.

Never commit real secrets. Development credentials must use local environment configuration or secret-management mechanisms appropriate to the deployment environment.

Dependency vulnerabilities that affect the application should be evaluated promptly. Automated dependency tooling may be added once Maven, npm, and GitHub Actions manifests are established.

## Supported versions

Before the first stable release, only the current development line is supported. After stable releases begin, supported versions must be documented here explicitly.
