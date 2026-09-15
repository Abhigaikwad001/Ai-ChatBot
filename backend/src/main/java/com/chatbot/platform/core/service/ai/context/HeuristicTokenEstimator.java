package com.chatbot.platform.core.service.ai.context;

import com.chatbot.platform.core.service.ai.AiMessage;
import org.springframework.stereotype.Component;

/**
 * Deterministic, provider-neutral heuristic token estimator.
 *
 * <p>Uses a documented character-ratio heuristic:
 * <ul>
 *   <li>English and alphanumeric tokens average roughly 3.5 to 4 characters per token
 *       (calculated as {@code ceil(characterCount / 4.0)}).</li>
 *   <li>Includes a constant per-message framing overhead (4 tokens) to account for chat template
 *       formatting (e.g. role headers, delimiters, and end-of-turn markers).</li>
 * </ul>
 *
 * <p><b>Note:</b> This is an approximation for context window budgeting and does not replace
 * exact provider-reported token usage returned post-generation in {@code AiResponse}.
 */
@Component
public class HeuristicTokenEstimator implements TokenEstimator {

    /**
     * Average number of characters per token in standard LLM BPE tokenizers.
     */
    public static final double CHARS_PER_TOKEN = 4.0;

    /**
     * Per-message formatting and delimiter overhead (e.g. {@code <|im_start|>role\ncontent<|im_end|>}).
     */
    public static final int MESSAGE_FRAMING_OVERHEAD = 4;

    @Override
    public int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        int estimated = (int) Math.ceil(text.length() / CHARS_PER_TOKEN);
        return Math.max(1, estimated);
    }

    @Override
    public int estimateMessageTokens(AiMessage message) {
        if (message == null) {
            return 0;
        }
        return MESSAGE_FRAMING_OVERHEAD + estimateTokens(message.content());
    }
}
