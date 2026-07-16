export function createMessage(type, payload = {}) {
  return { type, ...payload };
}

export function sendMessage(message) {
  return chrome.runtime.sendMessage(message);
}

