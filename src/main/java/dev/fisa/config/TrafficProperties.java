package dev.fisa.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "traffic")
public class TrafficProperties {
    private String strategy;
    private int tpsLimit;
}