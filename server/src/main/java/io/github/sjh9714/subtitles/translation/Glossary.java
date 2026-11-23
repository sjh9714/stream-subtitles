package io.github.sjh9714.subtitles.translation;

import io.github.sjh9714.subtitles.caption.Sentence;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Hand-written terms: how a name or a term must be written where the recognizer or the model gets it
 * wrong. Terms come in groups. A group belongs to a channel, to a topic such as a game, or to every video.
 */
@Component
public class Glossary {

    /**
     * @param heard the forms the recognizer writes for the term
     * @param write how the subtitle must write it
     */
    public record Term(List<String> heard, String write) {}

    /**
     * @param channel the channel whose videos the group applies to, or null
     * @param topic words that show a video touches the group's topic, or null
     * @param about what the terms belong to. The model is told this so it can skip a term that does not fit.
     * @param source where the written forms come from
     */
    public record Group(String channel, List<String> topic, String about, String source, List<Term> terms) {}

    private final List<Group> groups;

    @Autowired
    Glossary(JsonMapper json) {
        this(read(json));
    }

    Glossary(List<Group> groups) {
        this.groups = groups;
    }

    /**
     * The groups that can apply to a video: its channel's, those whose topic is named in its title or
     * anywhere in its captions, and those that belong to every video.
     */
    public List<Group> groupsFor(String title, String channel, List<Sentence> sentences) {
        StringBuilder text = new StringBuilder(String.valueOf(title));
        sentences.forEach(sentence -> text.append('\n').append(sentence.text()));
        return groups.stream()
                .filter(group -> group.channel() == null || group.channel().equals(channel))
                .filter(group -> group.topic() == null
                        || group.topic().stream().anyMatch(word -> mentions(text, word)))
                .toList();
    }

    /** Whether the text has this form as a whole word or phrase, in any letter case. */
    static boolean mentions(CharSequence text, String form) {
        return Pattern.compile(
                        "(?<![\\p{L}\\p{N}])" + Pattern.quote(form) + "(?![\\p{L}\\p{N}])",
                        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                .matcher(text)
                .find();
    }

    private static List<Group> read(JsonMapper json) {
        try (InputStream in = new ClassPathResource("glossary.json").getInputStream()) {
            return json.readValue(in, new TypeReference<List<Group>>() {});
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
