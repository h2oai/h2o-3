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
     * @return the class providing the actual steps: this class must extend {@link TrainingSteps}.
     */
    Class<T> getStepsClass();
}
