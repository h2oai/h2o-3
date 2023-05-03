import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.model_selection import H2OModelSelectionEstimator as modelSelection
from h2o.estimators.glm import H2OGeneralizedLinearEstimator

def test_modelselection_gaussian_RID():
    '''
    In this test, I use model selection multiple modes to generate GLM models with influence = dfbetas.  The rid frames
    from the different models should be the same as long as they are using the same predictor subsets.  Next, I
    run GLM model with influence = dfbetas with the same predictor subsets as in model selection backward mode.  The
     rid frame generated from both methods should equal.
'''
    d = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    my_y = "GLEASON"
    my_x = ["AGE","RACE","CAPSULE","DCAPS","PSA","VOL","DPROS"]
    model_backward = modelSelection(seed=12345, max_predictor_number=3, mode="backward", influence="dfbetas",
                                    standardize=False, family="gaussian")
    model_backward.train(training_frame=d, x=my_x, y=my_y)
    backward_rid = model_backward.get_regression_influence_diagnostics(())
    model_maxrsweep = modelSelection(seed=12345, max_predictor_number=3, mode="maxrsweep", build_glm_model=True,
                                         influence="dfbetas", standardize=False, family="gaussian")
    model_maxrsweep.train(training_frame=d, x=my_x, y=my_y)
    maxrsweep_rid = model_maxrsweep.get_regression_influence_diagnostics()
    model_maxr = modelSelection(seed=12345, max_predictor_number=3, mode="maxr", influence="dfbetas", 
                                standardize=False, family="gaussian")
    model_maxr.train(training_frame=d, x=my_x, y=my_y)
    maxr_rid = model_maxr.get_regression_influence_diagnostics()
    
    # compare rid frames for column size 4, 6, 8
    compare_frames(backward_rid[0], maxr_rid[0])
    compare_frames(backward_rid[1], maxr_rid[1])
    compare_frames(backward_rid[2], maxr_rid[2])
    compare_frames(backward_rid[0], maxrsweep_rid[0])
    compare_frames(backward_rid[1], maxrsweep_rid[1])
    compare_frames(backward_rid[2], maxrsweep_rid[2])
    
    best_predictor_subsets = model_backward.get_best_model_predictors()
    for ind in range(0, len(backward_rid)):
        glm = H2OGeneralizedLinearEstimator(family="gaussian", seed=1234, influence="dfbetas", standardize=False, lambda_=0.0)
        glm.train(x=best_predictor_subsets[ind], y=my_y, training_frame=d)
        glm_rid = glm.get_regression_influence_diagnostics()
        colnames = glm_rid.names
        for ind2 in range(0, len(colnames)):
            if "DFBETA" in colnames[ind2]:
                pyunit_utils.compare_frames_local(glm_rid[colnames[ind2]], backward_rid[ind][colnames[ind2]], prob=1.0, tol=1e-6)
    print("Pass test!")

def compare_frames(f1, f2):
    '''
    compare two frames with same column names but the order of the columns may have been switched. 
    '''
    names = f1.names
    assert f1.nrow == f2.nrow and f2.ncol == f2.ncol, "Expected frame size: {0} rows by {1} cols.  Actual: {2} rows " \
                                                     "by {3} cols".format(f1.nrow, f1.ncol, f2.nrow, f2.ncol)
    for colName in names:
        pyunit_utils.compare_frames_local(f1[colName], f2[colName], prob=1, tol=1e-6)
    

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_modelselection_gaussian_RID)
else:
    test_modelselection_gaussian_RID()
