import sys, os

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator

# check and make sure centering has no effect on I-spline coeffs
def test_GAM_coeffs_check():
    h2o_data = h2o.import_file(path=pyunit_utils.locate("smalldata/glm_test/binomial_20_cols_10KRows.csv"))
    h2o_data["C1"] = h2o_data["C1"].asfactor()
    h2o_data["C2"] = h2o_data["C2"].asfactor()
    h2o_data["C3"] = h2o_data["C3"].asfactor()
    h2o_data["C4"] = h2o_data["C4"].asfactor()
    h2o_data["C5"] = h2o_data["C5"].asfactor()
    h2o_data["C6"] = h2o_data["C6"].asfactor()
    h2o_data["C7"] = h2o_data["C7"].asfactor()
    h2o_data["C8"] = h2o_data["C8"].asfactor()
    h2o_data["C9"] = h2o_data["C9"].asfactor()
    h2o_data["C10"] = h2o_data["C10"].asfactor()
    myY = "C21"
    h2o_data["C21"] = h2o_data["C21"].asfactor()
    names = h2o_data.names
    myX = names.remove(myY)
    # build the GAM model
    h2o_model = H2OGeneralizedAdditiveEstimator(family='binomial', bs=[0, 1, 2, 3, 2], seed=9, spline_orders = [1, 1, 3, 1, 2], 
                                                num_knots=[3, 10, 3, 3, 3], gam_columns=["C11", "C12", "C13", "C14", "C15"])
    h2o_model.train(x=myX, y=myY, training_frame=h2o_data)
    # check and make sure coefficients regarding I-spline parameters are the same from coefficients that are centered
    # and non-centered., "C13_is_2"
    gam_is_coef_names = ["C13_is_0", "C13_is_1", "C13_is_2", "C15_is_0", "C15_is_1", "C15_is_2"]
    model_coef = h2o_model.coef()
    gam_coef_table = h2o_model._model_json["output"]["coefficients_table_no_centering"]._cell_values
    table_len = len(gam_coef_table)
    print(table_len)
    for cName in gam_is_coef_names:
        for index in range(0, table_len):
            if gam_coef_table[index][0] == cName:
                coef_val = gam_coef_table[index][1]
                assert abs(coef_val-model_coef[cName]) < 1e-6, "Expected coeff: {0}, actual: " \
                                                               "{1}".format(model_coef[cName], coef_val)
   
if __name__ == "__main__":
    pyunit_utils.standalone_test(test_GAM_coeffs_check)
else:
    test_GAM_coeffs_check()
