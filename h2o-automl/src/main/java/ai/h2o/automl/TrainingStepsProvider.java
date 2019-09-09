package ai.h2o.automl;

public interface TrainingStepsProvider<T extends TrainingSteps> {
    String getName();
    Class<T> getStepsClass();
}
