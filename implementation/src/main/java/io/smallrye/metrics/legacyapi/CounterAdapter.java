package io.smallrye.metrics.legacyapi;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.microprofile.metrics.MetricType;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;
import io.smallrye.metrics.SharedMetricRegistries;

class CounterAdapter implements org.eclipse.microprofile.metrics.Counter, MeterHolder {

    Counter counter;

    /*
     * Have to hold on to meta data for getCount().
     * Sometimes the meter instance (CompositeCounter)
     * is unable to retrieve the value even if the individual meter
     * is updated in the respective target meter registries.
     */
    MeterRegistry registry;
    MetricDescriptor descriptor;
    String scope;
    Set<Tag> tagsSet = new HashSet<Tag>();

    public CounterAdapter register(MpMetadata metadata, MetricDescriptor descriptor, MeterRegistry registry, String scope) {

        ThreadLocal<Boolean> threadLocal = SharedMetricRegistries.getThreadLocal(scope);
        threadLocal.set(true);
        //if we're creating a new counter... or we're "updating" an existing one with new metadata (but this doesnt actually register with micrometer)
        if (counter == null || metadata.cleanDirtyMetadata()) {

            /*
             * Save metadata to this CounterAdapter
             * for use with getCount()
             */
            this.registry = registry;
            this.descriptor = descriptor;
            this.scope = scope;

            tagsSet = new HashSet<Tag>();
            for (Tag t : descriptor.tags()) {
                tagsSet.add(t);
            }
            tagsSet.add(Tag.of("scope", scope));

            counter = Counter.builder(descriptor.name())
                    .description(metadata.getDescription())
                    .baseUnit(metadata.getUnit())
                    .tags(tagsSet)
                    .register(Metrics.globalRegistry);
        }

        threadLocal.set(false);
        return this;
    }

    @Override
    public void inc() {
        counter.increment();
    }

    @Override
    public void inc(long l) {
        counter.increment(l);
    }

    @Override
    /**
     * We need to obtain the Counter from the respective registry.
     * The Composite Counter held by by the `counter` (created by Global composite registry)
     * sometimes is unable to retrieve the value and returns 0.
     */
    public long getCount() {
        Counter promCounter = registry.find(descriptor.name()).tags(tagsSet).counter();
        if (promCounter != null) {
            return (long) promCounter.count();
        }
        return (long) counter.count();
    }

    @Override
    public Meter getMeter() {
        return counter;
    }

    @Override
    public MetricType getType() {
        return MetricType.COUNTER;
    }
}
