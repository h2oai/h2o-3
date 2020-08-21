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

    Optional<ModelingStep> getStep(String id) {
        return Stream.of(getAllSteps())
                .filter(step -> step._id.equals(id))
                .findFirst();
    }

    ModelingStep[] getSteps(Step[] steps) {
        List<ModelingStep> tSteps = new ArrayList<>();
        for (Step step : steps) {
            getStep(step._id).ifPresent(tStep -> {
                if (step._weight != Step.DEFAULT_WEIGHT) {
                    tStep._weight = step._weight;  // override default weight
                }
                tSteps.add(tStep);
            });
        }
        return tSteps.toArray(new ModelingStep[0]);
    }

    ModelingStep[] getSteps(Alias alias) {
        switch (alias) {
            case all:
                return getAllSteps();
            case defaults:
                return getDefaultModels();
            case grids:
                return getGrids();
            case exploitation:
                return getExploitation();
            default:
                return new ModelingStep[0];
        }
    }

    ModelingStep[] getAllSteps() {
        ModelingStep[] all = new ModelingStep[0];  // create a fresh array to avoid type issues in arraycopy
        all = ArrayUtils.append(all, getDefaultModels());
        all = ArrayUtils.append(all, getGrids());
        all = ArrayUtils.append(all, getExploitation());
        return all;
    }

    protected ModelingStep[] getDefaultModels() { return new ModelingStep[0]; }
    protected ModelingStep[] getGrids() { return new ModelingStep[0]; }
    protected ModelingStep[] getExploitation() { return new ModelingStep[0]; }

}
