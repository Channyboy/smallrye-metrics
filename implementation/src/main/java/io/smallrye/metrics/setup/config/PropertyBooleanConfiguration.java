package io.smallrye.metrics.setup.config;

public abstract class PropertyBooleanConfiguration extends PropertyConfiguration {
    protected boolean isEnabled = false;

    @Override
    public String toString() {
        return String.format(this.getClass().getName() + "metric name: [%s]; isEnabled: [%s]", metricName,
                isEnabled);
    }

    public boolean getIsEnabled() {
        return isEnabled;
    }
}
