package io.github.sjh9714.subtitles.translation;

import io.github.sjh9714.subtitles.translation.Glossary.Group;
import io.github.sjh9714.subtitles.translation.Glossary.Term;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Reads the model's {@code <heard> = <written> | <what it belongs to>} lines. A line in another shape
 * is skipped, because a missing term costs less than asking the model again.
 */
public final class TermsReply {

    private static final Pattern LINE = Pattern.compile("^(.+?) = (.+?) \\| (.+)$");
    /** A longer field is not a term. The fields go into later requests to the model as they are. */
    private static final int MAX_LENGTH = 80;

    private TermsReply() {}

    /** Returns one group per line, each holding one term of this channel. */
    public static List<Group> parse(String reply, String channel, String source) {
        List<Group> groups = new ArrayList<>();
        for (String raw : reply.split("\n")) {
            Matcher matcher = LINE.matcher(raw.strip());
            if (!matcher.matches()) {
                continue;
            }
            List<String> heard = Arrays.stream(matcher.group(1).split(" / "))
                    .map(String::strip)
                    .filter(form -> !form.isEmpty())
                    .toList();
            String written = matcher.group(2).strip();
            String about = matcher.group(3).strip();
            if (heard.isEmpty()
                    || Stream.concat(heard.stream(), Stream.of(written, about))
                            .anyMatch(field -> field.length() > MAX_LENGTH)) {
                continue;
            }
            groups.add(new Group(channel, null, about, source, List.of(new Term(heard, written))));
        }
        return groups;
    }
}
