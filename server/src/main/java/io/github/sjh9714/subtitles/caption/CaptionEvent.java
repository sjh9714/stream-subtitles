package io.github.sjh9714.subtitles.caption;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * One event of a YouTube auto-caption track in its json3 form, as the browser extension reads it.
 * An event is a time-cut caption line, not a sentence.
 */
public record CaptionEvent(@JsonProperty("tStartMs") long startMs, @JsonProperty("segs") List<Segment> segments) {

    /** A piece of the line. Lines with word timing have one segment per word. */
    public record Segment(@JsonProperty("utf8") String text, @JsonProperty("tOffsetMs") Long offsetMs) {}
}
