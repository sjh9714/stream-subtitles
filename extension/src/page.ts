// Runs in the page itself, not in the extension's isolated world, because only the page can
// read the player's caption track list and fetch the captions with the viewer's own session.
{
  const REQUEST = "stream-subtitles:read-captions";
  const RESULT = "stream-subtitles:captions";

  async function readCaptions(response: any): Promise<Captions> {
    const tracks: any[] = response?.captions?.playerCaptionsTracklistRenderer?.captionTracks ?? [];
    // Auto-generated captions carry a start time for every word. Uploaded ones do not.
    const track =
      tracks.find((t) => t.languageCode === "en" && t.kind === "asr") ??
      tracks.find((t) => String(t.languageCode).startsWith("en"));
    if (!track) {
      throw new Error("no English captions");
    }
    const body = await (await fetch(track.baseUrl + "&fmt=json3")).json();
    const events: CaptionEvent[] = body.events
      .filter((event: any) => event.segs)
      .map((event: any) => ({
        tStartMs: event.tStartMs,
        segs: event.segs.map((seg: any) => ({ utf8: seg.utf8, tOffsetMs: seg.tOffsetMs })),
      }));
    return {
      videoId: response.videoDetails.videoId,
      title: response.videoDetails.title,
      channel: response.videoDetails.author,
      events,
    };
  }

  window.addEventListener("message", (event) => {
    if (event.source !== window || event.data?.type !== REQUEST) {
      return;
    }
    const player = document.getElementById("movie_player") as any;
    const response = player?.getPlayerResponse?.();
    // The video id goes back even on failure, so the asker can tell an answer about another video.
    const videoId: string = response?.videoDetails?.videoId ?? "";
    const reply = (result: Omit<CaptionsResult, "videoId">) =>
      window.postMessage({ type: RESULT, videoId, ...result }, location.origin);
    readCaptions(response).then(
      (captions) => reply({ captions }),
      (error) => reply({ error: error instanceof Error ? error.message : String(error) }),
    );
  });
}
