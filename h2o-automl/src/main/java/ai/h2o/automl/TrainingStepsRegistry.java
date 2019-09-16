package ai.h2o.automl;

import ai.h2o.automl.EventLogEntry.Stage;
import ai.h2o.automl.StepDefinition.Step;
import water.Iced;
import water.nbhm.NonBlockingHashMap;
import water.util.ArrayUtils;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The registry responsible for loading all {@link TrainingStepsProvider} using service discovery,
 * and providing the list of {@link TrainingStep} to execute.
 */
public class TrainingStepsRegistry extends Iced<TrainingStepsRegistry> {

    static final NonBlockingHashMap<String, Class<TrainingSteps>> stepsByName = new NonBlockingHashMap<>();

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

    /**
     * @param aml the AutoML instance responsible to execute the {@link TrainingStep}s.
     * @return the list of {@link TrainingStep}s to execute according to the default training plan.
     */
    public TrainingStep[] getOrderedSteps(AutoML aml) {
        return getOrderedSteps(aml, _defaultTrainingPlan);
    }


    /**
     * @param aml the AutoML instance responsible to execute the {@link TrainingStep}s.
     * @return the list of {@link TrainingStep}s to execute according to the given training plan.
     */
    public TrainingStep[] getOrderedSteps(AutoML aml, StepDefinition[] trainingPlan) {
        aml.eventLog().info(Stage.Workflow, "Loading execution steps "+Arrays.toString(trainingPlan));
        List<TrainingStep> orderedSteps = new ArrayList<>();
        for (StepDefinition def : trainingPlan) {
            Class<TrainingSteps> clazz = stepsByName.get(def._name);
            if (clazz == null) {
                throw new IllegalArgumentException("Missing provider for training steps '"+def._name+"'");
            }
            try {
                TrainingSteps steps = clazz.getConstructor(AutoML.class).newInstance(aml);
                TrainingStep[] toAdd = null;
                if (def._alias != null) {
                    toAdd = steps.getSteps(def._alias);
                } else if (def._steps != null) {
                    toAdd = steps.getSteps(def._steps);
                    if (toAdd.length < def._steps.length) {
                        List<String> toAddIds = Stream.of(toAdd).map(s -> s._id).collect(Collectors.toList());
                        Stream.of(def._steps)
                                .filter(s -> !toAddIds.contains(s._id))
                                .forEach(s -> aml.eventLog().warn(Stage.Workflow,
                                        "Step '"+s._id+"' not defined in provider '"+def._name+"': skipping it."));
                    }
                }
                if (toAdd != null) {
                    for (TrainingStep ts : toAdd) {
                        ts._fromDef = def;
                    }
                    orderedSteps.addAll(Arrays.asList(toAdd));
                }
            } catch (NoSuchMethodException|IllegalAccessException|InstantiationException|InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }
        return orderedSteps.toArray(new TrainingStep[0]);
    }

    public StepDefinition[] createExecutionPlanFromSteps(TrainingStep[] steps) {
        List<StepDefinition> definitions = new ArrayList<>();
        for (TrainingStep step : steps) {
            Step stepDesc = new Step(step._id, step._weight);
            if (definitions.size() > 0) {
                StepDefinition lastDef = definitions.get(definitions.size() - 1);
                if (lastDef._name.equals(step._fromDef._name)) {
                    lastDef._steps = ArrayUtils.append(lastDef._steps, stepDesc);
                    continue;
                }
            }
            definitions.add(new StepDefinition(step._fromDef._name, new Step[]{stepDesc}));
        }
        return definitions.toArray(new StepDefinition[0]);
    }

}
