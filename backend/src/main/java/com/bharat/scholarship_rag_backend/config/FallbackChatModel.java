package com.bharat.scholarship_rag_backend.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * A {@link ChatModel} that tries several providers in order and falls through
 * to the next one when a provider throws (rate limit, provider outage, invalid
 * response). The final exception is rethrown only when every provider failed,
 * so a caller never silently receives an empty answer.
 */
@Slf4j
public class FallbackChatModel implements ChatModel {

    /** A named delegate, so log lines and the chain order are self-describing. */
    public record Provider(String name, ChatModel model) {
    }

    private final List<Provider> providers;

    public FallbackChatModel(List<Provider> providers) {
        this.providers = List.copyOf(providers);
    }

    @Override
    public ChatResponse doChat(ChatRequest request) {
        if (providers.isEmpty()) {
            throw new IllegalStateException("No LLM chat providers are configured");
        }

        RuntimeException last = null;
        for (int i = 0; i < providers.size(); i++) {
            Provider provider = providers.get(i);
            try {
                // Delegate to chat() (not doChat) so each provider merges the
                // request with ITS OWN defaults. Forwarding via doChat would
                // require the request to already carry provider-specific
                // parameters (e.g. an OpenAI model name), which would be the
                // wrong model for the other providers in the chain.
                return provider.model().chat(request);
            } catch (RuntimeException e) {
                last = e;
                if (i + 1 < providers.size()) {
                    log.warn("LLM chat provider '{}' failed; trying the next provider",
                            provider.name(), e);
                } else {
                    log.warn("LLM chat provider '{}' failed; no providers left",
                            provider.name(), e);
                }
            }
        }
        throw last;
    }

    @Override
    public String toString() {
        return "FallbackChatModel" + providers.stream().map(Provider::name).toList();
    }
}
