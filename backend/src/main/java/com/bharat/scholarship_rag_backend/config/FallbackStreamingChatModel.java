package com.bharat.scholarship_rag_backend.config;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A {@link StreamingChatModel} that tries several providers in order and falls
 * through to the next one when a provider fails <em>before</em> emitting any
 * token.
 *
 * <p>Once a provider has streamed its first token the fallback stops: the
 * already-emitted text cannot be un-sent, so restarting on another provider
 * would duplicate or interleave output. In that case the error is forwarded as
 * usual.
 */
@Slf4j
public class FallbackStreamingChatModel implements StreamingChatModel {

    /** A named delegate, so log lines and the chain order are self-describing. */
    public record Provider(String name, StreamingChatModel streamingModel) {
    }

    private final List<Provider> providers;

    public FallbackStreamingChatModel(List<Provider> providers) {
        this.providers = List.copyOf(providers);
    }

    @Override
    public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
        if (providers.isEmpty()) {
            handler.onError(new IllegalStateException("No LLM streaming providers are configured"));
            return;
        }
        attempt(0, request, handler);
    }

    private void attempt(int index, ChatRequest request, StreamingChatResponseHandler handler) {
        Provider provider = providers.get(index);
        boolean hasNext = index + 1 < providers.size();
        AtomicBoolean emitted = new AtomicBoolean(false);

        StreamingChatResponseHandler delegatingHandler = new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                emitted.set(true);
                handler.onPartialResponse(partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                handler.onCompleteResponse(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                if (!emitted.get() && hasNext) {
                    Provider next = providers.get(index + 1);
                    log.warn("LLM streaming provider '{}' failed before emitting any token; "
                            + "falling back to '{}'", provider.name(), next.name(), error);
                    attempt(index + 1, request, handler);
                } else {
                    if (emitted.get()) {
                        log.warn("LLM streaming provider '{}' failed after streaming started; "
                                + "no fallback is possible mid-stream", provider.name(), error);
                    }
                    handler.onError(error);
                }
            }
        };

        try {
            provider.streamingModel().chat(request, delegatingHandler);
        } catch (RuntimeException e) {
            if (!emitted.get() && hasNext) {
                Provider next = providers.get(index + 1);
                log.warn("LLM streaming provider '{}' threw before emitting any token; "
                        + "falling back to '{}'", provider.name(), next.name(), e);
                attempt(index + 1, request, handler);
            } else {
                handler.onError(e);
            }
        }
    }

    @Override
    public String toString() {
        return "FallbackStreamingChatModel" + providers.stream().map(Provider::name).toList();
    }
}
