package io.github.sjh9714.subtitles.caption;

import java.util.ArrayList;
import java.util.List;

/** Flattens caption events into spoken words, keeping word start times and speaker changes. */
public final class CaptionParser {

    private static final String SPEAKER_CHANGE = ">>";

    private CaptionParser() {}

    public static List<Word> parse(List<CaptionEvent> events) {
        List<Word> words = new ArrayList<>();
        boolean newSpeaker = false;
        for (CaptionEvent event : events) {
            if (event.segments() == null) {
                continue;
            }
            for (CaptionEvent.Segment segment : event.segments()) {
                if (segment.text() == null) {
                    continue;
                }
                long startMs = event.startMs() + (segment.offsetMs() == null ? 0 : segment.offsetMs());
                for (String token : segment.text().trim().split("\\s+")) {
                    if (token.startsWith(SPEAKER_CHANGE)) {
                        newSpeaker = true;
                        token = token.substring(SPEAKER_CHANGE.length());
                    }
                    if (token.isEmpty() || isNonSpeech(token)) {
                        continue;
                    }
                    words.add(new Word(token, startMs, newSpeaker));
                    newSpeaker = false;
                }
            }
        }
        return words;
    }

    /** Bracketed tags such as [laughter], [music] or the censoring mark [ __ ], possibly spread over tokens. */
    private static boolean isNonSpeech(String token) {
        return token.startsWith("[") || token.startsWith("_") || token.endsWith("]");
    }
}
