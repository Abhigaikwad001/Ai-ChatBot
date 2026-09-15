package com.chatbot.platform.core.service.ai;

import com.chatbot.platform.core.domain.entity.AiModelConfig;
import com.chatbot.platform.core.domain.entity.Conversation;
import com.chatbot.platform.core.domain.entity.Message;
import com.chatbot.platform.core.domain.enums.MessageRole;
import com.chatbot.platform.core.service.ai.context.ContextBuildResult;
import com.chatbot.platform.core.service.ai.context.HeuristicTokenEstimator;
import com.chatbot.platform.core.service.ai.context.TokenEstimator;
import com.chatbot.platform.infrastructure.config.ai.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Builds provider-neutral AI prompt contexts and requests from persisted conversation state.
 *
 * <p>Key Guarantees & Strategies:
 * <ul>
 *   <li><b>System Prompt Isolation:</b> System instructions are strictly derived from verified
 *       conversation config or defaults, and are never overwritten or spoofed by user text.</li>
 *   <li><b>System Prompt Invariant:</b> The system prompt is always preserved and never truncated.</li>
 *   <li><b>Current Turn Invariant:</b> The latest user message being dispatched is always preserved in full.</li>
 *   <li><b>Turn-Pair Awareness:</b> History is evaluated from newest to oldest in complete
 *       {@code (USER, ASSISTANT)} conversational turns to prevent orphaned assistant responses.</li>
 *   <li><b>Context Budgeting:</b> Enforces configurable message count and token limits, reserving
 *       sufficient headroom for the model completion response.</li>
 * </ul>
 */
@Component
public class ConversationContextBuilder {

    private static final Logger log = LoggerFactory.getLogger(ConversationContextBuilder.class);

    public static final String DEFAULT_SYSTEM_PROMPT =
        "You are a helpful, accurate, and professional clinical and administrative AI assistant.";

    private final TokenEstimator tokenEstimator;
    private final AiProperties aiProperties;

    public ConversationContextBuilder() {
        this(new HeuristicTokenEstimator(), new AiProperties());
    }

    public ConversationContextBuilder(TokenEstimator tokenEstimator, AiProperties aiProperties) {
        this.tokenEstimator = (tokenEstimator != null) ? tokenEstimator : new HeuristicTokenEstimator();
        this.aiProperties = (aiProperties != null) ? aiProperties : new AiProperties();
    }

    /**
     * Backward-compatible context builder returning just the provider-neutral {@link AiRequest}.
     */
    public AiRequest buildContext(Conversation conversation, List<Message> history) {
        return buildContextResult(conversation, history).aiRequest();
    }

    /**
     * Constructs a normalized, budgeted {@link ContextBuildResult} containing the finalized {@link AiRequest}
     * and observability metadata.
     *
     * @param conversation the parent conversation providing system prompt and model parameters
     * @param history the chronologically ordered messages up to and including the current user turn
     * @return {@link ContextBuildResult} with structured metrics and sanitized request
     */
    public ContextBuildResult buildContextResult(Conversation conversation, List<Message> history) {
        AiModelConfig modelConfig = (conversation.getAiModelConfig() != null)
            ? conversation.getAiModelConfig()
            : AiModelConfig.defaultConfig();

        // 1. Resolve and isolate system prompt
        String systemPrompt = (conversation.getSystemPrompt() != null && !conversation.getSystemPrompt().trim().isBlank())
            ? conversation.getSystemPrompt().trim()
            : DEFAULT_SYSTEM_PROMPT;

        int systemPromptTokens = tokenEstimator.estimateTokens(systemPrompt) + HeuristicTokenEstimator.MESSAGE_FRAMING_OVERHEAD;

        // 2. Filter valid messages
        List<Message> validMessages = new ArrayList<>();
        if (history != null) {
            for (Message msg : history) {
                if (msg != null && msg.getContent() != null && !msg.getContent().trim().isBlank()) {
                    validMessages.add(msg);
                }
            }
        }

        int totalValidMessages = validMessages.size();

        // If history is completely empty, return request with only system prompt
        if (validMessages.isEmpty()) {
            AiRequest request = buildAiRequest(modelConfig, systemPrompt, Collections.emptyList());
            return new ContextBuildResult(request, 0, 0, 0, systemPromptTokens, false);
        }

        // 3. Current user message is ALWAYS the last valid message in history and MUST survive
        Message currentMessageEntity = validMessages.get(validMessages.size() - 1);
        AiMessage currentAiMessage = new AiMessage(mapRole(currentMessageEntity.getRole()), currentMessageEntity.getContent());
        int currentMessageTokens = tokenEstimator.estimateMessageTokens(currentAiMessage);

        // 4. Calculate context budgets
        AiProperties.ContextProperties contextProps = aiProperties.getContext();
        int maxMessagesLimit = (contextProps != null && contextProps.getMaxMessages() != null)
            ? contextProps.getMaxMessages()
            : 20;

        int maxTokensLimit = (contextProps != null && contextProps.getMaxTokens() != null)
            ? contextProps.getMaxTokens()
            : 4096;

        int reservedOutputTokens = (contextProps != null && contextProps.getReservedOutputTokens() != null)
            ? contextProps.getReservedOutputTokens()
            : 1024;

        // Input token budget available after reserving output space
        int inputTokenBudget = Math.max(0, maxTokensLimit - reservedOutputTokens);

        // Remaining budgets for historical turns (after deducting system prompt and current message)
        int remainingTokenBudget = inputTokenBudget - systemPromptTokens - currentMessageTokens;
        int remainingMessageBudget = Math.max(0, maxMessagesLimit - 1); // 1 slot reserved for current message

        // 5. Turn-pair aware backward traversal of prior history
        List<Message> priorHistory = validMessages.subList(0, validMessages.size() - 1);
        List<AiMessage> selectedPriorMessages = new ArrayList<>();

        int cursor = priorHistory.size() - 1;
        boolean truncationOccurred = false;

        while (cursor >= 0) {
            Message msg = priorHistory.get(cursor);

            if (msg.getRole() == MessageRole.ASSISTANT) {
                // Check if preceded by a matching USER message to form a complete turn
                if (cursor - 1 >= 0 && priorHistory.get(cursor - 1).getRole() == MessageRole.USER) {
                    Message userMsg = priorHistory.get(cursor - 1);
                    AiMessage userAi = new AiMessage(AiRole.USER, userMsg.getContent());
                    AiMessage assistantAi = new AiMessage(AiRole.ASSISTANT, msg.getContent());

                    int turnTokens = tokenEstimator.estimateMessageTokens(userAi) + tokenEstimator.estimateMessageTokens(assistantAi);

                    if (remainingMessageBudget >= 2 && remainingTokenBudget >= turnTokens) {
                        // Include both messages of this turn
                        selectedPriorMessages.add(assistantAi);
                        selectedPriorMessages.add(userAi);
                        remainingMessageBudget -= 2;
                        remainingTokenBudget -= turnTokens;
                        cursor -= 2;
                    } else {
                        // Turn exceeds budget. Stop here to prevent orphaned assistant messages.
                        truncationOccurred = true;
                        break;
                    }
                } else {
                    // Standalone assistant message without preceding user message
                    AiMessage assistantAi = new AiMessage(AiRole.ASSISTANT, msg.getContent());
                    int msgTokens = tokenEstimator.estimateMessageTokens(assistantAi);

                    if (remainingMessageBudget >= 1 && remainingTokenBudget >= msgTokens) {
                        selectedPriorMessages.add(assistantAi);
                        remainingMessageBudget -= 1;
                        remainingTokenBudget -= msgTokens;
                        cursor -= 1;
                    } else {
                        truncationOccurred = true;
                        break;
                    }
                }
            } else {
                // Standalone USER message (or SYSTEM/TOOL)
                AiMessage aiMsg = new AiMessage(mapRole(msg.getRole()), msg.getContent());
                int msgTokens = tokenEstimator.estimateMessageTokens(aiMsg);

                if (remainingMessageBudget >= 1 && remainingTokenBudget >= msgTokens) {
                    selectedPriorMessages.add(aiMsg);
                    remainingMessageBudget -= 1;
                    remainingTokenBudget -= msgTokens;
                    cursor -= 1;
                } else {
                    truncationOccurred = true;
                    break;
                }
            }
        }

        if (cursor >= 0) {
            truncationOccurred = true;
        }

        // 6. Reverse selected prior messages to restore chronological order (oldest -> newest)
        Collections.reverse(selectedPriorMessages);

        // 7. Assemble final list: prior history in order + current user message
        List<AiMessage> finalizedMessages = new ArrayList<>(selectedPriorMessages);
        finalizedMessages.add(currentAiMessage);

        // 8. Calculate total estimated tokens for observability
        int totalEstimatedTokens = systemPromptTokens;
        for (AiMessage m : finalizedMessages) {
            totalEstimatedTokens += tokenEstimator.estimateMessageTokens(m);
        }

        int includedCount = finalizedMessages.size();
        int omittedCount = totalValidMessages - includedCount;

        if (truncationOccurred || omittedCount > 0) {
            log.debug("Context window truncation applied: included {}/{} messages (omitted: {}), estimated {} tokens",
                includedCount, totalValidMessages, omittedCount, totalEstimatedTokens);
        }

        AiRequest request = buildAiRequest(modelConfig, systemPrompt, finalizedMessages);

        return new ContextBuildResult(
            request,
            totalValidMessages,
            includedCount,
            omittedCount,
            totalEstimatedTokens,
            truncationOccurred || omittedCount > 0
        );
    }

    private AiRequest buildAiRequest(AiModelConfig modelConfig, String systemPrompt, List<AiMessage> messages) {
        return AiRequest.builder()
            .model(modelConfig.getModelName())
            .systemPrompt(systemPrompt)
            .messages(messages)
            .temperature(modelConfig.getTemperature())
            .maxTokens(modelConfig.getMaxTokens())
            .topP(modelConfig.getTopP())
            .build();
    }

    private AiRole mapRole(MessageRole messageRole) {
        if (messageRole == null) {
            return AiRole.USER;
        }
        return switch (messageRole) {
            case USER -> AiRole.USER;
            case ASSISTANT -> AiRole.ASSISTANT;
            case SYSTEM -> AiRole.SYSTEM;
            case TOOL -> AiRole.SYSTEM;
        };
    }
}
