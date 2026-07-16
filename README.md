# ShieldFocus

ShieldFocus is a local-first Chrome MV3 extension for domain blocking and privacy-preserving page classification.

## Architecture

- `background/service-worker.js` is the policy hub.
- `background/rule-manager.js` turns allow/block lists into fast Declarative Net Request rules.
- `background/decision-engine.js` combines lists, safe-domain signals, and classifier output.
- `content/metadata-extractor.js` reads only safe metadata.
- `content/page-scanner.js` sends limited page data to the service worker for a decision.
- `storage/settings-store.js` keeps settings and domain lists inside `chrome.storage.local`.
- `storage/decision-log.js` stores only small decision records.
- `classifiers/` contains the rule-based classifier interfaces for later AI expansion.
- `pages/blocked.html` and `pages/options.html` are the only UI surfaces in this first pass.

## Privacy Guardrails

- No backend.
- No database.
- No passwords.
- No form input capture.
- No full browsing history capture.
- No external AI calls in the initial version.

## Blocking Flow

1. Normalize the hostname.
2. Check the allowlist first.
3. Check the user blocklist and default blocklist.
4. Push known domains into DNR for pre-load blocking.
5. Scan only page metadata when the page is not already blocked.
6. Combine rule-based scores into a final allow/block decision.

