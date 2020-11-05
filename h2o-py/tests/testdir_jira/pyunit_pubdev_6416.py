#!/usr/bin/env python
# -*- coding: utf-8 -*- 
from h2o.estimators import H2OGradientBoostingEstimator
from h2o.grid import H2OGridSearch
from tests import pyunit_utils
import h2o
import unittest


class PUBDEV6416(unittest.TestCase):

    def test_pubdev_6416(self):
        # Attempt to add a model to the grid by specifying invalid hyperparameters search range.
        # Should fail and generate error
        data = h2o.import_file(pyunit_utils.locate('smalldata/iris/iris_train.csv'))
        hyper_params = {
            'max_depth': [8],
            'sample_rate': [.9],
            'col_sample_rate': [.9],
            'col_sample_rate_per_tree': [.9],
            'col_sample_rate_change_per_level': [.9],
            'min_rows': [5000000],  # Invalid hyperparameter
            'min_split_improvement': [1e-4],
            'histogram_type': ["UniformAdaptive"]
        }

        search_criteria = {'strategy': "RandomDiscrete",
                           'max_runtime_secs': 3600,
                           'max_models': 1,
                           'seed': 12345,
                           'stopping_rounds': 5,
                           'stopping_metric': "MSE",
                           'stopping_tolerance': 1e-3
                           }

        gbm = H2OGradientBoostingEstimator(distribution='multinomial',
                                           ntrees=5,
                                           learn_rate=0.05,
                                           score_tree_interval=5,
                                           seed=1,
                                           stopping_rounds=5,
                                           stopping_metric="MSE",
                                           stopping_tolerance=1e-4)

        grid = H2OGridSearch(gbm,
                             hyper_params=hyper_params,
                             grid_id="grid_pubdev6416",
                             search_criteria=search_criteria)

        with self.assertRaises(ValueError) as err:
            grid.train(x=["sepal_len", "sepal_wid"],
                       y="species",
                       max_runtime_secs=3600,
                       training_frame=data)
        # During the first search, the error should be present
        assert "Details: ERRR on field: _min_rows: The dataset size is too small to split for min_rows=5000000.0: must have at least 1.0E7 (weighted) rows" \
               in str(err.exception)
        assert len(grid.models) == 0

        hyper_params = {
            'max_depth': [8],
            'sample_rate': [.9],
            'col_sample_rate': [.9],
            'col_sample_rate_per_tree': [.9],
            'col_sample_rate_change_per_level': [.9],
            'min_rows': [10],
            'min_split_improvement': [1e-4],
            'histogram_type': ["UniformAdaptive"]
        }
        gbm = H2OGradientBoostingEstimator(distribution='multinomial',
                                           ntrees=5,
                                           learn_rate=0.05,
                                           learn_rate_annealing=0.99,
                                           score_tree_interval=5,
                                           seed=1,
                                           stopping_rounds=5,
                                           stopping_metric="MSE",
                                           stopping_tolerance=1e-4)

        grid = H2OGridSearch(gbm,
                             hyper_params=hyper_params,
                             grid_id="grid_pubdev6416",
                             search_criteria=search_criteria)

        grid.train(x=["sepal_len", "sepal_wid"],
                   y="species",
                   max_runtime_secs=3600,
                   training_frame=data)

        # Assert the model is actually trained and added to the grid, not affected by previous exceptions
        assert len(grid.models) == 1


suite = unittest.TestLoader().loadTestsFromTestCase(PUBDEV6416)
unittest.TextTestRunner().run(suite)
