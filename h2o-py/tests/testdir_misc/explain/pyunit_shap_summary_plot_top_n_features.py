import os
import sys

sys.path.insert(1, os.path.join("..", "..", ".."))
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot
import h2o
from tests import pyunit_utils
from h2o.estimators import H2OGradientBoostingEstimator


def test_shap_summary_plot_top_n_features():
    """GH-16757: h2o.shap_summary_plot should show exactly top_n_features features."""
    train = h2o.upload_file(pyunit_utils.locate("smalldata/titanic/titanic_expanded.csv"))
    y = "fare"

    gbm = H2OGradientBoostingEstimator(seed=1234, ntrees=20)
    gbm.train(y=y, training_frame=train)

    n_model_features = len([c for c in train.columns if c != y])
    print("Number of features in model:", n_model_features)

    for top_n in [3, 5, 7, 10]:
        if top_n >= n_model_features:
            continue

        plt = gbm.shap_summary_plot(train, top_n_features=top_n)
        fig = plt.figure()
        ax = fig.gca()
        # Y-axis tick labels correspond to the features displayed
        ytick_labels = [t.get_text() for t in ax.get_yticklabels()]
        n_features_shown = len([l for l in ytick_labels if l.strip()])
        matplotlib.pyplot.close()

        print(f"Requested top_n_features={top_n}, got {n_features_shown} features in plot")
        assert n_features_shown == top_n, \
            f"top_n_features={top_n} should show exactly {top_n} features but showed {n_features_shown}"

    # Default (top_n_features=20) should not exceed 20
    plt_default = gbm.shap_summary_plot(train)
    fig_default = plt_default.figure()
    ax_default = fig_default.gca()
    ytick_labels_default = [t.get_text() for t in ax_default.get_yticklabels()]
    n_default = len([l for l in ytick_labels_default if l.strip()])
    matplotlib.pyplot.close()
    assert n_default <= 20, f"Default top_n_features=20 showed {n_default} features"


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_shap_summary_plot_top_n_features)
else:
    test_shap_summary_plot_top_n_features()