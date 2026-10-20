package dev.techmart.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.List;

@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        return template;
    }

    /** Trừ tồn flash sale - kiểm tồn, kiểm giới hạn/user và trừ trong một lệnh. */
    @Bean
    public RedisScript<Long> flashSalePurchaseScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/flashsale-purchase.lua"));
        script.setResultType(Long.class);
        return script;
    }

    /** Token bucket dùng chung cho mọi instance. */
    @Bean
    @SuppressWarnings({"rawtypes", "unchecked"})
    public RedisScript<List> rateLimitScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/rate-limit.lua"));
        script.setResultType(List.class);
        return script;
    }

    /** Đếm lần đăng nhập sai theo tài khoản và dựng cờ khoá tạm thời (ADR-0012). */
    @Bean
    public RedisScript<Long> loginAttemptScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/login-attempt.lua"));
        script.setResultType(Long.class);
        return script;
    }
}
