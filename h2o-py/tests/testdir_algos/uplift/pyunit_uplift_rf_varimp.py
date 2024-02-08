from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.uplift_random_forest import H2OUpliftRandomForestEstimator


def varimp_uplift_drf():
    print("Uplift Distributed Random Forest varimp test")
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

    ntrees = 3
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
    varimp = uplift_model.varimp()
    print(varimp)
    assert varimp is not None, "Variable importance should not be None."
    assert len(varimp) == len(x_names), "Size of varimp result should be the same as size of input variables."
    uplift_model.varimp_plot(server=True)
    

if __name__ == "__main__":
    pyunit_utils.standalone_test(varimp_uplift_drf)
else:
    varimp_uplift_drf()
