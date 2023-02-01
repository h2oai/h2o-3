import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator

def test_glm_negLL_Obj():
    d = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/gaussian_20cols_10000Rows.csv"))
    d["C1"] = d["C1"].asfactor()
    d["C2"] = d["C2"].asfactor()
    my_y = "C21"
    my_x = d.names
    my_x.remove(my_y)
    glm_model = H2OGeneralizedLinearEstimator(family="gaussian", seed=1234, generate_scoring_history=True)
    glm_model.train(x=my_x, y=my_y, training_frame=d)
    glm_model_noReg = H2OGeneralizedLinearEstimator(family="gaussian", seed=1234, generate_scoring_history=True, lambda_=0.0)
    glm_model_noReg.train(x=my_x, y=my_y, training_frame=d)
    nll = glm_model.negative_log_likelihood()
    obj = glm_model.average_objective()
    nll_noReg = glm_model_noReg.negative_log_likelihood()
    obj_noReg = glm_model_noReg.average_objective()
    assert abs(nll_noReg-obj_noReg/glm_model_noReg.actual_params["obj_reg"]) < 1e-6, \
        "objective ({0}) and negative log likelihood ({1}) should equal but do not.".format(obj_noReg, nll_noReg)
    assert abs(nll-obj) > 1e-6, "objective ({0}) and negative log likelihood ({1}) should not equal but do" \
                                            " equal.".format(obj, nll)
if __name__ == "__main__":
  pyunit_utils.standalone_test(test_glm_negLL_Obj)
else:
    test_glm_negLL_Obj()
