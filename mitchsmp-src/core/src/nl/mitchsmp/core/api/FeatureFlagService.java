package nl.mitchsmp.core.api;

import java.util.Map;

public interface FeatureFlagService {
    boolean isEnabled(String feature);

    void setEnabled(String feature, boolean enabled);

    Map<String, Boolean> all();

    String featureForCommand(String commandRoot);
}
