package hex.isotonic;

import hex.ModelBuilder;
import hex.ModelCategory;
import hex.ModelMetrics;
import water.fvec.Frame;
import water.fvec.Vec;
import water.udf.CFuncRef;
import water.util.ArrayUtils;
import water.util.FrameUtils;
import water.util.TwoDimTable;
import water.util.VecUtils;

public class IsotonicRegression extends ModelBuilder<IsotonicRegressionModel, 
        IsotonicRegressionModel.IsotonicRegressionParameters, IsotonicRegressionModel.IsotonicRegressionOutput> {

    @Override
    public ModelCategory[] can_build() {
        return new ModelCategory[]{ModelCategory.Regression};
    }

    @Override
    public BuilderVisibility builderVisibility() {
        return BuilderVisibility.Experimental;
    }

    @Override
    public boolean isSupervised() {
        return true;
    }

    // for ModelBuilder registration
    public IsotonicRegression(boolean startup_once) {
        super(new IsotonicRegressionModel.IsotonicRegressionParameters(), startup_once);
    }

    public IsotonicRegression(IsotonicRegressionModel.IsotonicRegressionParameters parms) {
        super(parms);
        init(false);
    }

    @Override
    public void init(boolean expensive) {
        super.init(expensive);
        if (train() != null) {
            if (numFeatureCols() != 1) {
                error("_train",
                        "Training frame for Isotonic Regression can only have a single feature column, " +
                                "training frame columns: " + ArrayUtils.toStringQuotedElements(train().names()));
            }
            if (expensive) {
                Vec resp = response();
                if (resp != null && (resp.naCnt() > 0)) {
                    error("_response_column", "Isotonic Regression doesn't support NA values in response.");
                }
                if (numFeatureCols() == 1) {
                    Vec xVec = train().vec(0);
                    if (xVec != null && (xVec.naCnt() > 0)) {
                        error("_response_column",
                                "Isotonic Regression doesn't support NA values in feature column '" + train().name(0) + "'.");
                    }
                }
            }
        }
    }

    private int numFeatureCols() {
        return train().numCols() - (numSpecialCols() + 1 /*response*/);
    }

    @Override
    protected IsotonicRegressionDriver trainModelImpl() {
        return new IsotonicRegressionDriver();
    }

    private class IsotonicRegressionDriver extends Driver {
        @Override
        public void computeImpl() {
            IsotonicRegressionModel model = null;
            Frame thresholds = null;
            Vec weights = null;
            try {
                init(true);

                // The model to be built
                model = new IsotonicRegressionModel(dest(), _parms, 
                        new IsotonicRegressionModel.IsotonicRegressionOutput(IsotonicRegression.this));
                model.delete_and_lock(_job);

                Vec xVec = _train.vec(0);
                weights = hasWeightCol() ? _weights : _train.anyVec().makeCon(1.0);

                VecUtils.MinMaxTask minMax = VecUtils.findMinMax(xVec, weights);
                model._output._min_x = minMax._min;
                model._output._max_x = minMax._max;
                
                Frame fr = new Frame();
                fr.add("y", response());
                fr.add("X", xVec);
                fr.add("w", weights);

                thresholds = PoolAdjacentViolatorsDriver.runPAV(fr);

                model._output._nobs = weights.nzCnt();
                model._output._thresholds_y = FrameUtils.asDoubles(thresholds.vec(0));
                model._output._thresholds_x = FrameUtils.asDoubles(thresholds.vec(1));

                _job.update(1);
                model.update(_job);

                model.score(_parms.train(), null, CFuncRef.from(_parms._custom_metric_func)).delete();
                model._output._training_metrics = ModelMetrics.getFromDKV(model, _parms.train());

                if (valid() != null) {
                    _job.update(0,"Scoring validation frame");
                    model.score(_parms.valid(), null, CFuncRef.from(_parms._custom_metric_func)).delete();
                    model._output._validation_metrics = ModelMetrics.getFromDKV(model, _parms.valid());
                }

                model._output._model_summary = generateSummary(model._output);
                
                model.update(_job);
            } finally {
                if (model != null) {
                    model.unlock(_job);
                }
                if (thresholds != null) {
                    thresholds.delete();
                }
                if (weights != null && !hasWeightCol()) {
                    weights.remove();
                }
            }
        }
    }

    private TwoDimTable generateSummary(IsotonicRegressionModel.IsotonicRegressionOutput output) {
        String[] names = new String[]{"Number of Observations", "Number of Thresholds"};
        String[] types = new String[]{"long", "long"};
        String[] formats = new String[]{"%d", "%d"};
        TwoDimTable summary = new TwoDimTable("Isotonic Regression Model", "summary", new String[]{""}, names, types, formats, "");
        summary.set(0, 0, output._nobs);
        summary.set(0, 1, output._thresholds_x.length);
        return summary;
    }

    @Override
    public boolean haveMojo() { 
        return true; 
    }

}
