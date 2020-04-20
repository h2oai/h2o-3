# -*- encoding: utf-8 -*-
"""
Test suite for h2o.make_metrics().

:copyright: (c) 2020 H2O.ai
:license:   Apache License Version 2.0 (see LICENSE for details)
"""
import sys
sys.path.insert(1, "../../")

import h2o
from h2o.grid.grid_search import H2OGridSearch
from h2o.estimators import H2OGradientBoostingEstimator
from tests import pyunit_utils


def test_threshold_criterion():
    train_df = h2o.import_file(pyunit_utils.locate("smalldata/train_df.csv"))
    train_df["target"] = train_df["target"].asfactor()
    x = train_df.names
    x.remove("target")
    x.remove("target_bin")

    grid_space = dict(
                    ntrees=[10, 30, 50, 70],
                    max_depth=[2, 4, 6, 8],
                    stopping_tolerance=[0.99, 0.1, 0.01, 0.001],
                    min_rows=[15, 30, 50, 100],
                    learn_rate=[0.01, 0.03, 0.1, 0.3, 1]
                    )

    grid_criteria = dict(
                    strategy="RandomDiscrete",
                    max_models=30,
                    max_runtime_secs=120,
                    seed=1
                    )
    
    grid_search = H2OGridSearch(H2OGradientBoostingEstimator, grid_space, 'grid_search', grid_criteria)
    grid_search.train(training_frame=train_df, x=x, y="target", seed=1, weights_column = "Weight", sample_rate=1, 
                      check_constant_response=False)


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_threshold_criterion)
else:
    test_threshold_criterion()
