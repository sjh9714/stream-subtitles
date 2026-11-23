// Shows a video's stored subtitles at the current playback time. A video is sent for translation
// only when the viewer clicks the extension's icon, because every translation costs money.
// While the server is still translating, this keeps asking, shows what is done so far and tells
// the server where the viewer is.
{
  const REQUEST = "stream-subtitles:read-captions";
  const RESULT = "stream-subtitles:captions";
  const POLL_MS = 2000;
  const NOTICE_MS = 4000;
  const READ_TRIES = 5;
  const NO_CAPTIONS = "no English captions";

  let videoId = "";
  let subtitles: Subtitle[] = [];
  /** Shown when no subtitle covers the current time: a wait or an error, or nothing. */
  let notice = "";
  let overlay: HTMLElement | null = null;
  /** Counts loads, so a load replaced by a newer one stops. */
  let loads = 0;

  const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

  function askServer(request: ServerRequest): Promise<ServerReply> {
    return chrome.runtime.sendMessage(request);
  }

  function askPage(): Promise<CaptionsResult> {
    return new Promise((resolve) => {
      const onMessage = (event: MessageEvent) => {
        if (event.source !== window || event.data?.type !== RESULT) {
          return;
        }
        window.removeEventListener("message", onMessage);
        resolve(event.data);
      };
      window.addEventListener("message", onMessage);
      window.postMessage({ type: REQUEST }, location.origin);
    });
  }

  /** Right after moving to another video the player can still hold the previous one, so this waits for it. */
  async function readCaptions(id: string): Promise<Captions> {
    for (let attempt = 1; ; attempt++) {
      const result = await askPage();
      if (result.videoId === id) {
        if (result.captions) {
          return result.captions;
        }
        throw new Error(result.error);
      }
      if (attempt === READ_TRIES) {
        throw new Error("the player is showing another video");
      }
      await sleep(1000);
    }
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

  /** Sets the notice. A wait stays until it is replaced. An error goes away by itself. */
  function tell(text: string, stays: boolean) {
    notice = text;
    render();
    if (text && !stays) {
      setTimeout(() => {
        if (notice === text) {
          notice = "";
          render();
        }
      }, NOTICE_MS);
    }
  }

  /**
   * Shows what the server has for this video. Without a click it only looks: a video the server
   * does not know is left alone, and so is a server that is not running.
   */
  async function load(id: string, clicked: boolean) {
    const mine = ++loads;
    const current = () => mine === loads && id === videoId;
    const send = async () => {
      tell("자막 번역 중", true); // reading a long video's captions alone takes several seconds
      return askServer({ kind: "post", captions: await readCaptions(id), positionMs: positionMs() });
    };
    try {
      let reply = await askServer({ kind: "get", videoId: id, positionMs: positionMs() });
      if (!clicked && (reply.status === "NOT_FOUND" || reply.status === "UNREACHABLE")) {
        return;
      }
      if (reply.status === "NOT_FOUND") {
        reply = await send();
      }
      let resent = false;
      while (current()) {
        subtitles = reply.subtitles ?? subtitles;
        if (reply.status === "FAILED" && !resent) {
          // The server was restarted or a chunk failed. Sending again translates only what is missing.
          resent = true;
          reply = await send();
          continue;
        }
        if (reply.status !== "PENDING") {
          break;
        }
        tell(reply.ready ? "" : "자막 번역 중", true);
        await sleep(POLL_MS);
        reply = await askServer({ kind: "get", videoId: id, positionMs: positionMs() });
      }
      if (!current()) {
        return; // the viewer moved to another video, or clicked again
      }
      if (reply.status === "DONE") {
        tell("", true);
      } else {
        tell(reply.status === "UNREACHABLE" ? "자막 서버에 연결할 수 없음" : "자막 번역 실패", false);
      }
    } catch (error) {
      if (current()) {
        const reason = error instanceof Error ? error.message : String(error);
        tell(reason === NO_CAPTIONS ? "영어 자막이 없는 영상" : `자막을 읽지 못함: ${reason}`, false);
      }
    }
  }

  function start() {
    const id = location.pathname === "/watch" ? (new URLSearchParams(location.search).get("v") ?? "") : "";
    if (id === videoId) {
      return;
    }
    videoId = id;
    subtitles = [];
    loads++;
    tell("", true);
    if (id) {
      load(id, false);
    }
  }

  // The video element does not bubble timeupdate, so it is caught on the way down.
  document.addEventListener("timeupdate", render, true);
  // YouTube changes videos without reloading the page and fires this event when it does.
  document.addEventListener("yt-navigate-finish", start);
  // The background worker passes on a click on the extension's icon.
  chrome.runtime.onMessage.addListener((message: TranslateClick) => {
    if (message?.kind !== "translate") {
      return;
    }
    start(); // the address changes before YouTube says it finished moving, so catch up first
    if (videoId) {
      load(videoId, true);
    }
  });
  start();
}
