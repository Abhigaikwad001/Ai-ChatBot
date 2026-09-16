package com.chatbot.platform.core.service;

import com.chatbot.platform.api.dto.common.PageResponse;
import com.chatbot.platform.api.dto.message.MessageResponse;
import com.chatbot.platform.api.dto.message.SendMessageRequest;
import com.chatbot.platform.api.mapper.MessageMapper;
import com.chatbot.platform.core.domain.entity.AiAuditLog;
import com.chatbot.platform.core.domain.entity.Conversation;
import com.chatbot.platform.core.domain.entity.Message;
import com.chatbot.platform.core.domain.enums.ConversationStatus;
import com.chatbot.platform.core.domain.enums.MessageRole;
import com.chatbot.platform.core.domain.enums.MessageStatus;
import com.chatbot.platform.core.domain.enums.ProviderType;
import com.chatbot.platform.core.repository.AiAuditLogRepository;
import com.chatbot.platform.core.repository.ConversationRepository;
import com.chatbot.platform.core.repository.MessageRepository;
import com.chatbot.platform.core.service.ai.AiProvider;
import com.chatbot.platform.core.service.ai.AiProviderRegistry;
import com.chatbot.platform.core.service.ai.AiRequest;
import com.chatbot.platform.core.service.ai.AiResponse;
import com.chatbot.platform.core.service.ai.ConversationContextBuilder;
import com.chatbot.platform.api.dto.stream.ContentChunkEvent;
import com.chatbot.platform.api.dto.stream.HeartbeatEvent;
import com.chatbot.platform.api.dto.stream.MessageCompleteEvent;
import com.chatbot.platform.api.dto.stream.MessageStartEvent;
import com.chatbot.platform.api.dto.stream.StreamErrorEvent;
import com.chatbot.platform.api.dto.stream.StreamEventType;
import com.chatbot.platform.core.service.ai.context.ContextBuildResult;
import com.chatbot.platform.core.service.ai.context.HeuristicTokenEstimator;
import com.chatbot.platform.core.service.ai.context.TokenEstimator;
import com.chatbot.platform.core.service.ai.stream.AiStreamChunk;
import com.chatbot.platform.core.service.ai.stream.AiStreamHandle;
import com.chatbot.platform.core.service.ai.stream.AiStreamListener;
import com.chatbot.platform.infrastructure.config.ai.AiProperties;
import com.chatbot.platform.infrastructure.exception.ConversationDeletedException;
import com.chatbot.platform.infrastructure.exception.ResourceNotFoundException;
import com.chatbot.platform.infrastructure.exception.ai.AiProviderAuthenticationException;
import com.chatbot.platform.infrastructure.exception.ai.AiProviderException;
import com.chatbot.platform.infrastructure.exception.ai.AiProviderRateLimitException;
import com.chatbot.platform.infrastructure.exception.ai.AiProviderTimeoutException;
import com.chatbot.platform.infrastructure.exception.ai.AiProviderUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Core Chat Orchestration Service managing message persistence, deterministic sequencing,
 * conversation activity tracking, and decoupled AI Provider dispatch.
 *
 * <p>Employs a <b>Split-Transaction Lifecycle</b>:
 * <ol>
 *   <li><b>Transaction 1 (User Message):</b> Persists the user message and updates conversation activity,
 *       releasing database connections and pessimistic row locks immediately.</li>
 *   <li><b>Non-Transactional Step (AI Generation):</b> Builds the dialogue context and invokes the external
 *       {@link AiProvider} completely outside database transactions, preventing HikariCP pool starvation.</li>
 *   <li><b>Transaction 2 (Assistant Message):</b> On successful generation, persists the assistant message
 *       and writes an {@link AiAuditLog} entry in an isolated transaction.</li>
 * </ol>
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 100;

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final MessageMapper messageMapper;
    private final AiProviderRegistry aiProviderRegistry;
    private final ConversationContextBuilder contextBuilder;
    private final AiAuditLogRepository aiAuditLogRepository;
    private final AiProperties aiProperties;
    private final TransactionTemplate transactionTemplate;
    private final TokenEstimator tokenEstimator;

    @org.springframework.beans.factory.annotation.Autowired
    public ChatService(
        ConversationRepository conversationRepository,
        MessageRepository messageRepository,
        MessageMapper messageMapper,
        AiProviderRegistry aiProviderRegistry,
        ConversationContextBuilder contextBuilder,
        AiAuditLogRepository aiAuditLogRepository,
        AiProperties aiProperties,
        PlatformTransactionManager transactionManager,
        @org.springframework.beans.factory.annotation.Autowired(required = false) TokenEstimator tokenEstimator
    ) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.messageMapper = messageMapper;
        this.aiProviderRegistry = aiProviderRegistry;
        this.contextBuilder = contextBuilder;
        this.aiAuditLogRepository = aiAuditLogRepository;
        this.aiProperties = aiProperties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.tokenEstimator = (tokenEstimator != null) ? tokenEstimator : new HeuristicTokenEstimator();
    }

    public ChatService(
        ConversationRepository conversationRepository,
        MessageRepository messageRepository,
        MessageMapper messageMapper,
        AiProviderRegistry aiProviderRegistry,
        ConversationContextBuilder contextBuilder,
        AiAuditLogRepository aiAuditLogRepository,
        AiProperties aiProperties,
        PlatformTransactionManager transactionManager
    ) {
        this(conversationRepository, messageRepository, messageMapper, aiProviderRegistry, contextBuilder,
             aiAuditLogRepository, aiProperties, transactionManager, new HeuristicTokenEstimator());
    }

    /**
    /**
     * Executes chat turn in standalone no-auth mode.
     */
    public MessageResponse sendMessage(UUID conversationId, SendMessageRequest request) {
        return sendMessage(conversationId, null, request);
    }

    /**
     * Executes the complete conversational chat turn:
     * User Message -> Context Building -> AIProvider -> Assistant Message.
     *
     * @param conversationId the conversation identifier
     * @param userId the authenticated owner's ID (or null in standalone mode)
     * @param request the send message request payload
     * @return the newly generated and persisted assistant {@link MessageResponse}
     */
    public MessageResponse sendMessage(UUID conversationId, UUID userId, SendMessageRequest request) {
        validateMessageSize(request);

        // =========================================================================
        // Step 1: Persist USER message in isolated Transaction 1
        // =========================================================================
        Message userMessage = persistUserMessage(conversationId, userId, request);

        // =========================================================================
        // Step 2: Assemble provider-neutral context history (Non-Transactional Read)
        // =========================================================================
        Conversation conversation = conversationRepository.findById(conversationId)
            .orElseThrow(() -> new ResourceNotFoundException("Conversation not found with id: " + conversationId));

        int maxContextMessages = (aiProperties.getContext() != null && aiProperties.getContext().getMaxMessages() != null)
            ? aiProperties.getContext().getMaxMessages()
            : 20;
        int contextFetchLimit = Math.max(100, maxContextMessages * 2);

        Page<Message> recentPage = messageRepository.findByConversationIdOrderBySequenceNumberDesc(
            conversationId,
            PageRequest.of(0, contextFetchLimit)
        );
        List<Message> history = new ArrayList<>(recentPage.getContent());
        Collections.reverse(history);

        ContextBuildResult contextResult = contextBuilder.buildContextResult(conversation, history);
        AiRequest aiRequest = contextResult.aiRequest();

        log.info("Built AI context for conversation [{}]: {}/{} messages included (omitted: {}), ~{} tokens (truncated={})",
            conversationId, contextResult.includedMessages(), contextResult.totalHistoryMessages(),
            contextResult.omittedMessages(), contextResult.estimatedTotalTokens(), contextResult.truncated());

        // =========================================================================
        // Step 3: Resolve AI Provider & execute external HTTP call (OUTSIDE DB TRANSACTION)
        // =========================================================================
        ProviderType providerType = resolveProviderType(conversation);
        AiProvider aiProvider = aiProviderRegistry.getProvider(providerType);

        long startTime = System.currentTimeMillis();
        AiResponse aiResponse;

        try {
            log.info("Dispatching chat generation for conversation [{}] to provider [{}] (model: [{}])",
                conversationId, providerType, aiRequest.model());

            aiResponse = aiProvider.generate(aiRequest);

        } catch (Exception ex) {
            long latencyMs = System.currentTimeMillis() - startTime;
            log.error("AI generation failed for conversation [{}]: {}", conversationId, ex.getMessage());

            // Record failure in audit log via isolated transaction
            recordAuditLog(conversationId, null, providerType.name(), aiRequest.model(), latencyMs, 0, null, ex.getMessage());

            // The user message remains safely persisted. Re-throw sanitized exception for REST layer.
            if (ex instanceof AiProviderException ape) {
                throw ape;
            }
            throw new AiProviderException(providerType.name(), "AI provider encountered an unrecoverable failure", ex);
        }

        // =========================================================================
        // Step 4: Persist ASSISTANT message & Audit Log in isolated Transaction 2
        // =========================================================================
        Message assistantMessage = persistAssistantMessage(conversationId, aiResponse, contextResult);

        return messageMapper.toResponse(assistantMessage);
    }

    /**
     * Persists user message in standalone no-auth mode.
     */
    public Message persistUserMessage(UUID conversationId, SendMessageRequest request) {
        return persistUserMessage(conversationId, null, request);
    }

    /**
     * Persists only the user message in its own isolated transaction.
     * Uses pessimistic write locking on the Conversation row to guarantee consecutive, collision-free sequencing.
     */
    public Message persistUserMessage(UUID conversationId, UUID userId, SendMessageRequest request) {
        return transactionTemplate.execute(status -> {
            Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found with id: " + conversationId));

            if (userId != null && conversation.getUser() != null && !conversation.getUser().getId().equals(userId)) {
                log.warn("Unauthorized message post attempt: user [{}] tried to access conversation [{}]", userId, conversationId);
                throw new AccessDeniedException("Access denied: You do not have permission to access this conversation");
            }

            if (conversation.getStatus() == ConversationStatus.DELETED) {
                log.warn("Message send rejected: conversation [{}] is deleted", conversationId);
                throw new ConversationDeletedException("Cannot send messages to a deleted conversation");
            }

            // Acquire exclusive row lock to serialize sequence allocation
            Conversation lockedConvo = conversationRepository.findByIdForUpdate(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found with id: " + conversationId));

            if (lockedConvo.getStatus() == ConversationStatus.DELETED) {
                throw new ConversationDeletedException("Cannot send messages to a deleted conversation");
            }

            int nextSequence = messageRepository.getNextSequenceNumber(conversationId);
            Message userMessage = new Message(
                lockedConvo,
                nextSequence,
                MessageRole.USER,
                request.content().trim(),
                MessageStatus.SENT
            );

            if (request.metadata() != null) {
                userMessage.setMetadata(new HashMap<>(request.metadata()));
            }

            Message saved = messageRepository.save(userMessage);

            lockedConvo.setUpdatedAt(Instant.now());
            conversationRepository.save(lockedConvo);

            log.info("Persisted USER message [{}] (seq: {}) for conversation [{}]",
                saved.getId(), nextSequence, conversationId);

            return saved;
        });
    }

    /**
     * Persists the assistant message and records the audit log in Transaction 2.
     */
    private Message persistAssistantMessage(UUID conversationId, AiResponse aiResponse, ContextBuildResult contextResult) {
        return transactionTemplate.execute(status -> {
            Conversation lockedConvo = conversationRepository.findByIdForUpdate(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found with id: " + conversationId));

            int nextSequence = messageRepository.getNextSequenceNumber(conversationId);

            Message assistantMessage = new Message(
                lockedConvo,
                nextSequence,
                MessageRole.ASSISTANT,
                aiResponse.content(),
                MessageStatus.SENT
            );

            assistantMessage.setPromptTokens(aiResponse.promptTokens());
            assistantMessage.setCompletionTokens(aiResponse.completionTokens());

            Map<String, Object> metadata = new HashMap<>(aiResponse.metadata());
            metadata.put("provider", aiResponse.provider());
            metadata.put("model", aiResponse.model());
            if (contextResult != null) {
                metadata.put("contextTokensEstimated", contextResult.estimatedTotalTokens());
                metadata.put("contextMessagesIncluded", contextResult.includedMessages());
                metadata.put("contextTruncated", contextResult.truncated());
            }
            if (aiResponse.finishReason() != null) {
                metadata.put("finishReason", aiResponse.finishReason());
            }
            assistantMessage.setMetadata(metadata);

            Message savedAssistant = messageRepository.save(assistantMessage);

            lockedConvo.setUpdatedAt(Instant.now());
            conversationRepository.save(lockedConvo);

            // Record successful audit log
            AiAuditLog auditLog = new AiAuditLog(
                conversationId,
                savedAssistant.getId(),
                aiResponse.provider(),
                aiResponse.model(),
                aiResponse.latencyMs(),
                aiResponse.totalTokens()
            );
            auditLog.setFinishReason(aiResponse.finishReason());
            aiAuditLogRepository.save(auditLog);

            log.info("Persisted ASSISTANT message [{}] (seq: {}) for conversation [{}] in {}ms",
                savedAssistant.getId(), nextSequence, conversationId, aiResponse.latencyMs());

            return savedAssistant;
        });
    }

    /**
     * Records an audit log entry in an isolated transaction.
     */
    private void recordAuditLog(
        UUID conversationId,
        UUID messageId,
        String provider,
        String model,
        Long latencyMs,
        Integer totalTokens,
        String finishReason,
        String errorMessage
    ) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                AiAuditLog auditLog = new AiAuditLog(conversationId, messageId, provider, model, latencyMs, totalTokens);
                auditLog.setFinishReason(finishReason);
                auditLog.setErrorMessage(errorMessage);
                aiAuditLogRepository.save(auditLog);
            });
        } catch (Exception ex) {
            log.warn("Failed to persist AI audit log for conversation [{}]: {}", conversationId, ex.getMessage());
        }
    }

    private ProviderType resolveProviderType(Conversation conversation) {
        if (conversation.getAiModelConfig() != null && conversation.getAiModelConfig().getProvider() != null) {
            return conversation.getAiModelConfig().getProvider();
        }
        return aiProperties.getDefaultProvider();
    }

    private void validateMessageSize(SendMessageRequest request) {
        if (request == null || request.content() == null) {
            return;
        }
        if (aiProperties.getContext() != null && aiProperties.getContext().getMaxTokens() != null) {
            int maxTokens = aiProperties.getContext().getMaxTokens();
            int reserved = (aiProperties.getContext().getReservedOutputTokens() != null)
                ? aiProperties.getContext().getReservedOutputTokens()
                : 512;
            int maxInputBudget = maxTokens - reserved;
            int estimatedTokens = tokenEstimator.estimateTokens(request.content());
            if (estimatedTokens > maxInputBudget) {
                log.warn("User message rejected: estimated tokens ({}) exceed maximum context input budget ({})",
                    estimatedTokens, maxInputBudget);
                throw new IllegalArgumentException("Your message is too large to process. Please shorten it and try again.");
            }
        }
    }

    /**
     * Executes the complete conversational chat turn using Server-Sent Events (SSE) streaming:
     * 1. Validates message size against context budget
     * 2. Persists USER message in isolated Transaction 1
     * 3. Assembles provider-neutral context history (Non-Transactional Read)
     * 4. Initializes SSE emitter and heartbeat
     * 5. Dispatches streaming to AI Provider on an asynchronous Virtual Thread
     * 6. Chunks are streamed in real time via SSE
     * 7. On completion, persists ASSISTANT message and AiAuditLog in isolated Transaction 2
     *
     * @param conversationId the conversation identifier
     * @param userId the authenticated owner's ID
     * @param request the send message request payload
     * @return the active {@link SseEmitter} streaming events to the client
     */
    /**
     * Executes streaming chat turn in standalone no-auth mode.
     */
    public SseEmitter streamMessage(UUID conversationId, SendMessageRequest request) {
        return streamMessage(conversationId, null, request);
    }

    public SseEmitter streamMessage(UUID conversationId, UUID userId, SendMessageRequest request) {
        validateMessageSize(request);

        // Step 1: Persist USER message in isolated Transaction 1 (releases DB lock immediately)
        Message userMessage = persistUserMessage(conversationId, userId, request);

        // Step 2: Assemble provider-neutral context history (Non-Transactional Read)
        Conversation conversation = conversationRepository.findById(conversationId)
            .orElseThrow(() -> new ResourceNotFoundException("Conversation not found with id: " + conversationId));

        int maxContextMessages = (aiProperties.getContext() != null && aiProperties.getContext().getMaxMessages() != null)
            ? aiProperties.getContext().getMaxMessages()
            : 20;
        int contextFetchLimit = Math.max(100, maxContextMessages * 2);

        Page<Message> recentPage = messageRepository.findByConversationIdOrderBySequenceNumberDesc(
            conversationId,
            PageRequest.of(0, contextFetchLimit)
        );
        List<Message> history = new ArrayList<>(recentPage.getContent());
        Collections.reverse(history);

        ContextBuildResult contextResult = contextBuilder.buildContextResult(conversation, history);
        AiRequest aiRequest = contextResult.aiRequest();

        ProviderType providerType = resolveProviderType(conversation);
        AiProvider aiProvider = aiProviderRegistry.getProvider(providerType);

        long timeoutMs = (aiProperties.getStreaming() != null && aiProperties.getStreaming().getTimeoutMs() != null)
            ? aiProperties.getStreaming().getTimeoutMs()
            : 120_000L;
        SseEmitter emitter = new SseEmitter(timeoutMs);

        // Step 3: Setup Heartbeat keep-alive if enabled
        ScheduledExecutorService heartbeatScheduler = null;
        if (aiProperties.getStreaming() != null && Boolean.TRUE.equals(aiProperties.getStreaming().getHeartbeatEnabled())) {
            long heartbeatInterval = (aiProperties.getStreaming().getHeartbeatIntervalMs() != null)
                ? aiProperties.getStreaming().getHeartbeatIntervalMs()
                : 15_000L;
            heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "sse-heartbeat-" + conversationId);
                t.setDaemon(true);
                return t;
            });
            ScheduledExecutorService finalScheduler = heartbeatScheduler;
            heartbeatScheduler.scheduleAtFixedRate(() -> {
                try {
                    emitter.send(SseEmitter.event()
                        .name(StreamEventType.HEARTBEAT)
                        .data(new HeartbeatEvent()));
                } catch (Exception e) {
                    log.debug("Heartbeat send failed for conversation [{}]: {}", conversationId, e.getMessage());
                    finalScheduler.shutdown();
                }
            }, heartbeatInterval, heartbeatInterval, TimeUnit.MILLISECONDS);
        }

        ScheduledExecutorService finalHeartbeatScheduler = heartbeatScheduler;
        AtomicBoolean isCancelled = new AtomicBoolean(false);
        AtomicReference<AiStreamHandle> streamHandleRef = new AtomicReference<>(null);

        emitter.onCompletion(() -> {
            stopHeartbeat(finalHeartbeatScheduler);
        });

        emitter.onTimeout(() -> {
            log.warn("SSE connection timed out for conversation [{}]", conversationId);
            isCancelled.set(true);
            cancelStreamHandle(streamHandleRef.get());
            stopHeartbeat(finalHeartbeatScheduler);
        });

        emitter.onError(throwable -> {
            log.debug("SSE connection error for conversation [{}]: {}", conversationId, throwable.getMessage());
            isCancelled.set(true);
            cancelStreamHandle(streamHandleRef.get());
            stopHeartbeat(finalHeartbeatScheduler);
        });

        // Launch asynchronous streaming dialogue on a Virtual Thread (JDK 21)
        Thread.ofVirtual().name("ai-stream-" + conversationId).start(() -> {
            executeStreamingDialogue(
                conversationId,
                providerType,
                aiProvider,
                aiRequest,
                contextResult,
                emitter,
                isCancelled,
                streamHandleRef,
                finalHeartbeatScheduler
            );
        });

        return emitter;
    }

    private void executeStreamingDialogue(
        UUID conversationId,
        ProviderType providerType,
        AiProvider aiProvider,
        AiRequest aiRequest,
        ContextBuildResult contextResult,
        SseEmitter emitter,
        AtomicBoolean isCancelled,
        AtomicReference<AiStreamHandle> streamHandleRef,
        ScheduledExecutorService heartbeatScheduler
    ) {
        long startTime = System.currentTimeMillis();
        AtomicReference<Long> firstChunkTime = new AtomicReference<>(null);
        StringBuilder accumulatedContent = new StringBuilder();
        AtomicInteger chunkIndex = new AtomicInteger(0);

        try {
            // Emit initial message_start event
            emitter.send(SseEmitter.event()
                .name(StreamEventType.MESSAGE_START)
                .data(new MessageStartEvent(conversationId, MessageRole.ASSISTANT.name().toLowerCase())));

            AiStreamHandle handle = aiProvider.stream(aiRequest, new AiStreamListener() {
                @Override
                public void onHandle(AiStreamHandle h) {
                    streamHandleRef.set(h);
                }

                @Override
                public void onStart() {
                    log.debug("Started AI stream for conversation [{}]", conversationId);
                }

                @Override
                public void onChunk(AiStreamChunk chunk) {
                    if (isCancelled.get()) {
                        cancelStreamHandle(streamHandleRef.get());
                        return;
                    }

                    if (firstChunkTime.get() == null) {
                        firstChunkTime.set(System.currentTimeMillis());
                    }

                    if (chunk.content() != null && !chunk.content().isEmpty()) {
                        accumulatedContent.append(chunk.content());
                        int seq = chunkIndex.incrementAndGet();
                        try {
                            emitter.send(SseEmitter.event()
                                .name(StreamEventType.CONTENT)
                                .data(new ContentChunkEvent(chunk.content(), seq)));
                        } catch (Exception ex) {
                            log.warn("Client disconnected during stream for conversation [{}]: {}",
                                conversationId, ex.getMessage());
                            isCancelled.set(true);
                            cancelStreamHandle(streamHandleRef.get());
                        }
                    }
                }

                @Override
                public void onComplete(AiResponse completeResponse) {
                    if (isCancelled.get()) {
                        log.info("Stream was cancelled; finalizing partial record for conversation [{}]", conversationId);
                        handleStreamCancellation(conversationId, providerType, aiRequest.model(),
                            startTime, accumulatedContent.toString(), heartbeatScheduler);
                        return;
                    }

                    long totalLatencyMs = System.currentTimeMillis() - startTime;
                    long timeToFirstChunkMs = (firstChunkTime.get() != null)
                        ? (firstChunkTime.get() - startTime)
                        : totalLatencyMs;

                    try {
                        // Persist complete assistant message in Transaction 2
                        Message assistantMessage = persistAssistantMessage(conversationId, completeResponse, contextResult);

                        MessageCompleteEvent completeEvent = new MessageCompleteEvent(
                            assistantMessage.getId(),
                            conversationId,
                            MessageRole.ASSISTANT.name().toLowerCase(),
                            completeResponse.provider(),
                            completeResponse.model(),
                            completeResponse.finishReason(),
                            completeResponse.promptTokens(),
                            completeResponse.completionTokens(),
                            completeResponse.totalTokens(),
                            totalLatencyMs,
                            timeToFirstChunkMs
                        );

                        emitter.send(SseEmitter.event()
                            .name(StreamEventType.MESSAGE_COMPLETE)
                            .data(completeEvent));
                        emitter.complete();

                    } catch (Exception ex) {
                        log.error("Failed to persist assistant message upon stream completion for conversation [{}]: {}",
                            conversationId, ex.getMessage(), ex);
                        handleStreamFailure(conversationId, providerType, aiRequest.model(), ex, startTime,
                            accumulatedContent.toString(), emitter, isCancelled.get(), heartbeatScheduler);
                    } finally {
                        stopHeartbeat(heartbeatScheduler);
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                    long totalLatencyMs = System.currentTimeMillis() - startTime;
                    log.error("AI streaming failed for conversation [{}]: {}", conversationId, throwable.getMessage());
                    handleStreamFailure(conversationId, providerType, aiRequest.model(), throwable, startTime,
                        accumulatedContent.toString(), emitter, isCancelled.get(), heartbeatScheduler);
                }
            });

            streamHandleRef.set(handle);

        } catch (Exception ex) {
            log.error("Failed to initiate AI stream for conversation [{}]: {}", conversationId, ex.getMessage());
            handleStreamFailure(conversationId, providerType, aiRequest.model(), ex, startTime,
                accumulatedContent.toString(), emitter, isCancelled.get(), heartbeatScheduler);
        }
    }

    private void handleStreamCancellation(
        UUID conversationId,
        ProviderType providerType,
        String model,
        long startTime,
        String accumulatedContent,
        ScheduledExecutorService heartbeatScheduler
    ) {
        stopHeartbeat(heartbeatScheduler);
        long latencyMs = System.currentTimeMillis() - startTime;

        if (accumulatedContent != null && !accumulatedContent.isBlank()) {
            persistFailedAssistantMessage(conversationId, providerType.name(), model, accumulatedContent, "CANCELLED");
        }

        recordAuditLog(conversationId, null, providerType.name(), model, latencyMs, 0, "CANCELLED", "Stream cancelled by client");
    }

    private void handleStreamFailure(
        UUID conversationId,
        ProviderType providerType,
        String model,
        Throwable throwable,
        long startTime,
        String accumulatedContent,
        SseEmitter emitter,
        boolean isCancelled,
        ScheduledExecutorService heartbeatScheduler
    ) {
        stopHeartbeat(heartbeatScheduler);
        long latencyMs = System.currentTimeMillis() - startTime;

        if (isCancelled) {
            handleStreamCancellation(conversationId, providerType, model, startTime, accumulatedContent, heartbeatScheduler);
            return;
        }

        // If partial content was already emitted, persist it as FAILED (do NOT falsely mark SENT)
        if (accumulatedContent != null && !accumulatedContent.isBlank()) {
            persistFailedAssistantMessage(conversationId, providerType.name(), model, accumulatedContent, "ERROR");
        }

        // Record failure in audit log
        recordAuditLog(conversationId, null, providerType.name(), model, latencyMs, 0, "ERROR", throwable.getMessage());

        // Emit sanitized StreamErrorEvent to client
        StreamErrorEvent errorEvent = sanitizeError(throwable);
        try {
            emitter.send(SseEmitter.event()
                .name(StreamEventType.ERROR)
                .data(errorEvent));
            emitter.complete();
        } catch (Exception ex) {
            log.debug("Could not emit error event to client for conversation [{}]: {}", conversationId, ex.getMessage());
        }
    }

    private void persistFailedAssistantMessage(
        UUID conversationId,
        String provider,
        String model,
        String partialContent,
        String finishReason
    ) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                Conversation lockedConvo = conversationRepository.findByIdForUpdate(conversationId)
                    .orElse(null);
                if (lockedConvo == null) return;

                int nextSequence = messageRepository.getNextSequenceNumber(conversationId);
                Message failedMessage = new Message(
                    lockedConvo,
                    nextSequence,
                    MessageRole.ASSISTANT,
                    partialContent,
                    MessageStatus.FAILED
                );

                Map<String, Object> metadata = new HashMap<>();
                metadata.put("provider", provider);
                metadata.put("model", model);
                metadata.put("partialStream", true);
                metadata.put("finishReason", finishReason);
                failedMessage.setMetadata(metadata);

                messageRepository.save(failedMessage);
                lockedConvo.setUpdatedAt(Instant.now());
                conversationRepository.save(lockedConvo);

                log.info("Persisted partial FAILED assistant message [{}] (seq: {}) for conversation [{}]",
                    failedMessage.getId(), nextSequence, conversationId);
            });
        } catch (Exception ex) {
            log.warn("Failed to persist partial assistant message for conversation [{}]: {}", conversationId, ex.getMessage());
        }
    }

    private StreamErrorEvent sanitizeError(Throwable throwable) {
        if (throwable instanceof AiProviderTimeoutException) {
            return new StreamErrorEvent("AI_PROVIDER_TIMEOUT", "AI provider request timed out. Please try again.");
        }
        if (throwable instanceof AiProviderUnavailableException) {
            return new StreamErrorEvent("AI_PROVIDER_UNAVAILABLE", "AI provider service is currently unavailable.");
        }
        if (throwable instanceof AiProviderRateLimitException) {
            return new StreamErrorEvent("AI_PROVIDER_RATE_LIMIT", "AI provider rate limit reached. Please wait and try again.");
        }
        if (throwable instanceof AiProviderAuthenticationException) {
            return new StreamErrorEvent("AI_PROVIDER_AUTH_ERROR", "AI provider authentication failed.");
        }
        if (throwable instanceof AiProviderException) {
            return new StreamErrorEvent("AI_PROVIDER_ERROR", "AI generation encountered a provider error.");
        }
        if (throwable instanceof IllegalArgumentException iae) {
            return new StreamErrorEvent("BAD_REQUEST", iae.getMessage());
        }
        return new StreamErrorEvent("AI_STREAM_ERROR", "An unexpected error occurred during AI streaming.");
    }

    private void cancelStreamHandle(AiStreamHandle handle) {
        if (handle != null && !handle.isCancelled()) {
            try {
                handle.cancel();
            } catch (Exception ex) {
                log.debug("Error cancelling stream handle: {}", ex.getMessage());
            }
        }
    }

    private void stopHeartbeat(ScheduledExecutorService scheduler) {
        if (scheduler != null && !scheduler.isShutdown()) {
            try {
                scheduler.shutdownNow();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Retrieves paginated messages in standalone no-auth mode.
     */
    @Transactional(readOnly = true)
    public PageResponse<MessageResponse> getMessages(UUID conversationId, int page, int size) {
        return getMessages(conversationId, null, page, size);
    }

    /**
     * Retrieves paginated messages for a conversation in deterministic sequence order.
     * Enforces ownership if userId is provided, and excludes deleted conversations.
     */
    @Transactional(readOnly = true)
    public PageResponse<MessageResponse> getMessages(UUID conversationId, UUID userId, int page, int size) {
        Conversation conversation = conversationRepository.findById(conversationId)
            .orElseThrow(() -> new ResourceNotFoundException("Conversation not found with id: " + conversationId));

        if (userId != null && conversation.getUser() != null && !conversation.getUser().getId().equals(userId)) {
            log.warn("Unauthorized message retrieval attempt: user [{}] tried to read messages for conversation [{}]", userId, conversationId);
            throw new AccessDeniedException("Access denied: You do not have permission to access this conversation");
        }

        if (conversation.getStatus() == ConversationStatus.DELETED) {
            throw new ConversationDeletedException("Cannot retrieve messages for a deleted conversation");
        }

        int sanitizedPage = Math.max(0, page);
        int sanitizedSize = Math.min(Math.max(1, size <= 0 ? DEFAULT_PAGE_SIZE : size), MAX_PAGE_SIZE);

        Pageable pageable = PageRequest.of(sanitizedPage, sanitizedSize, Sort.by(Sort.Direction.ASC, "sequenceNumber"));
        Page<Message> messagePage = messageRepository.findByConversationIdOrderBySequenceNumberAsc(conversationId, pageable);

        return PageResponse.from(messagePage.map(messageMapper::toResponse));
    }
}
