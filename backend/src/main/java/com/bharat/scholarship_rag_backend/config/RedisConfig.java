package com.bharat.scholarship_rag_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory redisConnectionFactory) {

        RedisTemplate<String, Object> template = new RedisTemplate<>();

        template.setConnectionFactory(redisConnectionFactory);

        template.setKeySerializer(
                new StringRedisSerializer()
        );

        // Conversation messages are stored as explicit JSON strings (see
        // ConversationMemory). A type-tagging serializer is deliberately
        // avoided: with a custom mapper it produced type-less JSON that came
        // back as LinkedHashMap instead of ChatMessage.
        template.setValueSerializer(
                new StringRedisSerializer()
        );

        template.afterPropertiesSet();

        return template;
    }
}