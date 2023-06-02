import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.model_selection import H2OModelSelectionEstimator as modelSelection

# This test is to make sure when there are redundant predictors for backward modelselection, the algorithm will return
# the correct columns dropped.  It should removed all redundant predictors in the first shot and then proceed to 
# remove one at a time.  However, it should still return the same coefficients compared to the glm trained on dataset
# without extra columns.
def test_modelselection_drop_redundant_columns():
    d = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/gaussian_20Cols_10kRows_3Extra.csv"))
    d2 = h2o.import_file(path=pyunit_utils.locate("smalldata/glm_test/gaussian_20cols_10000Rows.csv"))
    d["C1"] = d["C1"].asfactor()
    d["C2"] = d["C2"].asfactor()
    d["C3"] = d["C3"].asfactor()
    d["C4"] = d["C4"].asfactor()
    d["C5"] = d["C5"].asfactor()
    d["C6"] = d["C6"].asfactor()
    d["C7"] = d["C7"].asfactor()
    d["C8"] = d["C8"].asfactor()
    d["C9"] = d["C9"].asfactor()
    d["C10"] = d["C10"].asfactor()
    d["C110"] = d["C110"].asfactor()
    d2["C1"] = d2["C1"].asfactor()
    d2["C2"] = d2["C2"].asfactor()
    d2["C3"] = d2["C3"].asfactor()
    d2["C4"] = d2["C4"].asfactor()
    d2["C5"] = d2["C5"].asfactor()
    d2["C6"] = d2["C6"].asfactor()
    d2["C7"] = d2["C7"].asfactor()
    d2["C8"] = d2["C8"].asfactor()
    d2["C9"] = d2["C9"].asfactor()
    d2["C10"] = d2["C10"].asfactor()

    my_y = "C21"
    my_x = d.names
    my_x.remove(my_y)
    my_x2 = d2.names
    my_x2.remove(my_y)
    model_extra = modelSelection(seed=12345, mode="backward", remove_collinear_columns=True)
    model_extra.train(training_frame=d, x=my_x, y=my_y) # model with redundant predictors in dataset
    model = modelSelection(seed=12345, mode="backward", remove_collinear_columns=True)
    model.train(training_frame=d2, x=my_x2, y=my_y)
    model_coefs = model.coef()
    model_extra_coefs = model_extra.coef()
    dropped_predictors = model.get_predictors_removed_per_step()
    dropped_extra_predictors = model_extra.get_predictors_removed_per_step()
    # dropped coefficients are the same as well except the first one with redundant columns should also be removed
    assert len(dropped_extra_predictors[len(dropped_extra_predictors)-1]) > 1, \
    "Dropped columns for redundant columns should exceed 1."
    lastInd = len(model_coefs)-1
    # coefficients and dropped predictors (except the last one) should equal between the two models
    for index in range(0, len(model_coefs)):
        model_coef = model_coefs[index]
        model_extra_coef = model_extra_coefs[index]
        keys = model_coef.keys()
        for oneKey in keys:
            assert abs(model_coef[oneKey]-model_extra_coef[oneKey]) < 1e-6, \
                "Expected coefficient {0}: {1}, Actual coefficient {0}: {3}. They are " \
                "different".format(oneKey, model_coef[oneKey], model_extra_coefs[oneKey])

        if index < lastInd:
            assert dropped_predictors[index] == dropped_extra_predictors[index], \
                "Expected dropped predictor: {0}, actual dropped predictor: {1}.  They are " \
                "different".format(dropped_predictors[index], dropped_extra_predictors[index])
    
if __name__ == "__main__":
    pyunit_utils.standalone_test(test_modelselection_drop_redundant_columns)
else:
    test_modelselection_drop_redundant_columns()
