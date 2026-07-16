(function () {
  function cleanText(value, maxLength = 240) {
    const text = String(value ?? "").replace(/\s+/g, " ").trim();
    if (text.length <= maxLength) {
      return text;
    }
    return `${text.slice(0, maxLength - 1)}…`;
  }

  function getMetaContent(name) {
    const selector = `meta[name="${name}"]`;
    const element = document.querySelector(selector);
    return cleanText(element?.getAttribute("content") ?? "", 240);
  }

  function extractPageMetadata() {
    return {
      url: cleanText(location.href, 2000),
      hostname: cleanText(location.hostname, 255),
      title: cleanText(document.title, 240),
      description: getMetaContent("description"),
      keywords: getMetaContent("keywords")
    };
  }

  globalThis.ShieldFocusMetadataExtractor = {
    extractPageMetadata
  };
})();

