package io.smallrye.metrics;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.metrics.MetricRegistry;

import io.micrometer.core.instrument.Metrics;
import io.smallrye.metrics.exporters.Exporter;
import io.smallrye.metrics.exporters.PrometheusMetricsExporter;
import io.smallrye.metrics.legacyapi.LegacyMetricRegistryAdapter;

@ApplicationScoped
public class MetricsRequestHandler {

    private static final String CLASS_NAME = MetricsRequestHandler.class.getName();
    private static final Logger LOGGER = Logger.getLogger(CLASS_NAME);

    private static final Map<String, String> corsHeaders;
    private static final String TEXT_PLAIN = "text/plain";
    private static final String STAR_STAR = "*/*";
    private static final String SCOPE_PARAM_KEY = "scope";
    private static final String NAME_PARAM_KEY = "name";

    private static final String FQ_PROMETHEUSCONFIG_PATH = "io.micrometer.prometheus.PrometheusConfig";

    static {
        corsHeaders = new HashMap<>();
        corsHeaders.put("Access-Control-Allow-Origin", "*");
        corsHeaders.put("Access-Control-Allow-Headers", "origin, content-type, accept, authorization");
        corsHeaders.put("Access-Control-Allow-Credentials", "true");
        corsHeaders.put("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD");
    }

    /**
     * @param requestPath e.g. request.getRequestURI for an HttpServlet
     * @param method http method (GET, POST, etc)
     * @param acceptHeaders accepted content types
     * @param parameterMap Map containing query parameters and values
     * @param responder a method that returns a response to the caller. See
     *        {@link Responder}
     * @throws IOException rethrows IOException if thrown by the responder
     *
     *         You can find example usage in the tests, in
     *         io.smallrye.metrics.tck.rest.MetricsHttpServlet
     */
    public void handleRequest(String requestPath, String method, Stream<String> acceptHeaders,
            Map<String, String[]> parameterMap, Responder responder) throws IOException {
        handleRequest(requestPath, "/metrics", method, acceptHeaders, parameterMap, responder);
    }

    /**
     *
     * @param requestPath e.g. request.getRequestURI for an HttpServlet
     * @param contextRoot the root at which Metrics are exposed, usually
     *        "/metrics"
     * @param method http method (GET, POST, etc)
     * @param acceptHeaders accepted content types
     * @param parameterMap Map containing query parameters and values
     * @param responder a method that returns a response to the caller. See
     *        {@link Responder}
     *
     * @throws IOException rethrows IOException if thrown by the responder
     *
     *         You can find example usage in the tests, in
     *         io.smallrye.metrics.tck.rest.MetricsHttpServlet
     */
    public void handleRequest(String requestPath, String contextRoot, String method, Stream<String> acceptHeaders,
            Map<String, String[]> parameterMap, Responder responder) throws IOException {

        final String METHOD_NAME = "handleRequest";

        Exporter exporter = obtainExporter(method, acceptHeaders, responder);
        if (exporter == null) {
            return;
        }

        if (!requestPath.startsWith(contextRoot)) {
            responder.respondWith(500,
                    "The expected context root of metrics is " + contextRoot
                            + ", but a request with a different path was routed to MetricsRequestHandler",
                    Collections.emptyMap());
            return;
        }

        String pathAfterContextRoot = requestPath.substring(contextRoot.length());
        /*
         * Allow user to request with "/metrics/" -- maybe not? Return 404 if request
         * path is more than just /metrics or /metrics/ Bad request!
         */
        if (pathAfterContextRoot.length() != 0 && !pathAfterContextRoot.equals("/")) {
            LOGGER.logp(Level.WARNING, CLASS_NAME, METHOD_NAME,
                    "The expected requests are /metrics, /metric?scope=<scope>, /metric?name=<name> or /metrics?scope=<scope>&name=<name>.");
            responder.respondWith(404, "The expected requests are /metrics, /metric?scope=<scope>"
                    + ", /metric?name=<name> or /metrics?scope=<scope>&name=<name>", Collections.emptyMap());
            return;
        }

        String scope = null;
        String metricName = null;
        /*
         * Retrieve first scope value
         */
        String[] scopeParameters = parameterMap.get(SCOPE_PARAM_KEY);
        if (scopeParameters != null && scopeParameters.length != 0) {
            scope = scopeParameters[0];

            if (scopeParameters.length > 1) {

                LOGGER.logp(Level.WARNING, CLASS_NAME, METHOD_NAME,
                        "More than one scope was detected. The first scope value \"{0}\" will be used.", scope);
            }

            /*
             * 404 if scope does not exist.
             */
            if (!SharedMetricRegistries.doesScopeExist(scope)) {
                LOGGER.logp(Level.WARNING, CLASS_NAME, METHOD_NAME, "Scope \"{0}\" not found.", scope);
                responder.respondWith(404, "Scope " + scope + " not found", Collections.emptyMap());
                return;
            }

        }

        /*
         * Retrieve first name value
         */
        String[] NameParameters = parameterMap.get(NAME_PARAM_KEY);
        if (NameParameters != null && NameParameters.length != 0) {
            metricName = NameParameters[0];
            if (NameParameters.length > 1) {
                LOGGER.logp(Level.WARNING, CLASS_NAME, METHOD_NAME,
                        "More than one metric name was detected. The first metric name \"{0}\" will be used.", metricName);
            }
        }

        String output = null;
        /*
         * All Metrics
         */
        if (scope == null && metricName == null) {
            LOGGER.logp(Level.FINEST, CLASS_NAME, METHOD_NAME, "Exporting all metrics.");
            output = exporter.exportAllScopes();
        }

        /*
         * Single Scope
         */
        else if (scope != null && metricName == null) {

            MetricRegistry reg = SharedMetricRegistries.getOrCreate(scope);

            // Cast to LegacyMetricRegistryAdapter and check that registry contains meters
            if (reg instanceof LegacyMetricRegistryAdapter
                    && ((LegacyMetricRegistryAdapter) reg).getPrometheusMeterRegistry().getMeters().size() != 0) {
                LOGGER.logp(Level.FINEST, CLASS_NAME, METHOD_NAME, "Exporting all metrics in scope \"{0}\".", scope);
                output = exporter.exportOneScope(scope);
            } else {
                LOGGER.logp(Level.WARNING, CLASS_NAME, METHOD_NAME, "No data in scope \"{0}\".", scope);
                responder.respondWith(204, "No data in scope " + scope, Collections.emptyMap());
                return;
            }

        }
        /*
         * Specific metric in a scope
         */
        else if (scope != null && metricName != null) {

            MetricRegistry registry = SharedMetricRegistries.getOrCreate(scope);

            if (registry instanceof LegacyMetricRegistryAdapter && ((LegacyMetricRegistryAdapter) registry)
                    .getPrometheusMeterRegistry().find(metricName).meters().size() != 0) {
                LOGGER.logp(Level.FINEST, CLASS_NAME, METHOD_NAME, "Exporting metric \"{0}\" from scope \"{1}\".",
                        new String[] { metricName, scope });

                output = exporter.exportMetricsByName(scope, metricName);
            } else {
                LOGGER.logp(Level.WARNING, CLASS_NAME, METHOD_NAME, "Metric \"{0}\" not found in scope \"{1}\".",
                        new String[] { metricName, scope });
                responder.respondWith(404, "Metric " + metricName + " not found in scope " + scope,
                        Collections.emptyMap());
                return;
            }

        }
        /*
         * Specific metric ACROSS scopes
         */
        else if (scope == null && metricName != null) {
            LOGGER.logp(Level.FINEST, CLASS_NAME, METHOD_NAME, "Exporting metric \"{0}\" across scopes.", metricName);
            output = exporter.exportOneMetricAcrossScopes(metricName);

            if (output == null || output.isEmpty() || output.length() == 0) {
                LOGGER.logp(Level.WARNING, CLASS_NAME, METHOD_NAME, "Metric \"{0}\" not found in any scope.", metricName);
                responder.respondWith(404, "Metric " + metricName + " not found in any scope  ",
                        Collections.emptyMap());
                return;
            }
        }
        /*
         * Something went wrong :(
         */
        else {
            LOGGER.logp(Level.WARNING, CLASS_NAME, METHOD_NAME,
                    "The expected requests are /metrics, /metric?scope=<scope>, /metric?name=<name> or /metrics?scope=<scope>&name=<name>");
            responder.respondWith(404, "The expected requests are /metrics, /metric?scope=<scope>"
                    + ", /metric?name=<name> or /metrics?scope=<scope>&name=<name>", Collections.emptyMap());
            return;
        }

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", exporter.getContentType());
        headers.put("Access-Control-Max-Age", "1209600");
        headers.putAll(corsHeaders);

        responder.respondWith(200, output, headers);
    }

    /**
     * Determine which exporter we want.
     *
     * @param method http method (GET, POST, etc)
     * @param acceptHeaders accepted content types
     * @param responder the responder to use if an error occurs
     * @return An exporter instance. If an exporter cannot be obtained for some
     *         reason, this method will use the responder to inform the user and
     *         will return null.
     */
    private Exporter obtainExporter(String method, Stream<String> acceptHeaders, Responder responder)
            throws IOException {

        final String METHOD_NAME = "obtainExporter";

        /*
         * Only support GET ... and we will only return Prometheus Exporter OpenMetrics
         * Exporting to be supported in the future.
         */

        if (!method.equals("GET")) {
            responder.respondWith(405, "Only GET method is accepted.", Collections.emptyMap());
            return null;
        } else if (acceptHeaders == null) {
            // Use PrometheusMetricsExporter
            return (isPrometheusLibraryLoaded(responder)) ? new PrometheusMetricsExporter() : null;

        } else {
            // Header can look like "text/plain, */*"
            Optional<String> mt = getBestMatchingMediaType(acceptHeaders);
            if (mt.isPresent()) {
                String mediaType = mt.get();

                return (isPrometheusLibraryLoaded(responder)) ? new PrometheusMetricsExporter() : null;

            } else {
                LOGGER.logp(Level.WARNING, CLASS_NAME, METHOD_NAME,
                        "Couldn't determine a suitable media type for the given Accept header.");
                responder.respondWith(406, "Couldn't determine a suitable media type for the given Accept header.",
                        Collections.emptyMap());
                return null;
            }
        }
    }

    /**
     * Check if we can load a class from the Micrometer Prometheus Library
     * 
     * @param responder
     * @return true if we were able to load a class from the Micrometer Prometheus library
     * @throws IOException
     */
    private boolean isPrometheusLibraryLoaded(Responder responder) throws IOException {
        final String METHOD_NAME = "isPrometheusLibraryLoaded";
        try {
            Class.forName(FQ_PROMETHEUSCONFIG_PATH);
            return true;
        } catch (Exception e) {
            LOGGER.logp(Level.WARNING, CLASS_NAME, METHOD_NAME, "The /metrics endpoint is not supported.");
            responder.respondWith(501,
                    "The /metrics endpoint is not supported.",
                    Collections.emptyMap());
            return false;
        }

    }

    /**
     * Find the best matching media type (i.e. the one with highest prio. If two
     * have the same prio, and one is text/plain, then use this. Return empty if no
     * match can be found
     *
     * @param acceptHeaders A steam of Accept: headers
     * @return best media type as string or null if no match
     */
    // This should be available somewhere in http handling world
    Optional<String> getBestMatchingMediaType(Stream<String> acceptHeaders) {

        List<WTTuple> tupleList = new ArrayList<>();

        // Dissect the headers into type and prioritize and put them in a list
        acceptHeaders.forEach(h -> {
            String[] headers = h.split(",");
            for (String header : headers) {
                String[] parts = header.split(";");
                float prio = 1.0f;
                if (parts.length > 1) {
                    for (String x : parts) {
                        if (x.startsWith("q=")) {
                            prio = Float.parseFloat(x.substring(2));
                        }
                    }
                }
                WTTuple t = new WTTuple(prio, parts[0]);
                tupleList.add(t);
            }
        });

        if (tupleList.isEmpty()) {
            return Optional.of(TEXT_PLAIN);
        }

        WTTuple bestMatchTuple = new WTTuple(-1, null);

        // Iterate over the list and find the best match
        for (WTTuple tuple : tupleList) {
            if (!isKnownMediaType(tuple)) {
                continue;
            }
            if (tuple.weight > bestMatchTuple.weight) {
                bestMatchTuple = tuple;
            } else if (tuple.weight == bestMatchTuple.weight) {
                if (!bestMatchTuple.type.equals(TEXT_PLAIN) && tuple.type.equals(TEXT_PLAIN)) {
                    bestMatchTuple = tuple;
                }
            }
        }

        // We found a match. Now if this is */* return text/plain. Otherwise return the
        // type found
        if (bestMatchTuple.weight > 0) {
            return bestMatchTuple.type.equals(STAR_STAR) ? Optional.of(TEXT_PLAIN) : Optional.of(bestMatchTuple.type);
        }

        // No match
        return Optional.empty();
    }

    private boolean isKnownMediaType(WTTuple tuple) {
        return tuple.type.equals(TEXT_PLAIN) || tuple.type.equals(STAR_STAR);
    }

    /**
     * Responder is used by MetricsRequestHandler to return a response to the caller
     */
    public interface Responder {
        /**
         * @param status http status code
         * @param message message to be returned
         * @param headers a map of http headers
         * @throws IOException this method may be implemented to throw an IOException.
         *         In such case the
         *         {@link MetricsRequestHandler#handleRequest(String, String, Stream, Map, Responder)}
         *         will propagate the exception
         */
        void respondWith(int status, String message, Map<String, String> headers) throws IOException;
    }

    /**
     * Helper object for media type matching
     */
    private static class WTTuple {
        float weight;
        String type;

        WTTuple(float weight, String type) {
            this.weight = weight;
            this.type = type;
        }
    }

}
