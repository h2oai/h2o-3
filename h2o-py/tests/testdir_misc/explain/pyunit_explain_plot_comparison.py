import os
import sys

sys.path.insert(1, os.path.join("..", "..", ".."))
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot

import h2o
from tests import pyunit_utils
from h2o.estimators import H2OGradientBoostingEstimator, H2OGeneralizedLinearEstimator

# Regression-test the *content* of H2O explanation plots against committed baselines.
#
# The plotted data (partial dependence, ICE, variable importances, residuals, model correlation) is computed
# server-side in the JVM and merely fetched by the Python client, so a content snapshot is identical across the whole
# Python / numpy / matplotlib version matrix. Models are pure-JVM algos (GBM, GLM) trained with a fixed seed and no
# subsampling so the snapshot is fully deterministic; this is what lets a single baseline be compared on every matrix
# entry. See pyunit_utils.assert_plot_matches_baseline (set H2O_REGEN_PLOT_BASELINES=1 to (re)generate baselines).

BASELINE_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "baselines")


def _baseline(name):
    return os.path.join(BASELINE_DIR, name + ".json")


def _train():
    train = h2o.import_file(pyunit_utils.locate("smalldata/prostate/prostate.csv"))
    train["RACE"] = train["RACE"].asfactor()
    y = "VOL"  # regression target -> enables residual_analysis / pd / ice
    x = ["AGE", "RACE", "DPROS", "DCAPS", "PSA", "GLEASON"]
    # Fixed model_id keeps plot titles / heatmap labels (which embed the id) stable across runs and the version matrix.
    gbm = H2OGradientBoostingEstimator(model_id="gbm_explain_cmp", seed=1234, ntrees=15, max_depth=4,
                                       sample_rate=1.0, col_sample_rate=1.0)
    gbm.train(x=x, y=y, training_frame=train)
    gbm2 = H2OGradientBoostingEstimator(model_id="gbm2_explain_cmp", seed=1234, ntrees=15, max_depth=3,
                                        sample_rate=1.0, col_sample_rate=1.0)
    gbm2.train(x=x, y=y, training_frame=train)
    glm = H2OGeneralizedLinearEstimator(model_id="glm_explain_cmp", seed=1234)
    glm.train(x=x, y=y, training_frame=train)
    return train, gbm, gbm2, glm, x


def test_explain_plot_comparison():
    train, gbm, gbm2, glm, x = _train()
    models = [gbm, gbm2, glm]

    # Compare every plot first, then assert once -- this renders the whole set into a single HTML report on failure
    # (instead of aborting at the first mismatch). Clustering on the heatmaps reorders rows/cols via scipy linkage,
    # whose ordering can differ between scipy versions, so it is disabled to keep the snapshot stable across the matrix.
    cmp = pyunit_utils.PlotComparator()
    cmp.check(gbm.pd_plot(train, "PSA"), _baseline("pd_plot_psa"))
    cmp.check(gbm.ice_plot(train, "PSA"), _baseline("ice_plot_psa"))
    cmp.check(gbm.residual_analysis_plot(train), _baseline("residual_analysis"))
    cmp.check(gbm.learning_curve_plot(), _baseline("learning_curve"))
    cmp.check(gbm.varimp_plot(server=True), _baseline("varimp_plot"))
    cmp.check(h2o.model_correlation_heatmap(models, train, cluster_models=False),
              _baseline("model_correlation_heatmap"))
    cmp.check(h2o.varimp_heatmap(models, cluster=False), _baseline("varimp_heatmap"))

    # SHAP plots. jitter=0 removes the random beeswarm y-offset and samples (default 1000) exceeds the row count, so
    # there is no random subsampling -> the scatter offsets are the JVM-computed contributions and are deterministic.
    # shap_explain_row_plot defaults to a barplot of a single row's contributions, also deterministic.
    cmp.check(gbm.shap_summary_plot(train, jitter=0), _baseline("shap_summary"))
    cmp.check(gbm.shap_explain_row_plot(train, row_index=0), _baseline("shap_explain_row"))
    cmp.assert_all_match("explain_plots")


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_explain_plot_comparison)
else:
    test_explain_plot_comparison()
