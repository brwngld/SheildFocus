function readQuery() {
  const params = new URLSearchParams(window.location.search);
  return {
    hostname: params.get("hostname") || "Unknown",
    reason: params.get("reason") || "Unknown"
  };
}

function render() {
  const { hostname, reason } = readQuery();
  const hostnameText = document.getElementById("hostnameText");
  const reasonText = document.getElementById("reasonText");
  const reasonValue = document.getElementById("reasonValue");
  const backButton = document.getElementById("backButton");

  if (hostnameText) {
    hostnameText.textContent = hostname;
  }

  if (reasonText) {
    reasonText.textContent = "Protection is active because this page matched a blocking rule.";
  }

  if (reasonValue) {
    reasonValue.textContent = reason;
  }

  if (backButton) {
    backButton.addEventListener("click", () => {
      history.back();
    });
  }
}

render();

