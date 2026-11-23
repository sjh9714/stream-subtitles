/** One caption event in the shape the server reads. It is YouTube's json3 event with the unused fields dropped. */
interface CaptionEvent {
  tStartMs: number;
  segs: { utf8: string; tOffsetMs?: number }[];
}

interface Captions {
  videoId: string;
  title: string;
  channel: string;
  events: CaptionEvent[];
}

interface Subtitle {
  startMs: number;
  endMs: number;
  text: string;
}

/** What the page script answers when asked for captions. The video id is the one the player holds. */
interface CaptionsResult {
  videoId: string;
  captions?: Captions;
  error?: string;
}

/** What the background worker tells a tab when the viewer clicks the extension's icon. */
interface TranslateClick {
  kind: "translate";
}

/**
 * What the content script asks the background worker to do. Only the worker may call the server.
 * The position is where the viewer is watching, which the server translates first.
 */
type ServerRequest =
  | { kind: "get"; videoId: string; positionMs: number }
  | { kind: "post"; captions: Captions; positionMs: number };

/**
 * NOT_FOUND means the server has never seen the video. UNREACHABLE means the server did not answer.
 * While PENDING the subtitles are the part translated so far, and ready says whether that covers the position.
 */
interface ServerReply {
  status: "PENDING" | "DONE" | "FAILED" | "NOT_FOUND" | "UNREACHABLE";
  ready?: boolean;
  subtitles?: Subtitle[];
}
