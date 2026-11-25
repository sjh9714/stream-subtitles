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

/** The chunk is the piece of the video the subtitle was translated in. Chunks arrive whole. */
interface Subtitle {
  chunk: number;
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
  | { kind: "get"; videoId: string; positionMs: number; have: number[] }
  | { kind: "post"; captions: Captions; positionMs: number };

/** Where the server is and the token it asks for. Set on the options page, kept in this browser. */
interface Settings {
  server: string;
  token: string;
}

/**
 * NOT_FOUND means the server has never seen the video. UNREACHABLE means the server did not answer.
 * DENIED means it did not accept the token. LIMIT means this month's spending cap is reached.
 * The subtitles are those of the chunks the request did not list, and ready says whether the position is covered.
 */
interface ServerReply {
  status: "PENDING" | "DONE" | "FAILED" | "NOT_FOUND" | "UNREACHABLE" | "DENIED" | "LIMIT";
  ready?: boolean;
  subtitles?: Subtitle[];
}
