package io.smallrye.metrics.legacyapi.interceptors;

import jakarta.enterprise.inject.spi.AnnotatedMember;
import jakarta.enterprise.inject.spi.InjectionPoint;

public interface MetricName {

    String of(InjectionPoint point);

    String of(AnnotatedMember<?> member);

    // TODO: expose an SPI so that external strategies can be provided. For example, Camel CDI could provide a property placeholder resolution strategy.
    String of(String attribute);
}
