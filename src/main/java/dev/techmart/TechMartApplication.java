package dev.techmart;

import dev.techmart.common.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
@EnableScheduling
public class TechMartApplication {

    public static void main(String[] args) {
        SpringApplication.run(TechMartApplication.class, args);
    }
}
