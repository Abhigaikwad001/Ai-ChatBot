package com.chatbot.platform.core.service.ai;

import com.chatbot.platform.core.domain.enums.ProviderType;
import com.chatbot.platform.infrastructure.config.ai.AiProperties;
import com.chatbot.platform.infrastructure.exception.ai.AiProviderUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Registry and factory for AI provider implementations.
 * Resolves concrete providers dynamically at runtime based on conversation configuration
 * or application defaults without conditional logic inside business services.
 */
@Component
public class AiProviderRegistry {

    private static final Logger log = LoggerFactory.getLogger(AiProviderRegistry.class);

    private final Map<ProviderType, AiProvider> providers = new EnumMap<>(ProviderType.class);
    private final AiProperties aiProperties;

    public AiProviderRegistry(List<AiProvider> providerBeans, AiProperties aiProperties) {
        this.aiProperties = aiProperties;
        // Sort non-primary first, primary second so @Primary beans take precedence in the registry map
        List<AiProvider> sorted = providerBeans.stream()
            .sorted(java.util.Comparator.comparingInt(p -> p.getClass().isAnnotationPresent(org.springframework.context.annotation.Primary.class) ? 1 : 0))
            .toList();

        for (AiProvider provider : sorted) {
            this.providers.put(provider.getProviderType(), provider);
            log.info("Registered AI Provider implementation: [{}] -> {}{}",
                provider.getProviderType(),
                provider.getClass().getSimpleName(),
                provider.getClass().isAnnotationPresent(org.springframework.context.annotation.Primary.class) ? " [PRIMARY]" : "");
        }
    }

    /**
     * Resolves an AiProvider by its explicit ProviderType.
     *
     * @param type the requested provider type
     * @return the matching AiProvider implementation
     * @throws AiProviderUnavailableException if no provider is registered for the given type
     */
    public AiProvider getProvider(ProviderType type) {
        if (type == null) {
            return getDefaultProvider();
        }
        AiProvider provider = providers.get(type);
        if (provider == null) {
            log.warn("Requested AI provider [{}] is not available in registry. Registered providers: {}",
                type, providers.keySet());
            throw new AiProviderUnavailableException(type.name(),
                "AI provider is not configured or supported on this system: " + type);
        }
        return provider;
    }

    /**
     * Resolves the default configured AiProvider.
     */
    public AiProvider getDefaultProvider() {
        ProviderType defaultType = aiProperties.getDefaultProvider();
        return getProvider(defaultType);
    }

    public boolean hasProvider(ProviderType type) {
        return providers.containsKey(type);
    }

    public Set<ProviderType> getRegisteredTypes() {
        return Collections.unmodifiableSet(providers.keySet());
    }
}
