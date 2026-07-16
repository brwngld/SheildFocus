(function () {
  const MESSAGE_TYPE = "SHIELD_FOCUS_PAGE_SCAN";
  let reported = false;

  function redirectToBlockedPage(blockPageUrl) {
    if (!blockPageUrl) {
      return;
    }

    window.location.replace(blockPageUrl);
  }

  async function reportPage() {
    if (reported) {
      return;
    }

    const extractor = globalThis.ShieldFocusMetadataExtractor;
    if (!extractor?.extractPageMetadata) {
      return;
    }

    reported = true;

    try {
      const response = await chrome.runtime.sendMessage({
        type: MESSAGE_TYPE,
        page: extractor.extractPageMetadata()
      });

      if (response?.action === "BLOCK") {
        redirectToBlockedPage(response.blockPageUrl);
      }
    } catch {
      reported = false;
    }
  }

  if (document.readyState === "complete" || document.readyState === "interactive") {
    void reportPage();
  } else {
    window.addEventListener("DOMContentLoaded", () => {
      void reportPage();
    }, { once: true });
  }
})();

