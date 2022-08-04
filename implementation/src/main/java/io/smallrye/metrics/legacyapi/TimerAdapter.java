package io.smallrye.metrics.legacyapi;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.Snapshot;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.smallrye.metrics.SharedMetricRegistries;

class TimerAdapter implements org.eclipse.microprofile.metrics.Timer, MeterHolder {
    Timer timer;

    /*
     * Have to hold on to meta data for get*().
     * Sometimes the meter instance (CompositeTimer)
     * is unable to retrieve the value even if the individual meter
     * is updated in the respective target meter registries.
     */
    final MeterRegistry registry;
    MetricDescriptor descriptor;
    String scope;
    Set<Tag> tagsSet = new HashSet<Tag>();

    // which MP metric type this adapter represents - this is needed because the same class is used as an adapter for Timer and SimpleTimer
    // if this is actually a SimpleTimer, this value will be changed to reflect that
    MetricType metricType = MetricType.TIMER;

    TimerAdapter(MeterRegistry registry) {
        this.registry = registry;
    }

    public TimerAdapter register(MpMetadata metadata, MetricDescriptor descriptor, String scope) {

        ThreadLocal<Boolean> threadLocal = SharedMetricRegistries.getThreadLocal(scope);
        threadLocal.set(true);
        if (timer == null || metadata.cleanDirtyMetadata()) {

            /*
             * Save metadata to this CounterAdapter
             * for use with get*() values
             */
            this.descriptor = descriptor;
            this.scope = scope;

            tagsSet = new HashSet<Tag>();
            for (Tag t : descriptor.tags()) {
                tagsSet.add(t);
            }
            tagsSet.add(Tag.of("scope", scope));

            timer = Timer
                    .builder(descriptor.name())
                    .description(metadata.getDescription())
                    .tags(tagsSet)
                    .publishPercentiles(0.5, 0.75, 0.95, 0.98, 0.99, 0.999)
                    .percentilePrecision(5) //from 0 - 5 , more precision == more memory usage
                    .register(Metrics.globalRegistry);
        }
        threadLocal.set(false);
        return this;
    }

    public void update(long l, TimeUnit timeUnit) {
        timer.record(l, timeUnit);
    }

    @Override
    public void update(Duration duration) {
        timer.record(duration);
    }

    @Override
    public <T> T time(Callable<T> callable) throws Exception {
        return timer.wrap(callable).call();
    }

    @Override
    public void time(Runnable runnable) {
        timer.wrap(runnable);
    }

    @Override
    public SampleAdapter time() {
        return new SampleAdapter(timer, Timer.start(registry));
    }

    @Override
    public Duration getElapsedTime() {
        Timer promTimer = registry.find(descriptor.name()).tags(tagsSet).timer();
        if (promTimer != null) {
            return Duration.ofNanos((long)promTimer.totalTime(TimeUnit.NANOSECONDS));
        }
        return Duration.ofNanos((long)timer.totalTime(TimeUnit.NANOSECONDS));
    }

    @Override
    public long getCount() {
        Timer promTimer = registry.find(descriptor.name()).tags(tagsSet).timer();
        if (promTimer != null) {
            return (long) promTimer.count();
        }

        return timer.count();
    }

    @Override
    /** TODO: Separate Issue/PR impl Snapshot adapter*/
    public Snapshot getSnapshot() {
        throw new UnsupportedOperationException("This operation is not supported when used with micrometer");
    }

    @Override
    public Meter getMeter() {
        return timer;
    }

    public Timer.Sample start() {
        return Timer.start(registry);
    }

    public void stop(Timer.Sample sample) {
        sample.stop(timer);
    }

    class SampleAdapter implements org.eclipse.microprofile.metrics.Timer.Context {
        final Timer timer;
        final Timer.Sample sample;

        SampleAdapter(Timer timer, Timer.Sample sample) {
            this.sample = sample;
            this.timer = timer;
        }

        @Override
        public long stop() {
            return sample.stop(timer);
        }

        @Override
        public void close() {
            sample.stop(timer);
        }
    }

    @Override
    public MetricType getType() {
        return metricType;
    }
}
