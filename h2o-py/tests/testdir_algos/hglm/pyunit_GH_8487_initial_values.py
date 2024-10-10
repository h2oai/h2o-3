from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator

# in this test, I compare H2O HGLM runs with and without stating starting values.

def test_HGLM_R():
    h2o_data = h2o.import_file(path=pyunit_utils.locate("smalldata/glm_test/semiconductor.csv"))
    y = "y"
    x = ["x1", "x3", "x5", "x6"]
    z = [0]
    tot = 1e-4
    h2o_data[0] = h2o_data[0].asfactor()
    start_vals = [0.001929687,0.002817188,-0.001707812,-0.003889062,0.010685937,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0.1,
                  0.1]
    h2o_glm = H2OGeneralizedLinearEstimator(HGLM=True, family="gaussian", rand_family=["gaussian"], random_columns=z,
                                            calc_like=True)
    h2o_glm.train(x=x, y=y, training_frame=h2o_data)
    modelMetrics = h2o_glm.training_model_metrics()

    h2o_glm_start_val = H2OGeneralizedLinearEstimator(HGLM=True, family="gaussian", rand_family=["gaussian"], random_columns=z,
                                            calc_like=True, startval=start_vals)
    h2o_glm_start_val.train(x=x, y=y, training_frame=h2o_data)
    modelMetricsSV = h2o_glm_start_val.training_model_metrics()

    # compare model metrics from both models and they should be the same
    metricsNames = ["hlik", "pvh", "dfrefe", "varfix", "pbvh", "convergence", "caic", "sumetadiffsquare"]
    metricsNamesArrays = ["summvc1", "sefe", "varranef", "ranef", "sere", "fixef", ]
    
    for ind in range(len(metricsNames)):
        assert abs(modelMetrics[metricsNames[ind]]-modelMetricsSV[metricsNames[ind]]) < tot, "expected {0}: {1}, " \
                                                                                             "actual {0}: {2}".format(metricsNames[ind], modelMetrics[metricsNames[ind]], modelMetricsSV[metricsNames[ind]])
    for ind in range(len(metricsNamesArrays)):
        pyunit_utils.equal_two_arrays(modelMetrics[metricsNamesArrays[ind]], modelMetricsSV[metricsNamesArrays[ind]], 1e-10, tot)

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_HGLM_R)
else:
    test_HGLM_R()
