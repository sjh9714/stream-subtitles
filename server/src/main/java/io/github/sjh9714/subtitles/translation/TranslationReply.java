package io.github.sjh9714.subtitles.translation;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the model's {@code <n>|<Korean>} lines and rejects a reply that does not cover every sentence once. */
final class TranslationReply {

    private static final Pattern LINE = Pattern.compile("^(\\d+)\\|(.*)$");

    private TranslationReply() {}

    static List<String> parse(String reply, int expected) {
        String[] lines = new String[expected];
        for (String raw : reply.split("\n")) {
            if (raw.isBlank()) {
                continue;
            }
            Matcher matcher = LINE.matcher(raw.strip());
            if (!matcher.matches()) {
                throw new MalformedReplyException("not a numbered line: " + raw);
            }
            int number = Integer.parseInt(matcher.group(1));
            if (number < 1 || number > expected || lines[number - 1] != null) {
                throw new MalformedReplyException("unexpected or repeated number: " + number);
            }
            lines[number - 1] = matcher.group(2).strip();
        }
        for (int i = 0; i < expected; i++) {
            if (lines[i] == null) {
                throw new MalformedReplyException("missing number: " + (i + 1));
            }
        }
        return Arrays.asList(lines);
    }

    static final class MalformedReplyException extends RuntimeException {
        MalformedReplyException(String message) {
            super(message);
        }
    }
}
