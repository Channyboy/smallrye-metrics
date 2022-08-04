package io.smallrye.metrics.legacyapi;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.MetricType;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;
import io.smallrye.metrics.SharedMetricRegistries;

interface GaugeAdapter<T> extends Gauge<T>, MeterHolder {

    GaugeAdapter<T> register(MpMetadata metadata, MetricDescriptor metricInfo, MeterRegistry registry, String scope);

    static class DoubleFunctionGauge<S> implements GaugeAdapter<Double> {
        io.micrometer.core.instrument.Gauge gauge;

        final S obj;
        final ToDoubleFunction<S> f;

        MeterRegistry registry;
        MetricDescriptor descriptor;
        String scope;
        Set<Tag> tagsSet = new HashSet<Tag>();

        DoubleFunctionGauge(S obj, ToDoubleFunction<S> f) {
            this.obj = obj;
            this.f = f;
        }

        public GaugeAdapter<Double> register(MpMetadata metadata, MetricDescriptor metricInfo, MeterRegistry registry,
                String scope) {

            ThreadLocal<Boolean> threadLocal = SharedMetricRegistries.getThreadLocal(scope);
            threadLocal.set(true);

            /*
             * Save metadata to this Adapter
             * for use with getValue()
             */
            this.registry = registry;
            this.descriptor = metricInfo;
            this.scope = scope;

            tagsSet = new HashSet<Tag>();
            for (Tag t : metricInfo.tags()) {
                tagsSet.add(t);
            }
            tagsSet.add(Tag.of("scope", scope));

            gauge = io.micrometer.core.instrument.Gauge.builder(metricInfo.name(), obj, f)
                    .description(metadata.getDescription())
                    .tags(tagsSet)
                    .baseUnit(metadata.getUnit())
                    .strongReference(true)
                    .register(Metrics.globalRegistry);
            threadLocal.set(false);
            return this;
        }

        @Override
        public Meter getMeter() {
            return gauge;
        }

        @Override
        public Double getValue() {
            io.micrometer.core.instrument.Gauge promGauge = registry.find(descriptor.name()).tags(tagsSet).gauge();

            if (promGauge != null) {
                return promGauge.value();
            }

            return gauge.value();
        }

        @Override
        public MetricType getType() {
            return MetricType.GAUGE;
        }
    }

    static class FunctionGauge<S, R extends Number> implements GaugeAdapter<R> {
        io.micrometer.core.instrument.Gauge gauge;

        final S obj;
        final Function<S, R> f;
        MeterRegistry registry;
        MetricDescriptor descriptor;
        String scope;
        Set<Tag> tagsSet = new HashSet<Tag>();

        FunctionGauge(S obj, Function<S, R> f) {
            this.obj = obj;
            this.f = f;
        }

        public GaugeAdapter<R> register(MpMetadata metadata, MetricDescriptor metricInfo, MeterRegistry registry,
                String scope) {
            ThreadLocal<Boolean> threadLocal = SharedMetricRegistries.getThreadLocal(scope);
            threadLocal.set(true);

            /*
             * Save metadata to this Adapter
             * for use with getValue()
             */
            this.registry = registry;
            this.descriptor = metricInfo;
            this.scope = scope;

            tagsSet = new HashSet<Tag>();
            for (Tag t : metricInfo.tags()) {
                tagsSet.add(t);
            }
            tagsSet.add(Tag.of("scope", scope));

            gauge = io.micrometer.core.instrument.Gauge.builder(metricInfo.name(), obj, obj -> f.apply(obj).doubleValue())
                    .description(metadata.getDescription())
                    .tags(tagsSet)
                    .baseUnit(metadata.getUnit())
                    .strongReference(true)
                    .register(Metrics.globalRegistry);
            threadLocal.set(false);
            return this;
        }

        @Override
        public Meter getMeter() {
            return gauge;
        }

        @Override
        public R getValue() {
            io.micrometer.core.instrument.Gauge promGauge = registry.find(descriptor.name()).tags(tagsSet).gauge();

            if (promGauge != null) {
                return (R) (Double) promGauge.value();
            }

            return (R) (Double) gauge.value();
        }

        @Override
        public MetricType getType() {
            return MetricType.GAUGE;
        }
    }

    static class NumberSupplierGauge<T extends Number> implements GaugeAdapter<T> {
        io.micrometer.core.instrument.Gauge gauge;
        final Supplier<T> supplier;

        NumberSupplierGauge(Supplier<T> supplier) {
            this.supplier = supplier;
        }

        @Override
        public GaugeAdapter<T> register(MpMetadata metadata, MetricDescriptor metricInfo, MeterRegistry registry,
                String scope) {
            if (gauge == null || metadata.cleanDirtyMetadata()) {

                ThreadLocal<Boolean> threadLocal = SharedMetricRegistries.getThreadLocal(scope);
                threadLocal.set(true);

                gauge = io.micrometer.core.instrument.Gauge.builder(metricInfo.name(), (Supplier<Number>) supplier)
                        .description(metadata.getDescription())
                        .tags(metricInfo.tags())
                        .tags("scope", scope)
                        .baseUnit(metadata.getUnit())
                        .strongReference(true).register(Metrics.globalRegistry);
                threadLocal.set(false);
            }

            return this;
        }

        @Override
        public Meter getMeter() {
            return gauge;
        }

        @Override
        public T getValue() {
            return supplier.get();
        }

        @Override
        public MetricType getType() {
            return MetricType.GAUGE;
        }
    }
}
