# ShieldFocus Android

ShieldFocus Android is the mobile counterpart to the desktop browser extension.
The Android version should block domains at the device network level so Chrome, Edge, Opera, Firefox, Samsung Internet, and other browsers are covered without browser-specific integrations.

## Current Status

The Android repo now includes a starter project scaffold:

- Gradle project files
- Compose UI shell
- Main activity
- VPN service stub
- Shared rule/data models

It is ready for the next implementation phase, which is the actual VPN tunnel and domain filtering logic.

## Core Decision

Use a local `VpnService`-based filter.

That gives us:

- On-device domain filtering
- Browser coverage across the whole device
- No need to connect to each browser separately
- A privacy-preserving story that keeps traffic local

## What Reuses Well

The Android app can reuse the same concepts from the extension:

- Default blocked-domain list
- User blocklist and allowlist
- Categories
- Schedules
- Settings
- Decision logging
- Domain normalization and matching

## What Does Not Reuse Directly

The browser extension pieces do not transfer as-is:

- `chrome.declarativeNetRequest`
- content scripts
- Chrome service workers
- `chrome.runtime.sendMessage`
- popup/options pages built for the browser extension

## First Android Version

Start with a modest, reliable v1:

- Local VPN filter
- DNS/domain parsing
- Default blocklist
- Custom blocked domains
- Allowlist
- Schedule-aware decisions
- Local DataStore or Room persistence
- Foreground service notification
- Simple block/activity log

## Important Limitation

The desktop extension can inspect page metadata after load.
The Android app should not promise that in v1.

With HTTPS traffic, a VPN-based filter can reliably see network destinations, but it should not try to read titles, keywords, or visible text without crossing into intrusive territory.

So Android v1 should focus on:

- Known domain blocking
- Custom blocklists
- Allowlists
- Schedules
- Category lists
- Optional DNS-based classification

## Avoid For v1

These are not the foundation for ShieldFocus Android:

- Accessibility Service as the main browser inspection method
- Installing a certificate to decrypt HTTPS traffic
- Per-browser integrations

## Suggested Stack

- Kotlin
- Jetpack Compose
- VpnService
- DataStore or Room
- Foreground service for protection status

## Architecture

```mermaid
flowchart TD
  A["Android apps and browsers"] --> B["ShieldFocus local VPN"]
  B --> C["Domain parser / rule engine"]
  C --> D{"Match?"}
  D -->|Block| E["Drop traffic / deny"]
  D -->|Allow| F["Internet"]
```

## Roadmap

### Phase 1

- Android project skeleton
- Local VPN service
- Rule engine
- Settings storage
- Basic notification

### Phase 2

- Default blocklist support
- User blocklist and allowlist
- Schedule support
- Decision logging

### Phase 3

- Better UI
- Import/export
- Category management
- Persistent startup behavior

### Phase 4

- Optional advanced classification
- Store-readiness checks
- Policy review for Google Play

## Store Notes

Google Play will require the VPN service use to be declared clearly.
The app must explain that the VPN is used for local on-device filtering and not for routing traffic through ShieldFocus servers.

## Product Strategy

- Desktop extension: richer page behavior and browser-level controls
- Android app: device-wide domain filtering
- Shared rule format: same lists, same categories, same schedules, same settings concept
