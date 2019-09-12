package ai.h2o.automl;

import ai.h2o.automl.EventLogEntry.Stage;
import water.DKV;
import water.Iced;
import water.Key;
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

    private Key<AutoML> _amlKey;
    private StepDefinition[] _defaultTrainingPlan;

    public TrainingStepsRegistry(AutoML aml) {
        this(aml, new StepDefinition[0]);
    }

    public TrainingStepsRegistry(AutoML aml, StepDefinition[] defaultTrainingPlan) {
        _amlKey = aml._key;
        _defaultTrainingPlan = defaultTrainingPlan;
    }

    public TrainingStep[] getOrderedSteps() {
        return getOrderedSteps(_defaultTrainingPlan);
    }

    public TrainingStep[] getOrderedSteps(StepDefinition[] trainingPlan) {
        aml().eventLog().info(Stage.Workflow, "Loading execution steps "+Arrays.toString(trainingPlan));
        List<TrainingStep> orderedSteps = new ArrayList<>();
        for (StepDefinition step : trainingPlan) {
            Class<TrainingSteps> clazz = stepsByName.get(step._name);
            if (clazz == null) {
                aml().eventLog().warn(Stage.Workflow, "Could not find TrainingSteps class for "+step._name);
                continue;
            }
            try {
                TrainingSteps steps = clazz.getConstructor(AutoML.class).newInstance(aml());
                if (step._alias != null) {
                    orderedSteps.addAll(Arrays.asList(steps.getSteps(step._alias)));
                } else if (step._ids != null) {
                    orderedSteps.addAll(Arrays.asList(steps.getSteps(step._ids)));
                }
            } catch (NoSuchMethodException|IllegalAccessException|InstantiationException|InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }
        return orderedSteps.toArray(new TrainingStep[0]);
    }

    public final AutoML aml() {
        return DKV.getGet(_amlKey);
    }

}
