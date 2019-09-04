package ai.h2o.automl;

import water.Iced;
import water.util.ArrayUtils;

import java.util.Optional;
import java.util.stream.Stream;

public abstract class TrainingSteps extends Iced<TrainingSteps> {
    // define selection logic, aliases and so on
    // defaults, grids, all

    Optional<TrainingStep> getStep(String id) {
        return Stream.of(getAllSteps()).filter(step -> step._id.equals(id)).findFirst();
    }

    TrainingStep[] getSteps(String alias) {
        switch (alias) {
            case "all":
            case "defaults":
                return getDefaultModels();
            case "grids":
                return getGrids();
            default:
                return new TrainingStep[0];
        }
    }

    TrainingStep[] getAllSteps() {
        return ArrayUtils.append(getDefaultModels(), getGrids());
    }

    protected TrainingStep[] getDefaultModels() { return new TrainingStep[0]; };
    protected TrainingStep[] getGrids() { return new TrainingStep[0]; };
}
