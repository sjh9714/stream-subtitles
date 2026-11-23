package io.github.sjh9714.subtitles.translation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.github.sjh9714.subtitles.caption.Sentence;
import io.github.sjh9714.subtitles.translation.Glossary.Group;
import io.github.sjh9714.subtitles.translation.Glossary.Term;
import io.github.sjh9714.subtitles.translation.Translator.VideoInfo;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class GlossaryTest {

    private static final Group OF_CHANNEL =
            new Group("a channel", null, "this channel", "", List.of(new Term(List.of("Mooney"), "Murin")));
    private static final Group OF_GAME = new Group(
            null, List.of("Deadlock"), "the game Deadlock", "", List.of(new Term(List.of("Haze", "Hazel"), "Haze")));
    private static final Group OF_ANY =
            new Group(null, null, "a show title", "", List.of(new Term(List.of("Squid Game"), "오징어 게임")));

    private final Glossary glossary = new Glossary(List.of(OF_CHANNEL, OF_GAME, OF_ANY));

    private static Sentence said(String text) {
        return new Sentence(0, 1000, text, false);
    }

    @Test
    void channelGroupAppliesOnlyToItsChannel() {
        assertThat(glossary.groupsFor("a title", "a channel", List.of())).containsExactly(OF_CHANNEL, OF_ANY);
        assertThat(glossary.groupsFor("a title", "another channel", List.of())).containsExactly(OF_ANY);
    }

    @Test
    void topicGroupAppliesWhenTheTopicIsNamedInTheTitleOrAnywhereInTheCaptions() {
        assertThat(glossary.groupsFor("DEADLOCK night", "x", List.of())).contains(OF_GAME);
        assertThat(glossary.groupsFor("a title", "x", List.of(said("I played deadlock yesterday.")))).contains(OF_GAME);
        assertThat(glossary.groupsFor("a title", "x", List.of(said("We are deadlocked.")))).doesNotContain(OF_GAME);
    }

    @Test
    void promptCarriesOnlyTheTermsHeardInTheChunkAsWholeWords() {
        VideoInfo video = new VideoInfo("a title", "a channel", List.of(OF_CHANNEL, OF_GAME, OF_ANY));
        Chunk chunk = new Chunk(0, List.of(), List.of(said("Hazel lane was rough."), said("Ask Mooney's chat.")));

        String message = TranslationPrompt.userMessage(video, chunk);

        assertThat(message).contains("- Hazel = Haze (the game Deadlock)\n", "- Mooney = Murin (this channel)\n");
        assertThat(message).doesNotContain("- Haze = Haze", "Squid Game");
    }

    @Test
    void promptHasNoGlossaryBlockWhenNothingIsHeard() {
        VideoInfo video = new VideoInfo("a title", "a channel", List.of(OF_GAME));
        Chunk chunk = new Chunk(0, List.of(), List.of(said("Nothing special.")));

        assertThat(TranslationPrompt.userMessage(video, chunk)).doesNotContain("Glossary");
    }

    @Test
    void shippedGlossaryFileCanBeRead() {
        assertThatCode(() -> new Glossary(JsonMapper.builder().build())).doesNotThrowAnyException();
    }
}
