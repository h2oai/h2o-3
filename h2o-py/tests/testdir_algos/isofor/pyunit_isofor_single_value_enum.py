import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils
from h2o.estimators.isolation_forest import H2OIsolationForestEstimator


def isolation_forest_single_value_enum():
    """
    GH-16460: H2O fails to predict on dataset with ENUM column containing only one unique value.

    This test creates a dataset with a categorical column that has only one unique value
    plus NAs, trains an Isolation Forest, and verifies that prediction works correctly.
    """
    print("Isolation Forest: Single Value Enum Test (GH-16460)")

    train = h2o.H2OFrame({
        "num1": list(range(1, 21)),
        "num2": [0.1, 0.5, 0.3, 0.7, 0.2, 0.9, 0.4, 0.6, 0.8, 0.1,
                 0.3, 0.5, 0.7, 0.2, 0.4, 0.6, 0.8, 0.9, 0.1, 0.5],
    })
    train = train.cbind(h2o.H2OFrame(
        {"cat_single": [
            "alpha", "alpha", "alpha", "NaN", "alpha", "alpha",
            "NaN", "alpha", "alpha", "alpha",
            "NaN", "NaN", "alpha", "alpha", "NaN", "alpha",
            "alpha", "NaN", "alpha", "alpha"
        ]},
        column_types={"cat_single": "enum"},
        na_strings=["NaN"]
    ))

    if_model = H2OIsolationForestEstimator(ntrees=10, seed=42)
    if_model.train(training_frame=train)

    # This predict call fails with the bug (GH-16460)
    preds = if_model.predict(train)
    assert preds.nrow == train.nrow, \
        "Expected %d predictions, got %d" % (train.nrow, preds.nrow)

    print("PASS: Prediction with single-value enum column succeeded")


if __name__ == "__main__":
    pyunit_utils.standalone_test(isolation_forest_single_value_enum)
else:
    isolation_forest_single_value_enum()
