import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
import math

# make sure variable inflation factors are calculated correctly for polynomials because multinomials and ordinals
# are processed separately from other families
def test_vif_multinomial():
    h2o_data = h2o.import_file(
        pyunit_utils.locate("smalldata/glm_test/multinomial_10_classes_10_cols_10000_Rows_train.csv"))
    h2o_data["C1"] = h2o_data["C1"].asfactor()
    h2o_data["C2"] = h2o_data["C2"].asfactor()
    h2o_data["C3"] = h2o_data["C3"].asfactor()
    h2o_data["C4"] = h2o_data["C4"].asfactor()
    h2o_data["C5"] = h2o_data["C5"].asfactor()
    h2o_data["C11"] = h2o_data["C11"].asfactor()
    X = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
    Y = "C11"
    model = H2OGeneralizedLinearEstimator(family='multinomial', lambda_=0, generate_variable_inflation_factors=True)
    model.train(training_frame=h2o_data, x=X, y=Y)

    vif_names = model.get_variable_inflation_factors()
    vif = model._model_json["output"]["variable_inflation_factors"]
    vif_predictor_names = model._model_json["output"]["vif_predictor_names"]
    # check variable inflation factors are the same gotten from the coefficient tables and from the variables directly
    count = 0
    for pred in vif_predictor_names:
        if math.isnan(vif[count]):
            assert math.isnan(vif_names[pred]), "For predictor: {0}, expected inflation variable factor is NaN but" \
                                            " actual value is {1} and is not NaN.".format(pred, vif_names[pred])
        else:
            assert abs(vif[count]-vif_names[pred]) < 1e-6, "For predictor: {0}, expected inflation variable factor:" \
                                                       " {1}, actual value: {2}".format(pred, vif_names[pred], vif[count])
        count = count+1
    count_non_nan = 0
    for pred in vif_names.keys():
        if not(math.isnan(vif_names[pred])):
            count_non_nan += 1
    assert count_non_nan == len(vif), "Expected numerical predictor length: {0}, actual: {1}.  They do not " \
                                      "match!".format(len(vif), count_non_nan)


if __name__ == "__main__":
  pyunit_utils.standalone_test(test_vif_multinomial)
else:
    test_vif_multinomial()
