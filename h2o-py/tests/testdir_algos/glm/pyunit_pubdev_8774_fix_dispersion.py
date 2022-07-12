import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator

def test_gamma_dispersion_factor():
    training_data = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/glm_test/gamma_dispersion_factor_9_10kRows.csv")
    Y = 'resp'
    x = ['abs.C1.', 'abs.C2.', 'abs.C3.', 'abs.C4.', 'abs.C5.']

    try:
        model = H2OGeneralizedLinearEstimator(family='gaussian', lambda_=0, compute_p_values=True, 
                                          fix_dispersion_parameter=True)
        model.train(training_frame=training_data, x=x, y=Y)
        assert False, "Test failed:  should have thrown exception of fix_dispersion_parameter."
    except Exception as ex:
        print(ex)
        temp = str(ex)
        assert "is only allowed for tweedie, gamma and negativebinomial families" in temp,"Wrong exception was received."
        print("Test passed!")

if __name__ == "__main__":
  pyunit_utils.standalone_test(test_gamma_dispersion_factor)
else:
    test_gamma_dispersion_factor()
