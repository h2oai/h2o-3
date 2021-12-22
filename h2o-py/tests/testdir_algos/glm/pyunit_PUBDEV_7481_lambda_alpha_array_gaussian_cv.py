import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator as glm

# Given arrays of alpha and lambda, build two cross-validation models, one with validation dataset
# and one without for Gaussian.  Since they use the metrics from cross-validation, they should come up with
# the same models
def glm_alpha_lambda_arrays_cv():
    print("Testing glm cross-validation with alpha array, lambda array for binomial models.")
    h2o_data = h2o.import_file(
        path=pyunit_utils.locate("smalldata/glm_test/gaussian_20cols_10000Rows.csv"))
    enum_columns = ["C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10"]
    for cname in enum_columns:
        h2o_data[cname] = h2o_data[cname].asfactor()
    myY = "C21"
    myX = h2o_data.names.remove(myY)
    data_frames = h2o_data.split_frame(ratios=[0.8])
    training_data = data_frames[0]
    test_data = data_frames[1]
    
    # choices made in model_all and model_xval should be the same since they should be using xval metrics
    model_all = glm(family="gaussian", Lambda=[0.1,0.5,0.9], alpha=[0.1,0.5,0.9], nfolds=3, cold_start=True, 
                    fold_assignment="modulo")
    model_all.train(x=myX, y=myY, training_frame = training_data, validation_frame = test_data)
    model_all_rpath = glm.getGLMRegularizationPath(model_all)
    model_xval =  glm(family="gaussian", Lambda=[0.1,0.5,0.9], alpha=[0.1,0.5,0.9], nfolds=3, cold_start=True, 
                      fold_assignment="modulo")
    model_xval.train(x=myX, y=myY, training_frame = training_data)
    model_xval_rpath = glm.getGLMRegularizationPath(model_xval)

    for l in range(0,len(model_all_rpath['lambdas'])):
        print("comparing coefficients for submodel {0}".format(l))
        pyunit_utils.assertEqualCoeffDicts(model_all_rpath['coefficients'][l], model_xval_rpath['coefficients'][l], tol=1e-6)
        pyunit_utils.assertEqualCoeffDicts(model_all_rpath['coefficients_std'][l], model_xval_rpath['coefficients_std'][l], tol=1e-6)



if __name__ == "__main__":
    pyunit_utils.standalone_test(glm_alpha_lambda_arrays_cv)
else:
    glm_alpha_lambda_arrays_cv()
