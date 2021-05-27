from __future__ import division
from __future__ import print_function
import sys

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator

import numpy as np
import pandas as pd
from sklearn.datasets import load_boston


def test_gam_cross_validation():
    np.random.seed(42)
    boston = load_boston()
    y = pd.Series(boston["target"], name="y")
    X = pd.DataFrame(boston["data"], columns=boston["feature_names"])  # shape: (506, 13)
    myweight = pd.Series(np.random.random_sample((len(y),)), name="myweight2")

    predictors = ['CRIM', 'AGE']
    gam_columns = ['CRIM']

    params = {
        "family": "gaussian",
        "gam_columns": gam_columns,
        'bs': len(gam_columns) * [0],
    }

    fold = pd.Series(np.append(np.zeros(253), np.ones(253)), dtype=int, index=y.index, name="fold_number")
    df0 = pd.concat([y, X, myweight, fold], axis=1)
    df = h2o.H2OFrame(python_obj=df0)

    for i in [0, 1]:
        mask = df["fold_number"] == i
        df_train = df[~mask, :]
        df_val = df[mask, :]

        model = H2OGeneralizedAdditiveEstimator(**params)
        model.train(
            x=predictors,
            y="y",
            weights_column="myweight2",
            training_frame=df_train,
        )

        print("Finished training for fold_number=", i, ", with validation-RMSE=", model.rmse(df_val))

    print("\nStarting training with API option fold_column=")
    model2 = H2OGeneralizedAdditiveEstimator(**params)
    model2.train(
        x=predictors,
        y="y",
        weights_column="myweight2",
        training_frame=df,
        fold_column="fold_number"
    )
    print("Finished training with API option fold_column=")

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_gam_cross_validation)
else:
    test_gam_cross_validation()
