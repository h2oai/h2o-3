import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator


def test_glm_control_vals_ordinal():
    h2o_data = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/multinomial_10_classes_10_cols_10000_Rows_train.csv"))
    h2o_data["C1"] = h2o_data["C1"].asfactor()
    h2o_data["C2"] = h2o_data["C2"].asfactor()
    h2o_data["C3"] = h2o_data["C3"].asfactor()
    h2o_data["C4"] = h2o_data["C4"].asfactor()
    h2o_data["C5"] = h2o_data["C5"].asfactor()
    h2o_data["C11"] = h2o_data["C11"].asfactor()
    x = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
    y = "C11"

     # h2o_data = h2o.import_file(pyunit_utils.locate("smalldata/junit/cars.csv"))
     # y = "cylinders"
     # h2o_data[y] = h2o_data[y].asfactor()

    # check ordinal distribution
    model_o = H2OGeneralizedLinearEstimator(family='ordinal',
                                            generate_scoring_history=True,
                                            score_each_iteration=True,
                                            seed=42)
    
    model_o.train(training_frame=h2o_data, y=y)
    print(model_o)
    print(model_o._model_json["output"]["scoring_history"])
    predictions_o = model_o.predict(h2o_data).as_data_frame()

    model_cv_o = H2OGeneralizedLinearEstimator(family='ordinal',
                                               control_variables=["C2"],
                                               generate_scoring_history=True,
                                               score_each_iteration=True,
                                               seed=42)
    model_cv_o.train(training_frame=h2o_data, y=y)
    print(model_cv_o)
    print(model_cv_o._model_json["output"]["scoring_history"])
    predictions_cv_o = model_cv_o.predict(h2o_data).as_data_frame()

    # check predictions are different
    for i in range(predictions_o.shape[0]):
        pyunit_utils.assert_not_equal(predictions_o.iloc[i, 1], predictions_cv_o.iloc[i, 1], f"Predictions at position {i} should differ but they don't!")


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_glm_control_vals_ordinal)
else:
    test_glm_control_vals_ordinal()
