package io.smallrye.metrics;

import io.prometheus.client.exemplars.tracer.common.SpanContextSupplier;
import io.smallrye.metrics.setup.SpanContextCallback;

public class MPSpanContextSupplier implements SpanContextSupplier {

    private SpanContextCallback spanContextCallback;

    @Override
    public String getTraceId() {
        return (spanContextCallback == null) ? "111" : spanContextCallback.getTraceId();
    }

    @Override
    public String getSpanId() {
        return (spanContextCallback == null) ? "222" : spanContextCallback.getSpanId();
    }

    public void setSpanContextCallback(SpanContextCallback spanContextCallback) {
        if (this.spanContextCallback != null) {
            // log it out
        }
        this.spanContextCallback = spanContextCallback;
    }

}
