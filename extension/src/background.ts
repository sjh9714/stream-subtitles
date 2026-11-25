// Talks to the translation server. A content script's own requests are bound by the page's
// cross-origin rules, so every server call goes through this worker.
{
  const DEFAULTS: Settings = { server: "http://127.0.0.1:8787", token: "" };

  async function call(request: ServerRequest): Promise<ServerReply> {
    const position = `?positionMs=${request.positionMs}`;
    try {
      const { server, token } = await chrome.storage.local.get<Settings>(DEFAULTS);
      const headers: Record<string, string> = token ? { Authorization: `Bearer ${token}` } : {};
      const response =
        request.kind === "get"
          ? await fetch(`${server}/api/videos/${request.videoId}/subtitles${position}&have=${request.have}`, {
              headers,
            })
          : await fetch(`${server}/api/videos/${request.captions.videoId}/translation${position}`, {
              method: "POST",
              headers: { ...headers, "Content-Type": "application/json" },
              body: JSON.stringify(request.captions),
            });
      if (response.status === 404) {
        return { status: "NOT_FOUND" };
      }
      if (response.status === 401) {
        return { status: "DENIED" };
      }
      if (response.status === 429) {
        return { status: "LIMIT" };
      }
      if (!response.ok) {
        return { status: "FAILED" };
      }
      return (await response.json()) as ServerReply;
    } catch {
      return { status: "UNREACHABLE" };
    }
  }

  // A click on the icon asks for the video in that tab to be translated. Tabs without the content
  // script, which is every page that is not YouTube, have nobody to receive it.
  chrome.action.onClicked.addListener((tab) => {
    if (tab.id !== undefined) {
      const click: TranslateClick = { kind: "translate" };
      chrome.tabs.sendMessage(tab.id, click).catch(() => {});
    }
  });

  chrome.runtime.onMessage.addListener((request: ServerRequest, _sender, sendResponse) => {
    call(request).then(sendResponse);
    return true; // the reply is sent after the fetch finishes
  });
}
