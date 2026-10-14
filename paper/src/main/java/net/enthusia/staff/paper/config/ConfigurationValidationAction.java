package net.enthusia.staff.paper.config;

@FunctionalInterface
public interface ConfigurationValidationAction {
    ConfigurationValidationReport validate();
}
