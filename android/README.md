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

## Strict Blocking: Current Coverage and Limitations

Strict Mode currently strengthens domain filtering by:

- Blocking exact domains and all of their subdomains
- Detecting the same site label across alternative top-level domains
- Detecting common numbered, mirror, proxy, official, and unblocked variants
- Intercepting direct IPv4 and IPv6 UDP DNS requests sent to the device resolver and a bounded set of well-known public resolvers
- Restarting the VPN automatically when Strict Mode changes so the new routes and policy take effect
- A future full-tunnel forwarder before supporting Android Always-on VPN lockdown

Strict Mode is deliberately implemented without a default VPN route. The current tunnel understands direct IPv4 and IPv6 UDP DNS packets; routing all device traffic into it would drop unsupported TCP and general application traffic.

The following bypasses or limitations remain:

- **Unknown DNS-over-HTTPS providers:** ShieldFocus fails closed for encrypted connections to the known resolver IPs it routes, but an app can use another DoH provider or relay that is not yet known to ShieldFocus.
- **IPv6 extension headers:** Direct IPv6 UDP DNS is supported, including valid response checksums. IPv6 packets that use extension-header chains are not parsed yet.
- **DNS over TCP:** DNS clients that fall back to TCP are not currently forwarded by the tunnel.
- **Direct-IP access:** A connection made directly to a server IP may not reveal a hostname, so it cannot be classified reliably by the domain policy.
- **Changing aliases:** Adult sites can introduce unrelated mirror names that contain no recognizable blocked-domain label. Updated curated lists are still required.
- **Explicit allow rules:** A custom allow rule intentionally takes precedence over strict domain-family blocking.

### Strict Blocking Roadmap

Implement these as separate, tested phases to avoid breaking device connectivity:

1. Add IPv6 extension-header traversal for UDP DNS packets that are not carried directly after the base IPv6 header.
2. Add DNS-over-TCP handling for routed resolver addresses.
3. Maintain an updateable local list of known DoH resolver hostnames and IP addresses.
4. Detect and fail closed on unsupported encrypted-DNS traffic only when Strict Mode is enabled.
5. Evaluate a full dual-stack traffic-forwarding tunnel for hostname-to-IP correlation and direct-IP enforcement.
6. Add authentication around custom allow rules and protection changes when Protection Lock is enabled.

Do not claim that ShieldFocus blocks every possible endpoint until the full-tunnel and encrypted-DNS phases are complete. The current DNS-only split tunnel opts out of Android Always-on VPN and must not be used with **Block connections without VPN**, which blocks traffic outside the configured DNS routes.

## Advanced DNS Settings

The existing Settings screen links to a dedicated **Advanced Settings** page. It exposes network controls separately so protocol-specific problems can be isolated:

- **IPv4 DNS protection:** controls the IPv4 VPN address, assigned DNS routes, and fallback resolvers.
- **IPv6 DNS protection:** independently controls the IPv6 VPN address and equivalent IPv6 resolver routes.
- **DNS response timeout:** selects a 1, 2, 4, or 6 second UDP forwarding timeout.
- **Per-family runtime health:** shows Active, Ready, Disabled, or Problem independently for IPv4 and IPv6, together with processed-request and forwarding-failure counts for the current VPN session.
- **Google SafeSearch:** maps Google and supported regional Google search domains to Google's enforced SafeSearch endpoint.

At least one IP family must remain enabled. Changing IPv4, IPv6, Strict Mode, or the DNS timeout while protection is active performs a controlled VPN restart so the new routes and forwarder configuration take effect.

For diagnosis, disable one family temporarily and test protection again. If the problem disappears, the disabled protocol or its resolver path is the likely source. Re-enable both families after testing for complete dual-stack DNS coverage.

SafeSearch filters explicit results on providers that expose a network-enforcement endpoint. ShieldFocus's DNS-only VPN cannot read or block individual search keywords because HTTPS encrypts the search path and query. Unsupported providers require their own network endpoint, a managed-browser policy, or a browser extension; TLS interception is intentionally out of scope.

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
