package water.util;;

import hex.ModelBuilder;
import hex.ModelCategory;

import hex.ModelMetrics;
import org.junit.Ignore;
import water.Futures;
import water.rapids.Merge;

@Ignore
public class Sort extends ModelBuilder<SortModel, SortModel.SortParameters, SortModel.SortOutput> {

    @Override
    public ModelCategory[] can_build() {
        return new ModelCategory[]{ModelCategory.Unknown,};
    }

    @Override
    public BuilderVisibility builderVisibility() {
        return BuilderVisibility.Experimental;
    }

    @Override
    public boolean isSupervised() {
        return false;
    }

    public Sort(SortModel.SortParameters parms) {
        super(parms);
        init(false);
    }

    @Override
    protected SortDriver trainModelImpl() {
        return new SortDriver();
    }

    @Override
    public void init(boolean expensive) {
        super.init(expensive);
    }

    @Override
    protected int nModelsInParallel(int folds) {
        return _parms._nModelsInParallel;
    }

    @Override
    protected ModelMetrics.MetricBuilder makeCVMetricBuilder(ModelBuilder<SortModel, SortModel.SortParameters, SortModel.SortOutput> cvModelBuilder, Futures fs) {
        return null;
    }

    @Override
    public void cv_makeAggregateModelMetrics(ModelMetrics.MetricBuilder[] mbs) {
        // do nothing
    }

    @Override
    public void cv_mainModelScores(int N, ModelMetrics.MetricBuilder[] mbs, ModelBuilder<SortModel, SortModel.SortParameters, SortModel.SortOutput>[] cvModelBuilders) {
        // we don't have any real metrics
        // just clean-up the CV models
        for (ModelBuilder<SortModel, SortModel.SortParameters, SortModel.SortOutput> mb : cvModelBuilders) {
            SortModel m = mb.dest().get();
            if (m != null)
                m.delete();
        }
    }

    private class SortDriver extends Driver {
        @Override
        public void computeImpl() {
            SortModel model = null;
            try {
                init(true);

                model = new SortModel(dest(), _parms, new SortModel.SortOutput(Sort.this));
                model.delete_and_lock(_job);

                Merge.sort(train(), ArrayUtils.seq(0, train().numCols()-1)).delete();
            } finally {
                if (model != null) {
                    model.unlock(_job);
                }
            }
        }
    }

}
