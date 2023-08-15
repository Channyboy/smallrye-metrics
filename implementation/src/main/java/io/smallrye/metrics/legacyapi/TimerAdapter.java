package io.smallrye.metrics.legacyapi;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.metrics.Snapshot;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Timer.Builder;
import io.smallrye.metrics.setup.config.DefaulBucketConfiguration;
import io.smallrye.metrics.setup.config.MetricPercentileConfiguration;
import io.smallrye.metrics.setup.config.MetricsConfigurationManager;
import io.smallrye.metrics.setup.config.TimerBucketConfiguration;
import io.smallrye.metrics.setup.config.TimerBucketMaxConfiguration;
import io.smallrye.metrics.setup.config.TimerBucketMinConfiguration;

class TimerAdapter implements org.eclipse.microprofile.metrics.Timer, MeterHolder {

    private static final String CLASS_NAME = TimerAdapter.class.getName();
    private static final Logger LOGGER = Logger.getLogger(CLASS_NAME);

    private final static int PRECISION;

    Timer globalCompositeTimer;

    /*
     * Increasing the percentile precision for timers will consume more memory. This setting is "3" by
     * default, and provided to adjust the precision to your needs.
     */
    static {
        PRECISION = ConfigProvider.getConfig().getOptionalValue("mp.metrics.smallrye.timer.precision", Integer.class)
                .orElse(3);
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.logp(Level.FINE, CLASS_NAME, null,
                    "Resolved MicroProfile Config value for mp.metrics.smallrye.timer.precision as \"{0}\"", PRECISION);
        }
    }

    final MeterRegistry registry;

    TimerAdapter(MeterRegistry registry) {
        this.registry = registry;
    }

    public TimerAdapter register(MpMetadata metadata, MetricDescriptor descriptor, String scope, Tag... globalTags) {

        if (globalCompositeTimer == null || metadata.cleanDirtyMetadata()) {

            MetricPercentileConfiguration percentilesConfig = MetricsConfigurationManager.getInstance()
                    .getPercentilesConfiguration(metadata.getName());

            TimerBucketConfiguration bucketsConfig = MetricsConfigurationManager.getInstance()
                    .getTimerBucketConfiguration(metadata.getName());

            DefaulBucketConfiguration defaultBucketConfig = MetricsConfigurationManager.getInstance()
                    .getDefaultBucketConfiguration(metadata.getName());

            Set<Tag> tagsSet = new HashSet<Tag>();
            for (Tag t : descriptor.tags()) {
                tagsSet.add(t);
            }

            if (globalTags != null) {
                for (Tag t : globalTags) {
                    tagsSet.add(t);
                }
            }

            tagsSet.add(Tag.of(LegacyMetricRegistryAdapter.MP_SCOPE_TAG, scope));

            Builder builder = Timer.builder(descriptor.name()).description(metadata.getDescription()).tags(tagsSet)
                    .percentilePrecision(PRECISION);

            if (percentilesConfig != null && percentilesConfig.getValues() != null
                    && percentilesConfig.getValues().length > 0) {
                double[] vals = Stream.of(percentilesConfig.getValues()).mapToDouble(Double::doubleValue).toArray();
                builder = builder.publishPercentiles(vals);
            } else if (percentilesConfig != null && percentilesConfig.getValues() == null
                    && percentilesConfig.isDisabled() == true) {
                // do nothing - percentiles were disabled
            } else {
                builder = builder.publishPercentiles(0.5, 0.75, 0.95, 0.98, 0.99, 0.999);
            }

            if (bucketsConfig != null && bucketsConfig.getValues().length > 0) {
                builder = builder.serviceLevelObjectives(bucketsConfig.getValues());
            }

            if (defaultBucketConfig != null && defaultBucketConfig.getIsEnabled() == true) {
                builder = builder.publishPercentileHistogram(defaultBucketConfig.getIsEnabled());

                // max and min
                TimerBucketMaxConfiguration defaultBucketMaxConfig = MetricsConfigurationManager.getInstance()
                        .getDefaultTimerMaxBucketConfiguration(metadata.getName());

                if (defaultBucketMaxConfig != null && defaultBucketMaxConfig.getValue() != null) {
                    builder = builder.maximumExpectedValue(defaultBucketMaxConfig.getValue());
                }

                TimerBucketMinConfiguration defaultBucketMinConfig = MetricsConfigurationManager.getInstance()
                        .getDefaultTimerMinBucketConfiguration(metadata.getName());

                if (defaultBucketMinConfig != null && defaultBucketMinConfig.getValue() != null) {
                    builder = builder.minimumExpectedValue(defaultBucketMinConfig.getValue());
                }
            }

            globalCompositeTimer = builder.register(Metrics.globalRegistry);

        }

        return this;
    }

    public void update(long l, TimeUnit timeUnit) {
        globalCompositeTimer.record(l, timeUnit);
    }

    @Override
    public void update(Duration duration) {
        globalCompositeTimer.record(duration);
    }

    @Override
    public <T> T time(Callable<T> callable) throws Exception {
        return globalCompositeTimer.wrap(callable).call();
    }

    @Override
    public void time(Runnable runnable) {
        globalCompositeTimer.wrap(runnable).run();
    }

    @Override
    public SampleAdapter time() {
        return new SampleAdapter(globalCompositeTimer, Timer.start(Metrics.globalRegistry));
    }

    @Override
    public Duration getElapsedTime() {
        return Duration.ofNanos((long) globalCompositeTimer.totalTime(TimeUnit.NANOSECONDS));
    }

    @Override
    public long getCount() {
        return globalCompositeTimer.count();
    }

    @Override
    public Snapshot getSnapshot() {
        return new SnapshotAdapter(globalCompositeTimer.takeSnapshot());
    }

    @Override
    public Meter getMeter() {
        return globalCompositeTimer;
    }

    public Timer.Sample start() {
        return Timer.start(registry);
    }

    public void stop(Timer.Sample sample) {
        sample.stop(globalCompositeTimer);
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
}
