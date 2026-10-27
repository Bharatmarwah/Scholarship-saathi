package com.bharat.scholarship_rag_backend.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallenv15.BgeSmallEnV15EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Wires the LLM providers behind a fallback chain.
 *
 * <p>Every provider speaks the OpenAI-compatible protocol, so all of them are
 * built from {@link OpenAiChatModel}/{@link OpenAiStreamingChatModel} with a
 * different base URL, key and model name. The order is Groq (primary, cheapest
 * and fastest) then Google Gemini then OpenRouter. A provider whose API key is
 * blank is skipped, so the chain degrades gracefully when a key is absent.
 *
 * <p>Retries are set to zero on each provider: a transient failure falls
 * through to the next provider immediately instead of being retried against the
 * same one, which is the whole point of having a chain.
 */
@Configuration
@Slf4j
public class LlmConfig {

    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    @Value("${groq.api.key:}")
    private String groqApiKey;
    @Value("${groq.model.name:}")
    private String groqModelName;
    @Value("${groq.base.url:}")
    private String groqBaseUrl;

    @Value("${gemini.api.key:}")
    private String geminiApiKey;
    @Value("${gemini.model.name:gemini-2.5-flash}")
    private String geminiModelName;
    @Value("${gemini.base.url:https://generativelanguage.googleapis.com/v1beta/openai/}")
    private String geminiBaseUrl;

    @Value("${openrouter.api.key:}")
    private String openRouterApiKey;
    @Value("${openrouter.model.name:}")
    private String openRouterModelName;
    @Value("${openrouter.base.url:https://openrouter.ai/api/v1}")
    private String openRouterBaseUrl;

    /**
     * Caps the completion size for every provider. The langchain4j default is
     * the provider's own ceiling, which for some models is tens of thousands of
     * tokens: OpenRouter then rejects the request with a 402 because the
     * account cannot afford that many output tokens, and the whole chain fails
     * even though the prompt itself was fine.
     */
    @Value("${llm.max-tokens:4096}")
    private int maxTokens;

    @Bean
    public ChatModel chatModel() {
        List<FallbackChatModel.Provider> providers = new ArrayList<>();

        addChatProvider(providers, "groq", groqApiKey, groqModelName, groqBaseUrl);
        addChatProvider(providers, "gemini", geminiApiKey, geminiModelName, geminiBaseUrl);
        addChatProvider(providers, "openrouter", openRouterApiKey, openRouterModelName, openRouterBaseUrl);

        log.info("Chat model fallback chain: {}",
                providers.stream().map(FallbackChatModel.Provider::name).toList());
        return new FallbackChatModel(providers);
    }

    @Bean
    public StreamingChatModel streamingChatModel() {
        List<FallbackStreamingChatModel.Provider> providers = new ArrayList<>();

        addStreamingProvider(providers, "groq", groqApiKey, groqModelName, groqBaseUrl);
        addStreamingProvider(providers, "gemini", geminiApiKey, geminiModelName, geminiBaseUrl);
        addStreamingProvider(providers, "openrouter", openRouterApiKey, openRouterModelName, openRouterBaseUrl);

        log.info("Streaming chat model fallback chain: {}",
                providers.stream().map(FallbackStreamingChatModel.Provider::name).toList());
        return new FallbackStreamingChatModel(providers);
    }

    /**
     * Local ONNX embedding model (bge-small-en-v1.5, 384 dimensions). Running
     * embeddings in-process keeps ingestion off any provider rate limit and
     * keeps the stored vectors consistent with the query vectors.
     *
     * <p>The model expects an instruction on the query side only; that is added
     * by {@link com.bharat.scholarship_rag_backend.embedding.QueryEmbedding},
     * not here, so stored document vectors stay instruction-free.
     */
    @Bean
    public EmbeddingModel embeddingModel() {
        return new BgeSmallEnV15EmbeddingModel();
    }

    private void addChatProvider(
            List<FallbackChatModel.Provider> providers,
            String name, String apiKey, String modelName, String baseUrl) {
        if (!isConfigured(name, apiKey)) {
            return;
        }
        providers.add(new FallbackChatModel.Provider(name, OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .maxTokens(maxTokens)
                .maxRetries(0)
                .timeout(TIMEOUT)
                .build()));
    }

    private void addStreamingProvider(
            List<FallbackStreamingChatModel.Provider> providers,
            String name, String apiKey, String modelName, String baseUrl) {
        if (!isConfigured(name, apiKey)) {
            return;
        }
        providers.add(new FallbackStreamingChatModel.Provider(name, OpenAiStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .maxTokens(maxTokens)
                .timeout(TIMEOUT)
                .build()));
    }

    private boolean isConfigured(String name, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("LLM provider '{}' is skipped: no API key configured", name);
            return false;
        }
        return true;
    }
}
