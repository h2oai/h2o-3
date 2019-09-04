package ai.h2o.automl;

import hex.Model;
import water.Iced;
import water.Job;

public abstract class TrainingStep<M extends Model> extends Iced<TrainingStep> {

    String _id;

    protected TrainingStep(String id) {
        _id = id;
    }

    protected abstract Job makeJob();
}
