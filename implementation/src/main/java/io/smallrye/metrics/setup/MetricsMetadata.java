package io.smallrye.metrics.setup;

import static io.smallrye.metrics.legacyapi.TagsUtils.parseTagsAsArray;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Timed;

import io.micrometer.core.instrument.Tags;
import io.smallrye.metrics.OriginAndMetadata;
import io.smallrye.metrics.SharedMetricRegistries;
import io.smallrye.metrics.elementdesc.AnnotationInfo;
import io.smallrye.metrics.elementdesc.BeanInfo;
import io.smallrye.metrics.elementdesc.MemberInfo;
import io.smallrye.metrics.legacyapi.LegacyMetricRegistryAdapter;
import io.smallrye.metrics.legacyapi.interceptors.MetricResolver;

public class MetricsMetadata {

    private MetricsMetadata() {
    }

    public static List<MetricID> registerMetrics(MetricRegistry registry, MetricResolver resolver, BeanInfo bean,
            MemberInfo element) {

        MetricResolver.Of<Counted> counted = resolver.counted(bean, element);
        List<MetricID> metricIDs = new ArrayList<>();

        if (counted.isPresent()) {
            AnnotationInfo t = counted.metricAnnotation();

            registry = SharedMetricRegistries.getOrCreate(t.scope());

            Metadata metadata = getMetadata(element, counted.metricName(), t.unit(), t.description());
            Tag[] tags = parseTagsAsArray(t.tags());
            registry.counter(metadata, tags);

            Tag[] mpTagArray = resolveAppNameTag(registry, tags);

            MetricID metricID = new MetricID(metadata.getName(), mpTagArray);
            metricIDs.add(metricID);

            ((LegacyMetricRegistryAdapter) registry).getMemberToMetricMappings().addCounter(element, metricID);

        }

        MetricResolver.Of<Timed> timed = resolver.timed(bean, element);
        if (timed.isPresent()) {
            AnnotationInfo t = timed.metricAnnotation();

            registry = SharedMetricRegistries.getOrCreate(t.scope());

            Metadata metadata = getMetadata(element, timed.metricName(), t.unit(), t.description());
            Tag[] tags = parseTagsAsArray(t.tags());
            registry.timer(metadata, tags);

            Tag[] mpTagArray = resolveAppNameTag(registry, tags);

            MetricID metricID = new MetricID(metadata.getName(), mpTagArray);
            metricIDs.add(metricID);
            ((LegacyMetricRegistryAdapter) registry).getMemberToMetricMappings().addTimer(element, metricID);

        }

        return metricIDs;
    }

    // XXX: this was just to create a OriginAndMetadata.. is this needed?
    public static Metadata getMetadata(Object origin, String name, String unit, String description) {
        Metadata metadata = Metadata.builder().withName(name).withUnit(unit).withDescription(description).build();
        return new OriginAndMetadata(origin, metadata);
    }

    /*
     * TODO: Temporary, resolve the mp.metrics.appName tag if if available to
     * append to MembersToMetricMapping so that interceptors can find the annotated
     * metric Possibly remove MembersToMetricMapping in future, and directly query
     * metric/meter-registry.
     */
    private static Tag[] resolveAppNameTag(MetricRegistry registry, Tag... tags) {
        Tags mmTags = ((LegacyMetricRegistryAdapter) registry).withAppTags(tags);

        List<Tag> mpListTags = new ArrayList<Tag>();
        mmTags.forEach(tag -> {
            Tag mpTag = new Tag(tag.getKey(), tag.getValue());
            mpListTags.add(mpTag);
        });

        return mpListTags.toArray(new Tag[0]);
    }
}
