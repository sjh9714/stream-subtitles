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

/** What the content script asks the background worker to do. Only the worker may call the server. */
type ServerRequest = { kind: "get"; videoId: string } | { kind: "post"; captions: Captions };

/** NOT_FOUND means the server has never seen the video. UNREACHABLE means the server did not answer. */
interface ServerReply {
  status: "PENDING" | "DONE" | "FAILED" | "NOT_FOUND" | "UNREACHABLE";
  subtitles?: Subtitle[];
}
