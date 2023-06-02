package ai.h2o.automl;

import ai.h2o.automl.events.EventLogEntry.Stage;
import ai.h2o.automl.StepDefinition.Alias;
import ai.h2o.automl.StepDefinition.Step;
import hex.Model;
import water.Iced;
import water.nbhm.NonBlockingHashMap;
import water.util.ArrayUtils;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The registry responsible for loading all {@link ModelingStepsProvider} using service discovery,
 * and providing the list of {@link ModelingStep} to execute.
 */
public class ModelingStepsRegistry extends Iced<ModelingStepsRegistry> {

    static final NonBlockingHashMap<String, ModelingStepsProvider> stepsByName = new NonBlockingHashMap<>();
    static final NonBlockingHashMap<String, ModelParametersProvider> parametersByName = new NonBlockingHashMap<>();

    static {
        ServiceLoader<ModelingStepsProvider> trainingStepsProviders = ServiceLoader.load(ModelingStepsProvider.class);
        for (ModelingStepsProvider provider : trainingStepsProviders) {
            registerProvider(provider);
        }
    }

    public static void registerProvider(ModelingStepsProvider provider) {
        stepsByName.put(provider.getName(), provider);
        if (provider instanceof ModelParametersProvider) {  // mainly for hardcoded providers in this module, that's why we can reuse the ModelingStepsProvider
            parametersByName.put(provider.getName(), (ModelParametersProvider)provider);
        }
    }

    public static Model.Parameters defaultParameters(String provider) {
        if (parametersByName.containsKey(provider)) {
            return parametersByName.get(provider).newDefaultParameters();
        }
        return null;
    }

    /**
     * @param aml the AutoML instance responsible to execute the {@link ModelingStep}s.
     * @return the list of {@link ModelingStep}s to execute according to the given modeling plan.
     */
    public ModelingStep[] getOrderedSteps(StepDefinition[] modelingPlan, AutoML aml) {
        modelingPlan = aml.selectModelingPlan(modelingPlan);
        aml.eventLog().info(Stage.Workflow, "Loading execution steps: "+Arrays.toString(modelingPlan));
        List<ModelingStep> orderedSteps = new ArrayList<>();
        for (StepDefinition def : modelingPlan) {
            ModelingSteps steps = aml.session().getModelingSteps(def._name);
            if (steps == null) continue;

            ModelingStep[] toAdd;
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
            } else { // if name, but no alias or steps, put them all by default (support for simple syntax)
                toAdd = steps.getSteps(Alias.all);
            }
            if (toAdd != null) {
                for (ModelingStep ts : toAdd) {
                    ts._fromDef = def;
                }
                orderedSteps.addAll(Arrays.asList(toAdd));
            }
        }
        return orderedSteps.stream()
                .filter(step -> step.getPriorityGroup() > 0 && step.getWeight() != 0) // negative weights can be used for registration only to be loaded by a dynamic step.
                .sorted(Comparator.comparingInt(step -> step._priorityGroup))
                .toArray(ModelingStep[]::new);
    }

    public StepDefinition[] createDefinitionPlanFromSteps(ModelingStep[] steps) {
        List<StepDefinition> definitions = new ArrayList<>();
        for (ModelingStep step : steps) {
            Step stepDesc = new Step(step._id, step._priorityGroup, step._weight);
            if (definitions.size() > 0) {
                StepDefinition lastDef = definitions.get(definitions.size() - 1);
                if (lastDef._name.equals(step._fromDef._name)) {
                    lastDef._steps = ArrayUtils.append(lastDef._steps, stepDesc);
                    continue;
                }
            }
            definitions.add(new StepDefinition(step._fromDef._name, stepDesc));
        }
        return definitions.toArray(new StepDefinition[0]);
    }

}
