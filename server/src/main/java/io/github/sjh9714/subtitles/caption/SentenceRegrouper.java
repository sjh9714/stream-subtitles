package io.github.sjh9714.subtitles.caption;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Regroups time-cut caption words into sentences.
 *
 * <p>A sentence ends at the recognizer's sentence punctuation or where the speaker changes. Doing this
 * in code instead of asking the model keeps every subtitle's start time on its first word. A run that
 * goes on without sentence punctuation is cut where the speaker most likely paused.
 */
public final class SentenceRegrouper {

    /** A run that reaches this many words without sentence punctuation is cut so one subtitle cannot grow without bound. */
    static final int MAX_WORDS = 30;

    /** Such a cut keeps at least this many words, so it does not leave a subtitle a word or two long. */
    static final int MIN_WORDS = 15;

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
            if (SENTENCE_END.matcher(word.text()).find()) {
                groups.add(current);
                current = new ArrayList<>();
            } else if (current.size() >= MAX_WORDS) {
                int cut = cutPoint(current);
                groups.add(new ArrayList<>(current.subList(0, cut)));
                current = new ArrayList<>(current.subList(cut, current.size()));
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

    /**
     * How many words of an overlong run go into the first piece. The run is cut after its last comma,
     * or with no comma before the word that follows the longest pause.
     */
    private static int cutPoint(List<Word> run) {
        for (int count = run.size(); count >= MIN_WORDS; count--) {
            if (run.get(count - 1).text().endsWith(",")) {
                return count;
            }
        }
        int cut = run.size();
        long longestPause = 0;
        for (int count = MIN_WORDS; count < run.size(); count++) {
            long pause = run.get(count).startMs() - run.get(count - 1).startMs();
            if (pause > longestPause) {
                longestPause = pause;
                cut = count;
            }
        }
        return cut;
    }
}
