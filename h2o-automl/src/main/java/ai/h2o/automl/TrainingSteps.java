package ai.h2o.automl;

import ai.h2o.automl.StepDefinition.Alias;
import ai.h2o.automl.StepDefinition.Step;
import water.Iced;
import water.Key;
import water.util.ArrayUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public abstract class TrainingSteps extends Iced<TrainingSteps> {

    private transient AutoML _aml;
    private Key<AutoML> _amlKey;

    public TrainingSteps(AutoML autoML) {
        _aml = autoML;
        _amlKey = autoML._key;
    }

    protected AutoML aml() {
        return _aml == null ? (_aml = _amlKey.get()) : _aml;
    }

    Optional<TrainingStep> getStep(String id) {
        return Stream.of(getAllSteps())
                .filter(step -> step._id.equals(id))
                .findFirst();
    }

    TrainingStep[] getSteps(Step[] steps) {
        List<TrainingStep> tSteps = new ArrayList<>();
        for (Step step : steps) {
            getStep(step._id).ifPresent(tStep -> {
                if (step._weight != Step.DEFAULT_WEIGHT) {
                    tStep._weight = step._weight;  // override default weight
                }
                tSteps.add(tStep);
            });
        }
        return tSteps.toArray(new TrainingStep[0]);
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
