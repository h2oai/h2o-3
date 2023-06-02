from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils, compare_frames, assert_equals
from h2o.estimators.gbm import H2OGradientBoostingEstimator


def test_interaction_constraints_equal_model():
    """
    Test that standard model build is equal to model build with usage of all x in constrains.The models should be equal
    by definition.

    """

    prostate = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    prostate.describe()
    prostate[1] = prostate[1].asfactor()

    ntrees = 10
    distribution = "bernoulli"
    seed = 42
    interaction_constraints = [prostate.names[2:]]
    max_depth = 8
    score_tree_interval = 1
    
    print("Used interaction constrains:", interaction_constraints)
    
    prostate_gbm_reference = H2OGradientBoostingEstimator(distribution=distribution, 
                                                          ntrees=ntrees, 
                                                          seed=seed,
                                                          max_depth=max_depth,
                                                          score_tree_interval=score_tree_interval)
    prostate_gbm_reference.train(x=list(range(2, 9)), y=1, training_frame=prostate)
    predictions_reference = prostate_gbm_reference.predict(prostate)

    prostate_gbm_interactions = H2OGradientBoostingEstimator(distribution=distribution,
                                                             ntrees=ntrees,
                                                             seed=seed,
                                                             max_depth=max_depth,
                                                             score_tree_interval=score_tree_interval,
                                                             interaction_constraints=interaction_constraints)
    prostate_gbm_interactions.train(x=list(range(2, 9)), y=1, training_frame=prostate)
    prediction_interactions = prostate_gbm_interactions.predict(prostate)

    print("Prediction reference:")
    print(predictions_reference)
    print("Prediction with interactions:")
    print(prediction_interactions)

    compare_frames(predictions_reference, prediction_interactions, -1, strict=True)

    reference_summary = prostate_gbm_reference._model_json["output"]["model_summary"]
    interactions_summary = prostate_gbm_interactions._model_json["output"]["model_summary"]
    print("Summary reference:")
    print(reference_summary)
    print("Summary interactions:")
    print(interactions_summary)
    assert_equals(reference_summary["mean_depth"][0], interactions_summary["mean_depth"][0])
    assert_equals(reference_summary["mean_leaves"][0], interactions_summary["mean_leaves"][0])

    pyunit_utils.check_model_metrics(prostate_gbm_reference, prostate_gbm_interactions, distribution)


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_interaction_constraints_equal_model)
else:
    test_interaction_constraints_equal_model()
