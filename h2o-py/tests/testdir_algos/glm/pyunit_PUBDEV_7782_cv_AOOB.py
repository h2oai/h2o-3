from __future__ import print_function
import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
import h2o.exceptions
from tests import pyunit_utils
from h2o.estimators import H2OGeneralizedLinearEstimator

# During normal GLM model building, the coefficient length can shrink when coefficients/gram matrix has zero 
# rows/columns.  Since betaCnd is allocated at the beginning of iteration loop and the coefficient length change
# happened within the iteration loop, there can be a discrepancy in the coefficient lengths.  Normally, this is not a 
# problem because the action of betaCnd = ADMM_solve() or other solvers.  But, in this case, that call is skipped.
# Hence, you will get betaCnd of one length and _state.beta() of another length.  My fix is to make sure when there
# is a length difference, I will extract the correct coefficients from betaCnd such that it will be of the same length
# as _state.beta().
#
# Test provided by Seb.
def test_GLM_throws_ArrayOutOfBoundException():    
# everything in this test is important to cause the exception:    
# - GLEASON as a categorical    
# - lambda search enabled    
# - alphas    # - CV enabled    
    df = h2o.import_file(path=pyunit_utils.locate("smalldata/prostate/prostate.csv"))    
    target = "CAPSULE"
    nFold = 5
    for col in [target, 'GLEASON']:        
        df[col] = df[col].asfactor()    
        glm = H2OGeneralizedLinearEstimator(lambda_search=True, alpha=[0.0, 0.2, 0.4, 0.6, 0.8, 1.0], nfolds=nFold, 
                                            seed=12345)
        glm.train(y=target, training_frame=df)
        
        assert len(glm._model_json["output"]['cross_validation_models'])==nFold, \
            "expected number of cross_validation_model: {0}.  Actual number of cross_validation: " \
            "{1}".format(len(glm._model_json["output"]['cross_validation_models']), nFold)

        # INFO water.default: Completing model GLM_model_python_1600368882855_5861_cv_5
        # WARN water.default: Model training job GLM completed with exception: java.lang.ArrayIndexOutOfBoundsException: 14
        # ERROR water.default:# java.lang.ArrayIndexOutOfBoundsException: 14
        # at water.util.ArrayUtils.subtract(ArrayUtils.java:1459)# at hex.glm.GLM$GLMDriver.fitIRLSM(GLM.java:1462)
        # at hex.glm.GLM$GLMDriver.fitModel(GLM.java:1726)# at hex.glm.GLM$GLMDriver.computeSubmodel(GLM.java:2095)
        # at hex.glm.GLM$GLMDriver.computeImpl(GLM.java:2202)# at hex.ModelBuilder$Driver.compute2(ModelBuilder.java:238)
        # at hex.glm.GLM$GLMDriver.compute2(GLM.java:890)# at water.H2O$H2OCountedCompleter.compute(H2O.java:1563)
        # at jsr166y.CountedCompleter.exec(CountedCompleter.java:468)# at jsr166y.ForkJoinTask.doExec(ForkJoinTask.java:263)
        # at jsr166y.ForkJoinPool$WorkQueue.runTask(ForkJoinPool.java:974)# at jsr166y.ForkJoinPool.runWorker(ForkJoinPool.java:1477)
        # at jsr166y.ForkJoinWorkerThread.run(ForkJoinWorkerThread.java:104)# 

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_GLM_throws_ArrayOutOfBoundException)
else:
    test_GLM_throws_ArrayOutOfBoundException()
