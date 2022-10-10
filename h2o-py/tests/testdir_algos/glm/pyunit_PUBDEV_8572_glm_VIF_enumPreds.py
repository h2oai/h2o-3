import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator

def test_vif_with_enum_only():
    print("Testing glm cross-validation with alpha array, default lambda values for binomial models.")
    h2o_data = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/binomial_20_cols_10KRows.csv"))
    enum_columns = ["C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10"]
    for cname in enum_columns:
        h2o_data[cname] = h2o_data[cname].asfactor()
    myY = "C21"
    h2o_data["C21"] = h2o_data["C21"].asfactor()
    myX = ["C1", "C2", "C3", "C4", "C5"]
    try:
        model = H2OGeneralizedLinearEstimator(family='binomial', lambda_=0, generate_variable_inflation_factors=True)
        model.train(training_frame=h2o_data, x=myX, y=myY)
        assert False, "Should have thrown an exception!"
    except Exception as ex:
        print(ex)
        temp = str(ex)
        assert ("generate_variable_inflation_factors:  cannot be enabled for GLM models with only non-numerical predictors." in temp), \
            "Wrong exception was received."

    print("glm Multinomial makeGLMModel test completed!")


if __name__ == "__main__":
  pyunit_utils.standalone_test(test_vif_with_enum_only)
else:
    test_vif_with_enum_only()
