from __future__ import print_function
import sys
import h2o
sys.path.insert(1,"../../../")
from tests import pyunit_utils
from h2o.estimators.aggregator import H2OAggregatorEstimator

#testing default setup of following parameters:
#distribution (available in Deep Learning, XGBoost, GBM):
#stopping_metric (available in: GBM, DRF, Deep Learning, AutoML, XGBoost, Isolation Forest):
#histogram_type (available in: GBM, DRF)
#solver (available in: GLM) already done in hex.glm.GLM.defaultSolver()
#categorical_encoding (available in: GBM, DRF, Deep Learning, K-Means, Aggregator, XGBoost, Isolation Forest)
#fold_assignment (available in: GBM, DRF, Deep Learning, GLM, Naïve-Bayes, K-Means, XGBoost)


def test_aggregator_effective_parameters():
    frame = h2o.create_frame(rows=10000, cols=10, categorical_fraction=0.6, integer_fraction=0, binary_fraction=0, real_range=100,
                             integer_range=100, missing_fraction=0, factors=100, seed=1234)

    agg1 = H2OAggregatorEstimator(target_num_exemplars=1000, rel_tol_num_exemplars=0.5, categorical_encoding="eigen")
    agg1.train(training_frame=frame)

    agg2 = H2OAggregatorEstimator(target_num_exemplars=1000, rel_tol_num_exemplars=0.5)
    agg2.train(training_frame=frame)

    assert agg2.parms['categorical_encoding']['input_value'] == "AUTO"
    assert agg2.parms['categorical_encoding']['actual_value'] == agg1.parms['categorical_encoding']['actual_value']

if __name__ == "__main__":
  pyunit_utils.standalone_test(test_aggregator_effective_parameters)
else:
    test_aggregator_effective_parameters()
