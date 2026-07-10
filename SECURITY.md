# Security policy

## Reporting

This starter does not yet define a working private vulnerability-reporting
address. Before publishing the repository, its owner must enable GitHub private
vulnerability reporting or replace this paragraph with a monitored private
security contact. Do not put vulnerabilities, credentials, or camera data in a
public issue. A report should include the affected revision, impact,
reproduction steps, and a minimal proof of concept.

## Current security posture

- Camera is the only platform permission in the merged manifest. AndroidX also
  defines an app-signature permission for protected dynamic receivers.
- Camera pixels are processed locally and are not intentionally persisted. The
  upstream MediaPipe privacy notice says input images are not sent to Google.
- The standard MediaPipe Tasks binary can send API metrics. This build replaces
  its stats factory with MediaPipe's no-op logger and excludes DataTransport;
  the quality gate verifies both properties.
- Android backup is disabled.
- The Gradle distribution and downloaded MediaPipe model are verified by
  SHA-256.
- Dependency artifacts are checked against Gradle verification metadata.
- Dependencies resolve from Google Maven and Maven Central; build plugins also
  resolve from the Gradle Plugin Portal.
- CI uses read-only repository permissions.

This posture applies only to the current starter. Domain adapters, telemetry,
network access, stored presets, credentials, or external control outputs require
a new threat model, least-privilege permissions, input validation, secret
storage, and explicit user-visible privacy documentation.

The telemetry patch is tied to the pinned MediaPipe version. Every upgrade must
re-check the upstream logger implementation, patched factory, runtime dependency
graph, merged permissions, and physical-device initialization before release.

## Supported revisions

Security fixes are applied to the current `main` branch. Older tags are not
maintained unless the repository owner states otherwise.
