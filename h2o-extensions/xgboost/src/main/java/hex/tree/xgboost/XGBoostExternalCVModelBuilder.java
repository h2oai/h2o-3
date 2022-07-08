package hex.tree.xgboost;

import hex.CVModelBuilder;
import hex.ModelBuilder;
import hex.tree.xgboost.remote.SteamExecutorStarter;
import org.apache.log4j.Logger;
import water.Job;

import java.io.IOException;

public class XGBoostExternalCVModelBuilder extends CVModelBuilder {

    private static final Logger LOG = Logger.getLogger(XGBoostExternalCVModelBuilder.class);

    private final SteamExecutorStarter _starter;
    private boolean _initialized;

    public XGBoostExternalCVModelBuilder(
            Job job,
            ModelBuilder<?, ?, ?>[] modelBuilders,
            int parallelization,
            SteamExecutorStarter starter
    ) {
        super(job, modelBuilders, parallelization);
        _starter = starter;
    }

    @Override
    protected void prepare(ModelBuilder<?, ?, ?> m) {
        if (!_initialized) {
            XGBoost xgb = (XGBoost) m;
            // We try to the cluster start just one time before CV models are executed in parallel. This way
            // the CV models don't need to compete for who will succeed starting the cluster - the flow is more
            // predictable and easier to debug.
            try {
                prepareCluster(xgb);
            } catch (Exception e) { // ignore, give another chance to start in CV models (same as before this change)
                LOG.error("Failed to prepare an external XGBoost cluster, " +
                        "individual CV models will attempt to start the cluster again.", e);
            }
            _initialized = true;
        }
    }

    @SuppressWarnings("unchecked")
    void prepareCluster(XGBoost xgb) {
        LOG.info("Requesting external cluster for model " + xgb.dest());
        try {
            _starter.startCluster(xgb.dest(), getJob());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start external XGBoost cluster", e);
        }
        LOG.info("External cluster successfully initialized");
    }
    
}
