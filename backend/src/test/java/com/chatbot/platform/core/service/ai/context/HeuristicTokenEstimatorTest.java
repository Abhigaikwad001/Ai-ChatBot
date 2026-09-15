package com.chatbot.platform.core.service.ai.context;

import com.chatbot.platform.core.service.ai.AiMessage;
import com.chatbot.platform.core.service.ai.AiRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HeuristicTokenEstimatorTest {

    private HeuristicTokenEstimator estimator;

    @BeforeEach
    void setUp() {
        estimator = new HeuristicTokenEstimator();
    }

    @Test
    @DisplayName("1. Returns 0 tokens for null, empty, or whitespace-only text")
    void testEstimateTokens_emptyAndBlank() {
        assertThat(estimator.estimateTokens(null)).isZero();
        assertThat(estimator.estimateTokens("")).isZero();
        assertThat(estimator.estimateTokens("   ")).isZero();
        assertThat(estimator.estimateTokens("\t\n")).isZero();
    }

    @Test
    @DisplayName("2. Estimates token count using ~4 characters per token heuristic")
    void testEstimateTokens_standardText() {
        // 4 chars -> 1 token
        assertThat(estimator.estimateTokens("Java")).isEqualTo(1);

        // 8 chars -> 2 tokens
        assertThat(estimator.estimateTokens("Spring21")).isEqualTo(2);

        // 16 chars -> 4 tokens
        assertThat(estimator.estimateTokens("1234567890123456")).isEqualTo(4);

        // 17 chars -> ceil(17 / 4.0) = 5 tokens
        assertThat(estimator.estimateTokens("12345678901234567")).isEqualTo(5);
    }

    @Test
    @DisplayName("3. Non-empty text yields at least 1 token")
    void testEstimateTokens_shortTextYieldsAtLeastOne() {
        assertThat(estimator.estimateTokens("a")).isEqualTo(1);
        assertThat(estimator.estimateTokens("Hi")).isEqualTo(1);
    }

    @Test
    @DisplayName("4. Includes message framing overhead (4 tokens) for AiMessage")
    void testEstimateMessageTokens_includesFramingOverhead() {
        AiMessage msg = new AiMessage(AiRole.USER, "Hello world!"); // 12 chars -> 3 tokens + 4 overhead = 7 tokens

        int tokens = estimator.estimateMessageTokens(msg);
        assertThat(tokens).isEqualTo(4 + 3);
    }

    @Test
    @DisplayName("5. Null AiMessage returns 0 tokens")
    void testEstimateMessageTokens_nullMessage() {
        assertThat(estimator.estimateMessageTokens(null)).isZero();
    }

    @Test
    @DisplayName("6. Estimation is fully deterministic across repeated invocations")
    void testEstimateTokens_deterministic() {
        String sample = "The patient presented with acute abdominal pain and nausea.";
        int firstRun = estimator.estimateTokens(sample);
        int secondRun = estimator.estimateTokens(sample);
        int thirdRun = estimator.estimateTokens(sample);

        assertThat(firstRun).isEqualTo(secondRun);
        assertThat(secondRun).isEqualTo(thirdRun);
    }
}
