package io.github.sjh9714.subtitles.caption;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Regroups time-cut caption words into sentences.
 *
 * <p>A sentence ends at the recognizer's sentence punctuation or where the speaker changes. Doing this
 * in code instead of asking the model keeps every subtitle's start time on its first word.
 */
public final class SentenceRegrouper {

    /** A run without punctuation is cut here so one subtitle cannot grow without bound. */
    static final int MAX_WORDS = 30;

    /** How long a subtitle stays after its last word starts when nothing follows it. */
    static final long LINGER_MS = 3000;

    private static final Pattern SENTENCE_END = Pattern.compile("[.?!][\"'”’)]*$");

    private SentenceRegrouper() {}

    public static List<Sentence> regroup(List<Word> words) {
        List<List<Word>> groups = new ArrayList<>();
        List<Word> current = new ArrayList<>();
        for (Word word : words) {
            if (word.newSpeaker() && !current.isEmpty()) {
                groups.add(current);
                current = new ArrayList<>();
            }
            current.add(word);
            if (SENTENCE_END.matcher(word.text()).find() || current.size() >= MAX_WORDS) {
                groups.add(current);
                current = new ArrayList<>();
            }
        }
        if (!current.isEmpty()) {
            groups.add(current);
        }

        List<Sentence> sentences = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) {
            List<Word> group = groups.get(i);
            long endMs = group.getLast().startMs() + LINGER_MS;
            if (i + 1 < groups.size()) {
                endMs = Math.min(endMs, groups.get(i + 1).getFirst().startMs());
            }
            String text = group.stream().map(Word::text).collect(Collectors.joining(" "));
            sentences.add(new Sentence(group.getFirst().startMs(), endMs, text, group.getFirst().newSpeaker()));
        }
        return sentences;
    }
}
