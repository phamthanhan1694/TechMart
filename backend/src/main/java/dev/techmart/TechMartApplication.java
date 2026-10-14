package dev.techmart;

import dev.techmart.common.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Loại {@code UserDetailsServiceAutoConfiguration}: xác thực đi bằng JWT tự quản lý, không
 * dùng {@code UserDetailsService}. Để mặc định thì Boot dựng một user "user" trong bộ nhớ
 * với mật khẩu sinh ngẫu nhiên in ra log - không dùng được, nhưng nhìn log dễ tưởng là có
 * một cửa đăng nhập thứ hai.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableConfigurationProperties(AppProperties.class)
@EnableScheduling
public class TechMartApplication {

    public static void main(String[] args) {
        SpringApplication.run(TechMartApplication.class, args);
    }
}
