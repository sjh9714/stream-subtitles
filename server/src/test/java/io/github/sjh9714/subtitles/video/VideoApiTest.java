package io.github.sjh9714.subtitles.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.sjh9714.subtitles.translation.Chunk;
import io.github.sjh9714.subtitles.translation.Glossary.Term;
import io.github.sjh9714.subtitles.translation.TermExtractor;
import io.github.sjh9714.subtitles.translation.Translator;
import io.github.sjh9714.subtitles.translation.Translator.Reply;
import io.github.sjh9714.subtitles.translation.Translator.VideoInfo;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Runs the whole request path against a real PostgreSQL, with only the model call replaced. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class VideoApiTest {

    /** One sentence cut across two caption lines, then a second sentence. */
    private static final String CAPTIONS = """
            {"title": "a title", "channel": "a channel", "events": [
              {"tStartMs": 1000, "segs": [{"utf8": "It"}, {"utf8": " was", "tOffsetMs": 200}]},
              {"tStartMs": 1500, "segs": [{"utf8": "late."}, {"utf8": " So", "tOffsetMs": 600},
                                          {"utf8": " I", "tOffsetMs": 800}, {"utf8": " left.", "tOffsetMs": 1100}]}
            ]}
            """;

    /** One sentence in the first 5-minute chunk and one in the second. */
    private static final String TWO_CHUNKS = """
            {"title": "a title", "channel": "a channel", "events": [
              {"tStartMs": 1000, "segs": [{"utf8": "Hello."}]},
              {"tStartMs": 300000, "segs": [{"utf8": "Bye."}]}
            ]}
            """;

    /** Five chunks, one more than are translated at the same time. Each names a streamer. */
    private static final String FIVE_CHUNKS = """
            {"title": "a title", "channel": "a channel with terms", "events": [
              {"tStartMs": 1000, "segs": [{"utf8": "Mooney here."}]},
              {"tStartMs": 300000, "segs": [{"utf8": "Mooney there."}]},
              {"tStartMs": 600000, "segs": [{"utf8": "Mooney again."}]},
              {"tStartMs": 900000, "segs": [{"utf8": "Mooney still."}]},
              {"tStartMs": 1200000, "segs": [{"utf8": "Mooney last."}]}
            ]}
            """;

    private static final Answer<Reply> PREFIX_EACH_SENTENCE = call -> {
        Chunk chunk = call.getArgument(1);
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < chunk.sentences().size(); i++) {
            text.append(i + 1).append("|ko:").append(chunk.sentences().get(i).text()).append('\n');
        }
        return new Reply(text.toString(), 10, 20);
    };

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @MockitoBean
    Translator translator;

    @MockitoBean
    TermExtractor extractor;

    @LocalServerPort
    int port;

    @Autowired
    JsonMapper json;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    VideoRepository videos;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void extractorFindsNothing() {
        when(extractor.extract(any(), any())).thenReturn(new Reply("", 1, 1));
    }

    @Test
    void translatesOnceThenServesTheStoredResult() throws Exception {
        when(translator.translate(any(), any())).thenAnswer(PREFIX_EACH_SENTENCE);

        HttpResponse<String> first = post("aaaaaaaaaaa", CAPTIONS);
        assertThat(first.statusCode()).isEqualTo(202);
        awaitStatus("aaaaaaaaaaa", "DONE");

        JsonNode subtitles = get("aaaaaaaaaaa").get("subtitles");
        assertThat(subtitles).hasSize(2);
        assertThat(subtitles.get(0).get("startMs").asLong()).isEqualTo(1000);
        assertThat(subtitles.get(0).get("endMs").asLong()).isEqualTo(2100);
        assertThat(subtitles.get(0).get("text").asString()).isEqualTo("ko:It was late.");
        assertThat(subtitles.get(1).get("startMs").asLong()).isEqualTo(2100);
        assertThat(subtitles.get(1).get("text").asString()).isEqualTo("ko:So I left.");

        HttpResponse<String> second = post("aaaaaaaaaaa", CAPTIONS);
        assertThat(second.statusCode()).isEqualTo(200);
        assertThat(json.readTree(second.body()).get("status").asString()).isEqualTo("DONE");
        verify(translator, times(1)).translate(any(), any());

        Long usageRows = jdbc.sql("select count(*) from translation_chunk where youtube_id = 'aaaaaaaaaaa'")
                .query(Long.class)
                .single();
        assertThat(usageRows).isEqualTo(1);
    }

    @Test
    void failedVideoCanBeRequestedAgain() throws Exception {
        when(translator.translate(any(), any())).thenThrow(new IllegalStateException("model unavailable"));
        assertThat(post("bbbbbbbbbbb", CAPTIONS).statusCode()).isEqualTo(202);
        awaitStatus("bbbbbbbbbbb", "FAILED");
        assertThat(get("bbbbbbbbbbb").get("subtitles")).isEmpty();

        doAnswer(PREFIX_EACH_SENTENCE).when(translator).translate(any(), any());
        assertThat(post("bbbbbbbbbbb", CAPTIONS).statusCode()).isEqualTo(202);
        awaitStatus("bbbbbbbbbbb", "DONE");
        verify(extractor, times(1)).extract(any(), any());
    }

    @Test
    void extractedTermsAreUsedByTheChunksThatStartLaterAndByTheNextVideoOfTheChannel() throws Exception {
        when(extractor.extract(any(), any()))
                .thenReturn(new Reply("Mooney = Murin | this channel's streamer\n", 100, 50));
        Map<String, VideoInfo> toldWith = new ConcurrentHashMap<>();
        when(translator.translate(any(), any())).thenAnswer(call -> {
            Chunk chunk = call.getArgument(1);
            toldWith.put(chunk.sentences().getFirst().text(), call.getArgument(0));
            if (chunk.index() < 4) {
                // The first four chunks finish only after the terms are stored, so the fifth starts after that.
                await().atMost(Duration.ofSeconds(10)).until(() -> storedTerms() == 1);
            }
            return PREFIX_EACH_SENTENCE.answer(call);
        });

        assertThat(post("hhhhhhhhhhh", FIVE_CHUNKS).statusCode()).isEqualTo(202);
        awaitStatus("hhhhhhhhhhh", "DONE");
        assertThat(toldWith.get("Mooney last.").glossary())
                .filteredOn(group -> group.channel() != null)
                .singleElement()
                .satisfies(group -> assertThat(group.terms()).containsExactly(new Term(List.of("Mooney"), "Murin")));

        String nextVideo = CAPTIONS.replace("a channel", "a channel with terms");
        assertThat(post("iiiiiiiiiii", nextVideo).statusCode()).isEqualTo(202);
        awaitStatus("iiiiiiiiiii", "DONE");
        assertThat(toldWith.get("It was late.").glossary())
                .filteredOn(group -> group.channel() != null)
                .hasSize(1);
        assertThat(storedTerms()).isEqualTo(1);
        Long inputTokens = jdbc.sql("""
                        select sum(input_tokens) from term_extraction
                        where youtube_id in ('hhhhhhhhhhh', 'iiiiiiiiiii')
                        """)
                .query(Long.class)
                .single();
        assertThat(inputTokens).isEqualTo(200);
    }

    @Test
    void replyThatSkipsALineIsAskedAgainAndBothCallsAreCounted() throws Exception {
        when(translator.translate(any(), any()))
                .thenReturn(new Reply("1|ko:It was late.\n", 10, 20))
                .thenAnswer(PREFIX_EACH_SENTENCE);

        assertThat(post("eeeeeeeeeee", CAPTIONS).statusCode()).isEqualTo(202);
        awaitStatus("eeeeeeeeeee", "DONE");

        assertThat(get("eeeeeeeeeee").get("subtitles")).hasSize(2);
        Long inputTokens = jdbc.sql("select input_tokens from translation_chunk where youtube_id = 'eeeeeeeeeee'")
                .query(Long.class)
                .single();
        assertThat(inputTokens).isEqualTo(20);
    }

    @Test
    void finishedChunkIsServedWhileAnotherIsStillWithTheModel() throws Exception {
        CountDownLatch secondChunkMayFinish = new CountDownLatch(1);
        when(translator.translate(any(), any())).thenAnswer(call -> {
            if (call.<Chunk>getArgument(1).index() == 1) {
                secondChunkMayFinish.await();
            }
            return PREFIX_EACH_SENTENCE.answer(call);
        });

        assertThat(post("fffffffffff", TWO_CHUNKS).statusCode()).isEqualTo(202);
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(get("fffffffffff").get("subtitles")).hasSize(1));

        JsonNode atFirstChunk = get("fffffffffff", 0);
        assertThat(atFirstChunk.get("status").asString()).isEqualTo("PENDING");
        assertThat(atFirstChunk.get("ready").asBoolean()).isTrue();
        assertThat(get("fffffffffff", 300_000).get("ready").asBoolean()).isFalse();

        secondChunkMayFinish.countDown();
        awaitStatus("fffffffffff", "DONE");
        JsonNode subtitles = get("fffffffffff").get("subtitles");
        assertThat(subtitles.get(0).get("text").asString()).isEqualTo("ko:Hello.");
        assertThat(subtitles.get(1).get("text").asString()).isEqualTo("ko:Bye.");

        JsonNode missing = json.readTree(http.send(
                        HttpRequest.newBuilder(uri("fffffffffff", "subtitles?have=0")).build(),
                        HttpResponse.BodyHandlers.ofString())
                .body());
        assertThat(missing.get("subtitles")).hasSize(1);
        assertThat(missing.get("subtitles").get(0).get("chunk").asInt()).isEqualTo(1);
        assertThat(missing.get("subtitles").get(0).get("text").asString()).isEqualTo("ko:Bye.");
    }

    @Test
    void videoThatNeedsTheModelIsRefusedOnceTheMonthlyCapIsReached() throws Exception {
        when(translator.translate(any(), any())).thenAnswer(PREFIX_EACH_SENTENCE);
        assertThat(post("jjjjjjjjjjj", CAPTIONS).statusCode()).isEqualTo(202);
        awaitStatus("jjjjjjjjjjj", "DONE");
        jdbc.sql("insert into model_call (model, input_tokens, output_tokens, usd) values ('a model', 1, 1, 10)")
                .update();
        try {
            assertThat(post("kkkkkkkkkkk", CAPTIONS).statusCode()).isEqualTo(429);
            assertThat(post("jjjjjjjjjjj", CAPTIONS).statusCode()).isEqualTo(200);
            assertThat(get("jjjjjjjjjjj").get("subtitles")).hasSize(2);
        } finally {
            jdbc.sql("delete from model_call where model = 'a model'").update();
        }
        assertThat(post("kkkkkkkkkkk", CAPTIONS).statusCode()).isEqualTo(202);
        awaitStatus("kkkkkkkkkkk", "DONE");
    }

    @Test
    void videoCutOffByARestartTranslatesOnlyTheMissingChunk() throws Exception {
        when(translator.translate(any(), any())).thenAnswer(PREFIX_EACH_SENTENCE);
        jdbc.sql("insert into video (youtube_id, channel, status) values ('ggggggggggg', 'a channel', 'PENDING')")
                .update();
        videos.saveChunk(
                "ggggggggggg",
                List.of(new Subtitle(0, 0, 1000, 4000, "saved before the restart")),
                new VideoRepository.ChunkUsage(0, 10, 20, 30));

        videos.failInterrupted();
        assertThat(get("ggggggggggg").get("status").asString()).isEqualTo("FAILED");

        assertThat(post("ggggggggggg", TWO_CHUNKS).statusCode()).isEqualTo(202);
        awaitStatus("ggggggggggg", "DONE");

        verify(translator, times(1)).translate(any(), any());
        JsonNode subtitles = get("ggggggggggg").get("subtitles");
        assertThat(subtitles).hasSize(2);
        assertThat(subtitles.get(0).get("text").asString()).isEqualTo("saved before the restart");
        assertThat(subtitles.get(1).get("text").asString()).isEqualTo("ko:Bye.");
    }

    @Test
    void unknownVideoIsNotFoundAndBadRequestIsRejected() throws Exception {
        HttpResponse<String> unknown = http.send(
                HttpRequest.newBuilder(uri("ccccccccccc", "subtitles")).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(unknown.statusCode()).isEqualTo(404);

        assertThat(post("not-a-video-id", CAPTIONS).statusCode()).isEqualTo(400);
        assertThat(post("ddddddddddd", "{\"channel\": \"a channel\", \"events\": []}").statusCode()).isEqualTo(400);
    }

    private long storedTerms() {
        return jdbc.sql("select count(*) from channel_term where channel = 'a channel with terms'")
                .query(Long.class)
                .single();
    }

    private void awaitStatus(String youtubeId, String status) {
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(get(youtubeId).get("status").asString()).isEqualTo(status));
    }

    private HttpResponse<String> post(String youtubeId, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(youtubeId, "translation"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode get(String youtubeId) throws Exception {
        return get(youtubeId, 0);
    }

    private JsonNode get(String youtubeId, long positionMs) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(youtubeId, "subtitles?positionMs=" + positionMs)).build();
        return json.readTree(http.send(request, HttpResponse.BodyHandlers.ofString()).body());
    }

    private URI uri(String youtubeId, String resource) {
        return URI.create("http://localhost:" + port + "/api/videos/" + youtubeId + "/" + resource);
    }
}
