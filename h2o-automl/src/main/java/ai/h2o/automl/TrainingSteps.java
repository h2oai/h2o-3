package ai.h2o.automl;

import ai.h2o.automl.StepDefinition.Alias;
import water.Iced;
import water.util.ArrayUtils;

import java.util.Optional;
import java.util.stream.Stream;

public abstract class TrainingSteps extends Iced<TrainingSteps> {

    protected AutoML _aml;

    public TrainingSteps(AutoML autoML) {
        _aml = autoML;
    }

    Optional<TrainingStep> getStep(String id) {
        return Stream.of(getAllSteps())
                .filter(step -> step._id.equals(id))
                .findFirst();
    }

    TrainingStep[] getSteps(String[] ids) {
        return Stream.of(ids)
                .map(this::getStep)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toArray(TrainingStep[]::new);
    }

    TrainingStep[] getSteps(Alias alias) {
        switch (alias) {
            case all:
                return getAllSteps();
            case defaults:
                return getDefaultModels();
            case grids:
                return getGrids();
            default:
                return new TrainingStep[0];
        }
    }

    TrainingStep[] getAllSteps() {
        return ArrayUtils.append(getDefaultModels(), getGrids());
    }

    protected TrainingStep[] getDefaultModels() { return new TrainingStep[0]; }
    protected TrainingStep[] getGrids() { return new TrainingStep[0]; }

}
