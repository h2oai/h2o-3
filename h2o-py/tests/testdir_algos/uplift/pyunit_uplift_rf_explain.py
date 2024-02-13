import sys, os

sys.path.insert(1, os.path.join("..", "..", ".."))
import h2o
from tests import pyunit_utils
from h2o.estimators import H2OUpliftRandomForestEstimator


def uplift_random_forest_explain():
    print("Uplift Distributed Random Forest explain test")
    seed = 12345

    treatment_column = "treatment"
    response_column = "outcome"
    x_names = ["feature_"+str(x) for x in range(1, 3)]

    train_h2o = h2o.upload_file(pyunit_utils.locate("smalldata/uplift/upliftml_train.csv"))
    train_h2o[treatment_column] = train_h2o[treatment_column].asfactor()
    train_h2o[response_column] = train_h2o[response_column].asfactor()

    valid_h2o = h2o.upload_file(pyunit_utils.locate("smalldata/uplift/upliftml_test.csv"))
    valid_h2o[treatment_column] = valid_h2o[treatment_column].asfactor()
    valid_h2o[response_column] = valid_h2o[response_column].asfactor()

    ntrees = 2
    max_depth = 2
    min_rows = 10
    sample_rate = 0.8

    uplift_model = H2OUpliftRandomForestEstimator(
        ntrees=ntrees,
        max_depth=max_depth,
        treatment_column=treatment_column,
        min_rows=min_rows,
        seed=seed,
        sample_rate=sample_rate,
        score_each_iteration=True
    )

    uplift_model.train(y=response_column, x=x_names, training_frame=train_h2o, validation_frame=valid_h2o)
    print(uplift_model)

    # should throw error
    try:
        uplift_model.explain(valid_h2o)
    except ValueError:
        assert True, "The explain function should fail with UpliftDRF."
        

if __name__ == "__main__":
    pyunit_utils.standalone_test(uplift_random_forest_explain)
else:
    uplift_random_forest_explain()
