package io.github.sjh9714.subtitles.translation;

import io.github.sjh9714.subtitles.caption.Sentence;
import io.github.sjh9714.subtitles.translation.Translator.Reply;
import io.github.sjh9714.subtitles.translation.Translator.VideoInfo;
import java.util.List;

/**
 * Asks the model which names and terms in a video's captions need a fixed written form.
 * The caller reads the reply with {@link TermsReply}.
 */
public interface TermExtractor {

    /**
     * @param video its glossary holds the terms that are already known, which the model leaves out
     * @param sample a part of the video's captions
     */
    Reply extract(VideoInfo video, List<Sentence> sample);
}
