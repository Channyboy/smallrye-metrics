package io.smallrye.metrics.legacyapi;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.Snapshot;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;
import io.smallrye.metrics.SharedMetricRegistries;

class HistogramAdapter implements Histogram, MeterHolder {

    /*
     * Due to multiple Prometheus meter registries being registered to the global
     * composite meter registry with deny filters used, this can lead to a problem
     * when the composite meter is retrieving a value of the meter. It will chose
     * the "first" meter registry associated to the composite meter. This meter
     * registry may have returned a Noop meter (due it being denied). As a result,
     * querying this composite meter for a value can return a 0.
     * 
     * We keep acquire the Prometheus meter registry's meter and use it to retrieve
     * values. Can't just acquire the meter during value retrieval due to situation
     * where if this meter(holder) was removed from the MP shim, the application
     * code could still have reference to this object and can still perform a get
     * value calls.
     * 
     * We keep the global composite meter as this is what is "used" when we need to
     * remove this meter. The composite meter's object ref is used to remove from
     * the global composite registry.
     * 
     * See SharedMetricRegistries.java for more information.
     * 
     */

    DistributionSummary globalCompositeSummary;
    DistributionSummary promSummary;

    HistogramAdapter register(MpMetadata metadata, MetricDescriptor metricInfo, MeterRegistry registry, String scope) {

        ThreadLocal<Boolean> threadLocal = SharedMetricRegistries.getThreadLocal(scope);

        threadLocal.set(true);
        if (globalCompositeSummary == null || metadata.cleanDirtyMetadata()) {

            Set<Tag> tagsSet = new HashSet<Tag>();
            for (Tag t : metricInfo.tags()) {
                tagsSet.add(t);
            }
            tagsSet.add(Tag.of("scope", scope));

            globalCompositeSummary = DistributionSummary.builder(metricInfo.name())
                    .description(metadata.getDescription())
                    .baseUnit(metadata.getUnit())
                    .tags(tagsSet)
                    .publishPercentiles(0.5, 0.75, 0.95, 0.98, 0.99, 0.999)
                    .percentilePrecision(5)
                    .register(Metrics.globalRegistry);
        }
        threadLocal.set(false);
        return this;
    }

    @Override
    public void update(int i) {
        globalCompositeSummary.record(i);
    }

    @Override
    public void update(long l) {
        globalCompositeSummary.record(l);
    }

    @Override
    public long getCount() {
        return promSummary.count();
    }

    @Override
    public long getSum() {
        return (long) promSummary.takeSnapshot().total();
    }

    @Override
    public Snapshot getSnapshot() {
        return new SnapshotAdapter(promSummary.takeSnapshot());
    }

    @Override
    public Meter getMeter() {
        return globalCompositeSummary;
    }

    @Override
    public MetricType getType() {
        return MetricType.HISTOGRAM;
    }
}
