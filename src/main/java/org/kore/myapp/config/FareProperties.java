package org.kore.myapp.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.fare")
public record FareProperties(
        @Min(0) long minimum,
        @Min(1) long perMeter
) {}
