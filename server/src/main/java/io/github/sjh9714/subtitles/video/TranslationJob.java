package io.github.sjh9714.subtitles.video;

import io.github.sjh9714.subtitles.caption.CaptionEvent;
import io.github.sjh9714.subtitles.caption.CaptionParser;
import io.github.sjh9714.subtitles.caption.Sentence;
import io.github.sjh9714.subtitles.caption.SentenceRegrouper;
import io.github.sjh9714.subtitles.translation.Chunk;
import io.github.sjh9714.subtitles.translation.Chunker;
import io.github.sjh9714.subtitles.translation.Translator;
import io.github.sjh9714.subtitles.translation.Translator.Translation;
import io.github.sjh9714.subtitles.translation.Translator.VideoInfo;
import io.github.sjh9714.subtitles.video.VideoRepository.ChunkUsage;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Turns one video's captions into stored subtitles, off the request thread.
 *
 * <p>Chunks run one after another and nothing is saved until all of them succeed. A failed chunk fails
 * the video, and a job cut off by a restart stays PENDING. Both are known gaps of this first version.
 */
@Component
class TranslationJob {

    private static final Logger log = LoggerFactory.getLogger(TranslationJob.class);

    private final Translator translator;
    private final VideoRepository videos;

    TranslationJob(Translator translator, VideoRepository videos) {
        this.translator = translator;
        this.videos = videos;
    }

    @Async
    public void run(String youtubeId, VideoInfo video, List<CaptionEvent> events) {
        try {
            List<Sentence> sentences = SentenceRegrouper.regroup(CaptionParser.parse(events));
            List<Subtitle> subtitles = new ArrayList<>();
            List<ChunkUsage> usages = new ArrayList<>();
            for (Chunk chunk : Chunker.split(sentences)) {
                long startedAt = System.currentTimeMillis();
                Translation translation = translator.translate(video, chunk);
                usages.add(new ChunkUsage(
                        chunk.index(),
                        translation.inputTokens(),
                        translation.outputTokens(),
                        System.currentTimeMillis() - startedAt));
                for (int i = 0; i < chunk.sentences().size(); i++) {
                    String korean = translation.lines().get(i);
                    if (!korean.isBlank()) {
                        Sentence sentence = chunk.sentences().get(i);
                        subtitles.add(new Subtitle(subtitles.size(), sentence.startMs(), sentence.endMs(), korean));
                    }
                }
            }
            videos.saveResult(youtubeId, subtitles, usages);
        } catch (RuntimeException e) {
            log.error("translation failed for video {}", youtubeId, e);
            videos.markFailed(youtubeId, String.valueOf(e.getMessage()));
        }
    }
}
