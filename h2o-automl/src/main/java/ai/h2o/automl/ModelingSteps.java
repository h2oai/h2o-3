package ai.h2o.automl;

import ai.h2o.automl.StepDefinition.Alias;
import ai.h2o.automl.StepDefinition.Step;
import water.Iced;
import water.util.ArrayUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public abstract class ModelingSteps extends Iced<ModelingSteps> {

    private transient AutoML _aml;

    public ModelingSteps(AutoML autoML) {
        _aml = autoML;
    }

    protected AutoML aml() {
        return _aml;
    }

    public Optional<ModelingStep> getStep(String id) {
        return Stream.of(getAllSteps())
                .map(step -> step._id.equals(id) ? Optional.of(step) 
                            : (Optional<ModelingStep>)step.getSubStep(id))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    protected ModelingStep[] getSteps(Step[] steps) {
        List<ModelingStep> tSteps = new ArrayList<>();
        for (Step step : steps) {
            getStep(step._id).ifPresent(tStep -> {
                if (step._weight != Step.DEFAULT_WEIGHT) {
                    tStep._weight = step._weight;  // override default weight
                }
                if (step._group!= Step.DEFAULT_GROUP) {
                    tStep._priorityGroup = step._group; // override default priority
                }
                tSteps.add(tStep);
            });
        }
        return tSteps.toArray(new ModelingStep[0]);
    }

    protected ModelingStep[] getSteps(Alias alias) {
        switch (alias) {
            case all:
                return getAllSteps();
            case defaults:
                return getDefaultModels();
            case grids:
                return getGrids();
            case optionals:
            case exploitation: // old misleading alias, kept for backwards compatibility
                return getOptionals();
            default:
                return new ModelingStep[0];
        }
    }

    protected ModelingStep[] getAllSteps() {
        ModelingStep[] all = new ModelingStep[0];  // create a fresh array to avoid type issues in arraycopy
        all = ArrayUtils.append(all, getDefaultModels());
        all = ArrayUtils.append(all, getGrids());
        all = ArrayUtils.append(all, getOptionals());
        return all;
    }

    /**
     * @return the list of all single model steps that should be executed by default when this provider is active.
     */
    protected ModelingStep[] getDefaultModels() { return new ModelingStep[0]; }

    /**
     * @return the list of all grid steps that should be executed by default when this provider is active.
     */
    protected ModelingStep[] getGrids() { return new ModelingStep[0]; }
    /**
     * @return the list of all steps that should be executed on-demand, i.e. requested by their id.
     */
    protected ModelingStep[] getOptionals() { return new ModelingStep[0]; }
    public abstract String getProvider();

    protected void cleanup() {}

}
