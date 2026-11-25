package io.github.sjh9714.subtitles.translation;

import io.github.sjh9714.subtitles.caption.Sentence;
import io.github.sjh9714.subtitles.translation.Translator.VideoInfo;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/** Builds the user message for one chunk. The system prompt is resources/prompts/translate-v1.txt. */
final class TranslationPrompt {

    /** A glossary term in the form the captions have it. */
    private record Heard(String form, String write, String about) {}

    private TranslationPrompt() {}

    static String userMessage(VideoInfo video, Chunk chunk) {
        StringBuilder message = new StringBuilder();
        message.append("Title: ").append(video.title()).append('\n');
        message.append("Channel: ").append(video.channel()).append('\n');
        message.append("Register: casual Korean 반말.\n");
        List<Heard> terms = heard(video, chunk.sentences());
        if (!terms.isEmpty()) {
            message.append("\nGlossary:\n").append(lines(terms));
        }
        if (!chunk.context().isEmpty()) {
            message.append("\nPrevious context:\n");
            for (Sentence sentence : chunk.context()) {
                message.append(line(sentence, sentence.text())).append('\n');
            }
        }
        message.append("\nCaptions:\n");
        UnaryOperator<String> marked = marker(terms);
        List<Sentence> sentences = chunk.sentences();
        for (int i = 0; i < sentences.size(); i++) {
            Sentence sentence = sentences.get(i);
            message.append(i + 1).append('|').append(line(sentence, marked.apply(sentence.text()))).append('\n');
        }
        return message.toString();
    }

    /**
     * The glossary terms heard in these sentences, one per line, so a long glossary does not ride along
     * with every call.
     */
    static String termsHeard(VideoInfo video, List<Sentence> sentences) {
        return lines(heard(video, sentences));
    }

    private static List<Heard> heard(VideoInfo video, List<Sentence> sentences) {
        String text = sentences.stream().map(Sentence::text).collect(Collectors.joining("\n"));
        List<Heard> terms = new ArrayList<>();
        for (Glossary.Group group : video.glossary()) {
            for (Glossary.Term term : group.terms()) {
                term.heard().stream()
                        .filter(form -> Glossary.mentions(text, form))
                        .forEach(form -> terms.add(new Heard(form, term.write(), group.about())));
            }
        }
        return terms;
    }

    private static String lines(List<Heard> terms) {
        StringBuilder block = new StringBuilder();
        for (Heard term : terms) {
            block.append("- ")
                    .append(term.form())
                    .append(" = ")
                    .append(term.write())
                    .append(" (")
                    .append(term.about())
                    .append(")\n");
        }
        return block.toString();
    }

    /**
     * Puts the written form in braces after each heard form, as in {@code Mooney{Murin}}. The model skips
     * glossary entries it has to look up by itself, more so when the glossary is long. A longer form wins
     * over a shorter one inside it, and a word that is already written right is left unmarked.
     */
    private static UnaryOperator<String> marker(List<Heard> terms) {
        if (terms.isEmpty()) {
            return text -> text;
        }
        List<Heard> longestFirst = terms.stream()
                .sorted(Comparator.comparingInt((Heard term) -> term.form().length()).reversed())
                .toList();
        // One group per term, so the term that matched is known without comparing letter cases again.
        Pattern forms = Pattern.compile(
                longestFirst.stream()
                        .map(term -> "(" + Pattern.quote(term.form()) + ")")
                        .collect(Collectors.joining("|", "(?<![\\p{L}\\p{N}])(?:", ")(?![\\p{L}\\p{N}])")),
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        return text -> forms.matcher(text).replaceAll(match -> {
            String write = IntStream.range(0, longestFirst.size())
                    .filter(i -> match.group(i + 1) != null)
                    .mapToObj(i -> longestFirst.get(i).write())
                    .findFirst()
                    .orElse(match.group());
            return Matcher.quoteReplacement(
                    write.equals(match.group()) ? write : match.group() + "{" + write + "}");
        });
    }

    /**
     * Takes out the braces the model sometimes copies into its reply, as in {@code Mooney{Murin}} or
     * {@code Murin{Murin}}. What stays is the form in the braces.
     */
    static String withoutMarks(VideoInfo video, Chunk chunk, String reply) {
        List<Heard> terms = heard(video, chunk.sentences());
        if (terms.isEmpty()) {
            return reply;
        }
        String before = terms.stream()
                .flatMap(term -> Stream.of(term.form(), term.write()))
                .distinct()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .map(Pattern::quote)
                .collect(Collectors.joining("|"));
        return Pattern.compile("(?:" + before + ")?\\{([^{}\n]*)\\}", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                .matcher(reply)
                .replaceAll(match -> Matcher.quoteReplacement(match.group(1)));
    }

    private static String line(Sentence sentence, String text) {
        return (sentence.newSpeaker() ? ">> " : "") + text;
    }
}
