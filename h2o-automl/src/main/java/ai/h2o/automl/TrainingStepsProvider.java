package ai.h2o.automl;

/**
 * A simple class used by service discovery to register new {@link TrainingSteps} implementations.
 */
public interface TrainingStepsProvider<T extends TrainingSteps> {
    /**
     * @return the name of this provider: must be unique among all registered providers.
     */
    String getName();

    /**
     *
     * @param aml the {@link AutoML} instance needed to build the {@link TrainingSteps}
     * @return an instance of {@link TrainingSteps} listing all the various AutoML steps executable with this provider name.
     */
    T newInstance(AutoML aml);
}
