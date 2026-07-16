import { getState } from "../storage/settings-store.js";
import { applyTheme } from "../shared/theme.js";

const GOOGLE_URL = "https://www.google.com/";
const BLOCKED_PAGE_VIEW_MESSAGE = "SHIELD_FOCUS_BLOCKED_PAGE_VIEW";

function readQuery() {
  const params = new URLSearchParams(window.location.search);
  const delay = Number.parseInt(params.get("delay") ?? "", 10);
  const target = params.get("target") || "previous";
  let hostname = params.get("hostname") || "";
  const referrer = document.referrer ? (() => {
    try {
      return new URL(document.referrer);
    } catch {
      return null;
    }
  })() : null;

  if (!hostname && referrer?.hostname) {
    hostname = referrer.hostname;
  }

  return {
    hostname: hostname || "Unknown",
    reason: params.get("reason") || "Blocked by ShieldFocus",
    delay: Number.isFinite(delay) ? Math.min(60, Math.max(0, delay)) : 5,
    target: target === "google" ? "google" : "previous"
  };
}

function updateRing(ring, secondsLeft, totalSeconds) {
  if (!ring) {
    return;
  }

  const progress = totalSeconds <= 0 ? 0 : Math.max(0, Math.min(100, (secondsLeft / totalSeconds) * 100));
  ring.style.setProperty("--progress", String(progress));
}

function openGoogle() {
  window.location.replace(GOOGLE_URL);
}

function goBackOrGoogle() {
  if (window.history.length > 1) {
    window.history.back();
    return;
  }

  openGoogle();
}

function performRedirect(target) {
  if (target === "google") {
    openGoogle();
    return;
  }

  goBackOrGoogle();
}

function isPrivateContext() {
  return Boolean(
    globalThis.browser?.extension?.inIncognitoContext ??
    globalThis.chrome?.extension?.inIncognitoContext ??
    false
  );
}

function render() {
  const { hostname, reason, delay, target } = readQuery();
  const hostnameText = document.getElementById("hostnameText");
  const reasonText = document.getElementById("reasonText");
  const reasonValue = document.getElementById("reasonValue");
  const redirectLabel = document.getElementById("redirectLabel");
  const modeNotice = document.getElementById("modeNotice");
  const countdownValue = document.getElementById("countdownValue");
  const countdownCopy = document.getElementById("countdownCopy");
  const countdownRing = document.getElementById("countdownRing");
  const primaryAction = document.getElementById("primaryAction");
  const googleAction = document.getElementById("googleAction");

  let remaining = delay;
  let timerId = null;
  let redirected = false;

  const primaryLabel = target === "google" ? "Open Google now" : "Go back now";

  if (hostnameText) {
    hostnameText.textContent = hostname;
  }

  if (reasonText) {
    reasonText.textContent = "ShieldFocus blocked this page to keep browsing intentional and local.";
  }

  if (reasonValue) {
    reasonValue.textContent = reason;
  }

  if (redirectLabel) {
    redirectLabel.textContent = target === "google" ? "Google" : "Previous site";
  }

  if (modeNotice) {
    if (isPrivateContext()) {
      modeNotice.hidden = false;
      modeNotice.textContent = "Private window mode is active. ShieldFocus redirect pages are available here.";
    } else {
      modeNotice.hidden = true;
      modeNotice.textContent = "";
    }
  }

  if (primaryAction) {
    primaryAction.textContent = primaryLabel;
  }

  if (googleAction) {
    googleAction.textContent = target === "google" ? "Go back instead" : "Open Google";
  }

  const tick = () => {
    if (countdownValue) {
      countdownValue.textContent = String(remaining);
    }

    if (countdownCopy) {
      countdownCopy.textContent =
        remaining === 1
          ? `Redirecting in ${remaining} second`
          : `Redirecting in ${remaining} seconds`;
    }

    updateRing(countdownRing, remaining, delay);

    if (remaining <= 0) {
      window.clearInterval(timerId);
      if (!redirected) {
        redirected = true;
        performRedirect(target);
      }
      return;
    }

    remaining -= 1;
  };

  if (delay <= 0) {
    if (countdownValue) {
      countdownValue.textContent = "0";
    }
    if (countdownCopy) {
      countdownCopy.textContent = "Redirecting now";
    }
    updateRing(countdownRing, 0, 1);
    performRedirect(target);
    return;
  }

  void chrome.runtime.sendMessage({
    type: BLOCKED_PAGE_VIEW_MESSAGE,
    hostname,
    reason,
    score: 100
  });

  tick();
  timerId = window.setInterval(tick, 1000);

  primaryAction?.addEventListener("click", () => {
    if (redirected) {
      return;
    }

    redirected = true;
    window.clearInterval(timerId);
    performRedirect(target);
  });

  googleAction?.addEventListener("click", () => {
    if (redirected) {
      return;
    }

    redirected = true;
    window.clearInterval(timerId);
    openGoogle();
  });
}

async function init() {
  try {
    const state = await getState();
    applyTheme(state.settings.theme);
  } catch {
    applyTheme("system");
  }

  render();
}

void init();
