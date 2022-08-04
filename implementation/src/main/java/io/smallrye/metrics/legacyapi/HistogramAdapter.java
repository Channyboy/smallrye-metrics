package io.smallrye.metrics.legacyapi;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.Snapshot;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;
import io.smallrye.metrics.SharedMetricRegistries;

class HistogramAdapter implements Histogram, MeterHolder {
    DistributionSummary summary;

    /*
     * Have to hold on to meta data for get*().
     * Sometimes the meter instance (CompositeTimer)
     * is unable to retrieve the value even if the individual meter
     * is updated in the respective target meter registries.
     */
    MeterRegistry registry;
    MetricDescriptor descriptor;
    String scope;
    Set<Tag> tagsSet = new HashSet<Tag>();
    
    HistogramAdapter register(MpMetadata metadata, MetricDescriptor metricInfo, MeterRegistry registry, String scope) {

        ThreadLocal<Boolean> threadLocal = SharedMetricRegistries.getThreadLocal(scope);

        threadLocal.set(true);
        if (summary == null || metadata.cleanDirtyMetadata()) {
            
            /*
             * Save metadata to this CounterAdapter
             * for use with getCount()
             */
            this.registry = registry;
            this.descriptor = metricInfo;
            this.scope = scope;

            tagsSet = new HashSet<Tag>();
            for (Tag t : metricInfo.tags()) {
                tagsSet.add(t);
            }
            tagsSet.add(Tag.of("scope", scope));
            
            summary = DistributionSummary.builder(metricInfo.name())
                    .description(metadata.getDescription())
                    .baseUnit(metadata.getUnit())
                    .tags(tagsSet)
                    .publishPercentiles(0.5, 0.75, 0.95, 0.98, 0.99, 0.999)
                    .percentilePrecision(5) //from 0 - 5 , more precision == more memory usage
                    .register(Metrics.globalRegistry);
        }

        threadLocal.set(false);
        return this;
    }

    @Override
    public void update(int i) {
        summary.record(i);
    }

    @Override
    public void update(long l) {
        summary.record(l);
    }

    @Override
    public long getCount() {
        DistributionSummary promDistSum = registry.find(descriptor.name()).tags(tagsSet).summary();
        if (promDistSum != null) {
            return (long) promDistSum.count();
        }
        return summary.count();
    }

    @Override
    public long getSum() {
        DistributionSummary promDistSum = registry.find(descriptor.name()).tags(tagsSet).summary();
        if (promDistSum != null) {
            return (long) promDistSum.takeSnapshot().total();
        }
        return (long) summary.takeSnapshot().total();
    }

    /** TODO: Separate Issue/PR impl Snapshot adapter*/
    @Override
    public Snapshot getSnapshot() {
        throw new UnsupportedOperationException("This operation is not supported when used with micrometer");
    }

    @Override
    public Meter getMeter() {
        return summary;
    }

    @Override
    public MetricType getType() {
        return MetricType.HISTOGRAM;
    }
}
