package ai.h2o.automl;

/**
 * A simple class used by service discovery to register new {@link ModelingSteps} implementations.
 */
public interface ModelingStepsProvider<T extends ModelingSteps> {
    /**
     * @return the name of this provider: must be unique among all registered providers.
     */
    String getName();

    /**
     * Creates an instance of {@link ModelingSteps} associated to this provider's name,
     * or returns null to fully skip this provider.
     *
     * @param aml the {@link AutoML} instance needed to build the {@link ModelingSteps}
     * @return an instance of {@link ModelingSteps} listing all the various AutoML steps executable with this provider name.
     */
    T newInstance(AutoML aml);
}
