// Asks the server for a video's subtitles, sends the captions when the server has none,
// and shows the subtitle that belongs to the current playback time. While the server is still
// translating, it keeps asking, shows what is done so far and tells the server where the viewer is.
{
  const REQUEST = "stream-subtitles:read-captions";
  const RESULT = "stream-subtitles:captions";
  const POLL_MS = 2000;

  let videoId = "";
  let subtitles: Subtitle[] = [];
  /** Shown when no subtitle covers the current time: a wait or an error, or nothing. */
  let notice = "";
  let overlay: HTMLElement | null = null;

  function askServer(request: ServerRequest): Promise<ServerReply> {
    return chrome.runtime.sendMessage(request);
  }

  function readCaptions(): Promise<Captions> {
    return new Promise((resolve, reject) => {
      const onMessage = (event: MessageEvent) => {
        if (event.source !== window || event.data?.type !== RESULT) {
          return;
        }
        window.removeEventListener("message", onMessage);
        event.data.captions ? resolve(event.data.captions) : reject(new Error(event.data.error));
      };
      window.addEventListener("message", onMessage);
      window.postMessage({ type: REQUEST }, location.origin);
    });
  }

  /** Puts text over the video. It lives inside the player so it also shows in fullscreen. */
  function show(text: string) {
    const player = document.getElementById("movie_player");
    if (!player) {
      return;
    }
    if (!overlay || !overlay.isConnected) {
      overlay = document.createElement("div");
      overlay.style.cssText =
        "position:absolute;left:50%;bottom:12%;transform:translateX(-50%);max-width:80%;z-index:60;" +
        "padding:4px 10px;border-radius:4px;background:rgba(8,8,8,.75);color:#fff;" +
        "font-size:2.2em;line-height:1.4;text-align:center;pointer-events:none;white-space:pre-wrap";
      player.appendChild(overlay);
    }
    overlay.textContent = text;
    overlay.style.display = text ? "block" : "none";
  }

  /** The subtitle covering this time, found by binary search over the start times. */
  function subtitleAt(ms: number): Subtitle | undefined {
    let low = 0;
    let high = subtitles.length - 1;
    while (low < high) {
      const middle = Math.ceil((low + high) / 2);
      if (subtitles[middle].startMs <= ms) {
        low = middle;
      } else {
        high = middle - 1;
      }
    }
    const candidate = subtitles[low];
    return candidate && candidate.startMs <= ms && ms < candidate.endMs ? candidate : undefined;
  }

  function positionMs(): number {
    const video = document.querySelector<HTMLVideoElement>("#movie_player video");
    return video ? Math.floor(video.currentTime * 1000) : 0;
  }

  function render() {
    // While an ad plays, the video element's time belongs to the ad.
    const inAd = document.getElementById("movie_player")?.classList.contains("ad-showing");
    show(inAd ? "" : (subtitleAt(positionMs())?.text ?? notice));
  }

  async function load(id: string) {
    let reply = await askServer({ kind: "get", videoId: id, positionMs: positionMs() });
    if (reply.status === "NOT_FOUND" || reply.status === "FAILED") {
      const captions = await readCaptions();
      if (captions.videoId !== id) {
        throw new Error("the player is showing another video");
      }
      reply = await askServer({ kind: "post", captions, positionMs: positionMs() });
    }
    while (id === videoId) {
      subtitles = reply.subtitles ?? subtitles;
      if (reply.status !== "PENDING") {
        break;
      }
      notice = reply.ready ? "" : "자막 번역 중";
      render();
      await new Promise((resolve) => setTimeout(resolve, POLL_MS));
      reply = await askServer({ kind: "get", videoId: id, positionMs: positionMs() });
    }
    if (id !== videoId) {
      return; // the viewer moved to another video while this one was loading
    }
    notice =
      reply.status === "DONE" ? "" : reply.status === "UNREACHABLE" ? "자막 서버에 연결할 수 없음" : "자막 번역 실패";
    render();
  }

  function start() {
    const id = location.pathname === "/watch" ? (new URLSearchParams(location.search).get("v") ?? "") : "";
    if (id === videoId) {
      return;
    }
    videoId = id;
    subtitles = [];
    notice = "";
    render();
    if (id) {
      load(id).catch((error) => {
        if (id === videoId) {
          notice = `자막을 읽지 못함: ${error.message}`;
          render();
        }
      });
    }
  }

  // The video element does not bubble timeupdate, so it is caught on the way down.
  document.addEventListener("timeupdate", render, true);
  // YouTube changes videos without reloading the page and fires this event when it does.
  document.addEventListener("yt-navigate-finish", start);
  start();
}
