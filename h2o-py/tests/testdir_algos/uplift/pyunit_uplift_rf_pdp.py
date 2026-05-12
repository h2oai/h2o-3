import sys, os

sys.path.insert(1, os.path.join("..", "..", ".."))
import h2o
from tests import pyunit_utils
from h2o.estimators import H2OUpliftRandomForestEstimator


def uplift_random_forest_pdp():
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
    
    features = ["feature_3", "feature_4", "feature_5"]
    # pdp with all data
    pdp = uplift_model.partial_plot(valid_h2o, cols=features, plot=False)
    assert len(pdp) == len(features)
    assert pdp[0] is not None
    
    mask = valid_h2o[treatment_column] == "treatment"
    
    # pdp with treatment group data
    treatment_valid_h2o = valid_h2o[mask, :]
    pdp_tr = uplift_model.partial_plot(treatment_valid_h2o, cols=features, plot=False)
    assert len(pdp_tr) == len(features)

    # pdp with control group data
    control_valid_h2o = valid_h2o[~mask, :]
    pdp_ct = uplift_model.partial_plot(control_valid_h2o, cols=features, plot=False)
    assert len(pdp_ct) == len(features)


if __name__ == "__main__":
    pyunit_utils.standalone_test(uplift_random_forest_pdp)
else:
    uplift_random_forest_pdp()
