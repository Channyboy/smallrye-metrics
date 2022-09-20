package io.smallrye.metrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.inject.Inject;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.Timer;
import org.eclipse.microprofile.metrics.annotation.Metric;

import io.micrometer.core.instrument.Tags;
import io.smallrye.metrics.legacyapi.LegacyMetricRegistryAdapter;
import io.smallrye.metrics.legacyapi.LegacyMetricsExtension;
import io.smallrye.metrics.legacyapi.TagsUtils;
import io.smallrye.metrics.legacyapi.interceptors.MetricName;
import io.smallrye.metrics.legacyapi.interceptors.SeMetricName;

@ApplicationScoped
public class MetricProducer {

    MetricRegistry registry;

    MetricName metricName = new SeMetricName(Collections.emptySet());

    @Inject
    LegacyMetricsExtension metricExtension;

    /**
     * No-arg for CDI
     */
    public MetricProducer() {

    }

    /**
     * Used to create a MetricProducer with a provided LegacyMetricExtension which
     * would be typically provided by injection as seen above. This constructor is
     * for runtimes that may need to proxy the MetricProducer.
     * 
     * @param metricExtension a LegacyMetricsExtension object
     */
    public MetricProducer(LegacyMetricsExtension metricExtension) {
        this.metricExtension = metricExtension;
    }

    @Produces
    public <T extends Number> Gauge<T> getGauge(InjectionPoint ip) {
        // A forwarding Gauge must be returned as the Gauge creation happens when the
        // declaring bean gets instantiated and the corresponding Gauge can be injected
        // before which leads to producing a null value
        return () -> {
            // TODO: better error report when the gauge doesn't exist

            registry = SharedMetricRegistries.getOrCreate(getScope(ip));
            SortedMap<MetricID, Gauge> gauges = registry.getGauges();

            String name = metricName.of(ip);
            Tag[] tags = getTags(ip);

            // FIXME: Temporary, resolve the mp.metrics.appName tag if if available to
            // append to MembersToMetricMapping
            // so that interceptors can find the annotated metric
            // Possibly remove MembersToMetricMapping in future, and directly query
            // metric/meter-registry.

            Tags mmTags = ((LegacyMetricRegistryAdapter) registry).withAppTags(tags);

            List<Tag> mpListTags = new ArrayList<Tag>();
            mmTags.forEach(tag -> {
                Tag mpTag = new Tag(tag.getKey(), tag.getValue());
                mpListTags.add(mpTag);
            });

            Tag[] mpTagArray = mpListTags.toArray(new Tag[0]);

            MetricID gaugeId = new MetricID(name, mpTagArray);

            return ((Gauge<T>) gauges.get(gaugeId)).getValue();
        };
    }

    @Produces
    public Counter getCounter(InjectionPoint ip) {

        registry = SharedMetricRegistries.getOrCreate(getScope(ip));
        Metadata metadata = getMetadata(ip);
        Tag[] tags = getTags(ip);

        Counter counter = registry.counter(metadata, tags);
        

        if (registry instanceof LegacyMetricRegistryAdapter) {

            //FIXME: Temporary, resolve the mp.metrics.appName tag if if available to MetricID
            // MetricID will be added to metricID list in extension class.
            // Used during shutdown of CDI extension
            Tags mmTags = ((LegacyMetricRegistryAdapter) registry).withAppTags(tags);

            List<Tag> mpListTags = new ArrayList<Tag>();
            mmTags.forEach(tag -> {
                Tag mpTag = new Tag(tag.getKey(), tag.getValue());
                mpListTags.add(mpTag);
            });

            Tag[] mpTagArray = mpListTags.toArray(new Tag[0]);

            //add this CDI MetricID into MetricRegistry's MetricID list....
            MetricID metricID = new MetricID(metadata.getName(), mpTagArray);
            metricExtension.addMetricId(metricID);

        }         

        return counter;
    }

    @Produces
    public Timer getTimer(InjectionPoint ip) {

        registry = SharedMetricRegistries.getOrCreate(getScope(ip));

        Metadata metadata = getMetadata(ip);
        Tag[] tags = getTags(ip);

        Timer timer = registry.timer(metadata, tags);
        
        if (registry instanceof LegacyMetricRegistryAdapter) {

            //FIXME: Temporary, resolve the mp.metrics.appName tag if if available to MetricID
            // MetricID will be added to metricID list in extension class.
            // Used during shutdown of CDI extension
            Tags mmTags = ((LegacyMetricRegistryAdapter) registry).withAppTags(tags);

            List<Tag> mpListTags = new ArrayList<Tag>();
            mmTags.forEach(tag -> {
                Tag mpTag = new Tag(tag.getKey(), tag.getValue());
                mpListTags.add(mpTag);
            });

            Tag[] mpTagArray = mpListTags.toArray(new Tag[0]);

            //add this CDI MetricID into MetricRegistry's MetricID list....
            MetricID metricID = new MetricID(metadata.getName(), mpTagArray);
            metricExtension.addMetricId(metricID);

        }   
        return timer;
    }

    @Produces
    public Histogram getHistogram(InjectionPoint ip) {

        registry = SharedMetricRegistries.getOrCreate(getScope(ip));
        Metadata metadata = getMetadata(ip);
        Tag[] tags = getTags(ip);

        Histogram histogram = registry.histogram(metadata, tags);

        return histogram;
    }

    private Metadata getMetadata(InjectionPoint ip) {
        Metric metric = ip.getAnnotated().getAnnotation(Metric.class);
        Metadata metadata;
        if (metric != null) {
            Metadata actualMetadata = Metadata.builder().withName(metricName.of(ip)).withUnit(metric.unit())
                    .withDescription(metric.description()).build();
            metadata = new OriginAndMetadata(ip, actualMetadata);
        } else {
            Metadata actualMetadata = Metadata.builder().withName(metricName.of(ip)).withUnit(MetricUnits.NONE)
                    .withDescription("").build();
            metadata = new OriginAndMetadata(ip, actualMetadata);
        }

        return metadata;
    }

    private String getScope(InjectionPoint ip) {
        Metric metric = ip.getAnnotated().getAnnotation(Metric.class);
        if (metric != null) {
            return metric.scope();
        } else {
            return MetricRegistry.APPLICATION_SCOPE;
        }
    }

    private Tag[] getTags(InjectionPoint ip) {
        Metric metric = ip.getAnnotated().getAnnotation(Metric.class);
        if (metric != null && metric.tags().length > 0) {
            return TagsUtils.parseTagsAsArray(metric.tags());
        } else {
            return new Tag[0];
        }
    }

}
