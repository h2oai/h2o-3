from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator

# in this test, I compare the results obtained from R run with H2O-3 runs using a much larger datasets to test 
# multiple chunks operation.

def test_HGLM_R():
    tot=1e-6
    h2o_data = h2o.import_file(path=pyunit_utils.locate("smalldata/glm_test/HGLM_5KRows_100Z.csv"), 
                               col_types=["enum", "enum", "enum", "enum", "numeric", "numeric", "numeric", 
                                          "numeric"])
    y = "response"
    x = ["enum1","enum2","enum3","num1","num2","num3"]
    z = 0
    h2o_glm = H2OGeneralizedLinearEstimator(HGLM=True, family="gaussian", rand_family=["gaussian"], random_columns=[z],
                                            calc_like=True)
    h2o_glm.train(x=x, y=y, training_frame=h2o_data)
    modelMetrics = h2o_glm.training_model_metrics()
    rmodelMetrics = {"hlik":-23643.3076231, "caic":47019.7968491, "pvh":-23491.5738429, "pbvh": -23490.2982034,  
                     "dfrefe":4953.0, "varfix":703.86912057}

    metricsNames = ["hlik", "caic", "pvh", "pbvh", "dfrefe", "varfix"]
    for kNames in metricsNames:
        assert abs(rmodelMetrics[kNames]-modelMetrics[kNames])<tot,"for {2}, Expected from R: {0}, actual from H2O-3: " \
                                                               "{1}".format(rmodelMetrics[kNames], modelMetrics[kNames], kNames)

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_HGLM_R)
else:
    test_HGLM_R()
