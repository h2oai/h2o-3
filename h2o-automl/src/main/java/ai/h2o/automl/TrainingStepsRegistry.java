package ai.h2o.automl;

import ai.h2o.automl.EventLogEntry.Stage;
import water.Iced;
import water.nbhm.NonBlockingHashMap;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ServiceLoader;

public class TrainingStepsRegistry extends Iced<TrainingStepsRegistry> {

    private static NonBlockingHashMap<String, Class<TrainingSteps>> stepsByName = new NonBlockingHashMap<>();

    static {
        ServiceLoader<TrainingStepsProvider> trainingStepsProviders = ServiceLoader.load(TrainingStepsProvider.class);
        for (TrainingStepsProvider provider : trainingStepsProviders) {
            stepsByName.put(provider.getName(), provider.getStepsClass());
        }
    }

    private StepDefinition[] _defaultTrainingPlan;

    public TrainingStepsRegistry(StepDefinition[] defaultTrainingPlan) {
        _defaultTrainingPlan = defaultTrainingPlan;
    }

    public TrainingStep[] getOrderedSteps(AutoML aml) {
        return getOrderedSteps(aml, _defaultTrainingPlan);
    }

    public TrainingStep[] getOrderedSteps(AutoML aml, StepDefinition[] trainingPlan) {
        aml.eventLog().info(Stage.Workflow, "Loading execution steps "+Arrays.toString(trainingPlan));
        List<TrainingStep> orderedSteps = new ArrayList<>();
        for (StepDefinition def : trainingPlan) {
            Class<TrainingSteps> clazz = stepsByName.get(def._name);
            if (clazz == null) {
                aml.eventLog().warn(Stage.Workflow, "Could not find TrainingSteps class for "+def._name);
                continue;
            }
            try {
                TrainingSteps steps = clazz.getConstructor(AutoML.class).newInstance(aml);
                if (def._alias != null) {
                    orderedSteps.addAll(Arrays.asList(steps.getSteps(def._alias)));
                } else if (def._steps != null) {
                    orderedSteps.addAll(Arrays.asList(steps.getSteps(def._steps)));
                }
            } catch (NoSuchMethodException|IllegalAccessException|InstantiationException|InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }
        return orderedSteps.toArray(new TrainingStep[0]);
    }

}
