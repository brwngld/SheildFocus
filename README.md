# ShieldFocus

ShieldFocus is a local-first Chromium MV3 extension for domain blocking and privacy-preserving page classification.

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

## Current Status

- MV3 extension skeleton is in place.
- Local storage, logging, and the blocking engine are working.
- Allowlist, blocklist, categories, schedules, and custom rules are supported.
- Popup and options page are functional.
- Broadening test coverage is underway.

## Load In Chrome

1. Open `chrome://extensions`.
2. Turn on `Developer mode`.
3. Click `Load unpacked`.
4. Select the `ShieldFocus` folder.
5. Open the extension popup or the options page to manage rules.

## Chromium Family Support

ShieldFocus uses one shared Chromium-compatible package for:

- Google Chrome
- Microsoft Edge
- Opera desktop

The same `manifest.json` and source tree are used for all three browsers.

To stage a clean Chromium package:

```bash
node scripts/package-chromium.mjs
```

That command creates `dist/chromium/`, which you can load unpacked in Chrome, Edge, or Opera desktop.

## Firefox Support

Firefox desktop uses the same source code with a Firefox-ready manifest that includes the required `browser_specific_settings.gecko.id`.

To stage a Firefox package:

```bash
node scripts/package-firefox.mjs
```

That command creates `dist/firefox/`.

## Browser Support Notes

Supported:

- Chrome
- Edge
- Opera desktop
- Firefox desktop

Unsupported for now:

- Opera Mini
- UC Browser

## Verify

- Run the included smoke tests from the repo root.
- Re-check the options page after any storage or decision-engine change.
- Confirm blocked pages still redirect correctly in Chrome.

## Next Steps

- Finish UI polish later.
- Keep adding scenario coverage around precedence and persistence.
- Add local AI only after the rule-based flow stays stable.
- Finish Firefox-specific signing/publishing details next if we want store release support.
- Keep Opera Mini and UC Browser marked as unsupported unless their browser models change.

## Packaging Checklist

- Confirm the manifest parses cleanly.
- Confirm the placeholder icons are present in `assets/`.
- Load the extension from `chrome://extensions` in Developer mode.
- Open the popup and the options page once after loading.
- Run the smoke and integration tests from the repo root.
- Keep release notes focused on blocking behavior, privacy, and local storage.
