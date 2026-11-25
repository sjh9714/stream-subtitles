// The options page: where the translation server is and the token it asks for.
{
  const DEFAULTS: Settings = { server: "http://127.0.0.1:8787", token: "" };
  const server = document.getElementById("server") as HTMLInputElement;
  const token = document.getElementById("token") as HTMLInputElement;
  const result = document.getElementById("result") as HTMLElement;

  chrome.storage.local.get<Settings>(DEFAULTS).then((settings) => {
    server.value = settings.server;
    token.value = settings.token;
  });

  document.getElementById("save")!.addEventListener("click", async () => {
    let origin: string;
    try {
      origin = new URL(server.value.trim()).origin;
    } catch {
      result.textContent = "주소 형식이 맞지 않음";
      return;
    }
    // The browser lets the extension call only the servers the viewer allowed, and asks here.
    if (!(await chrome.permissions.request({ origins: [`${origin}/*`] }))) {
      result.textContent = "이 주소에 접속하도록 허용해야 함";
      return;
    }
    const settings: Settings = { server: origin, token: token.value.trim() };
    await chrome.storage.local.set(settings);
    server.value = origin;
    result.textContent = "저장함";
  });
}
