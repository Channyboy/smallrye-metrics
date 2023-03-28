package io.smallrye.metrics.setup;

public interface SpanContextCallback {

    public String getTraceId();

    public String getSpanId();
}
