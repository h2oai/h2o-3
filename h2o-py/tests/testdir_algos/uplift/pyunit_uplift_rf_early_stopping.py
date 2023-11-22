import sys, os

sys.path.insert(1, os.path.join("..", "..", ".."))
import h2o
from tests import pyunit_utils
from h2o.estimators import H2OUpliftRandomForestEstimator


def uplift_random_forest_early_stopping():
    print("Uplift Distributed Random Forest early stopping test")
    seed = 12345

    treatment_column = "treatment"
    response_column = "outcome"
    x_names = ["feature_"+str(x) for x in range(1, 13)]

    train_h2o = h2o.upload_file(pyunit_utils.locate("smalldata/uplift/upliftml_train.csv"))
    train_h2o[treatment_column] = train_h2o[treatment_column].asfactor()
    train_h2o[response_column] = train_h2o[response_column].asfactor()

    valid_h2o = h2o.upload_file(pyunit_utils.locate("smalldata/uplift/upliftml_test.csv"))
    valid_h2o[treatment_column] = valid_h2o[treatment_column].asfactor()
    valid_h2o[response_column] = valid_h2o[response_column].asfactor()

    ntrees = 20

    uplift_model = H2OUpliftRandomForestEstimator(
        ntrees=ntrees,
        max_depth=5,
        treatment_column=treatment_column,
        uplift_metric="KL",
        min_rows=10,
        seed=seed,
        sample_rate=0.99,
        auuc_type="gain",
    )

    uplift_model.train(y=response_column, x=x_names, training_frame=train_h2o, validation_frame=valid_h2o)
    print(uplift_model)

    uplift_model_es = H2OUpliftRandomForestEstimator(
        ntrees=ntrees,
        max_depth=5,
        treatment_column=treatment_column,
        uplift_metric="KL",
        min_rows=10,
        seed=seed,
        sample_rate=0.99,
        auuc_type="gain",
        stopping_metric="AUUC",
        stopping_rounds=4
    )

    uplift_model_es.train(y=response_column, x=x_names, training_frame=train_h2o, validation_frame=valid_h2o)
    print(uplift_model_es)
    
    num_trees = pyunit_utils.extract_from_twoDimTable(uplift_model._model_json["output"]["model_summary"],
                                                     "number_of_trees", takeFirst=True)
    num_trees_es = pyunit_utils.extract_from_twoDimTable(uplift_model_es._model_json["output"]["model_summary"],
                                                          "number_of_trees", takeFirst=True)
    print("Number of tress built with early stopping: {0}.  Number of trees built without early stopping: "
          "{1}".format(num_trees_es[0], num_trees[0]))
    assert num_trees_es[0] <= num_trees[0], "Early stopping criteria AUUC is not working."
    assert pyunit_utils.assert_equals(num_trees_es[0], uplift_model_es.actual_params["ntrees"],
                                      "Actual parameters and model summary should be equal")
    assert pyunit_utils.assert_equals(ntrees, uplift_model_es.input_params["ntrees"],
                                      "Input parameters should not be changed")
    assert uplift_model_es.actual_params["ntrees"] < num_trees[0], "Actual parameters are not updated."


if __name__ == "__main__":
    pyunit_utils.standalone_test(uplift_random_forest_early_stopping)
else:
    uplift_random_forest_early_stopping()
