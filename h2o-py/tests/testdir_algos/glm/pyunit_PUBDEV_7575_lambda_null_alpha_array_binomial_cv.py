import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator as glm

# Given alpha array and using default lambda, build two cross-validation models, one with validation dataset
# and one without for binomial.  Since they use the metrics from cross-validation, they should come up with
# the same models.
def glm_alpha_arrays_null_lambda_cv():
    print("Testing glm cross-validation with alpha array, default lambda values for binomial models.")
    h2o_data = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/binomial_20_cols_10KRows.csv"))
    enum_columns = ["C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10"]
    for cname in enum_columns:
        h2o_data[cname] = h2o_data[cname]
    myY = "C21"
    h2o_data["C21"] = h2o_data["C21"].asfactor()
    myX = h2o_data.names.remove(myY)
    data_frames = h2o_data.split_frame(ratios=[0.8])
    training_data = data_frames[0]
    test_data = data_frames[1]
    
    # build model with CV but no validation dataset
    cv_model = glm(family='binomial',alpha=[0.1,0.5,0.9], nfolds = 3)
    cv_model.train(training_frame=training_data,x=myX,y=myY)
    cv_r = glm.getGLMRegularizationPath(cv_model)
    # build model with CV and with validation dataset
    cv_model_valid = glm(family='binomial',alpha=[0.1,0.5,0.9], nfolds = 3)
    cv_model_valid.train(training_frame=training_data, validation_frame = test_data, x=myX,y=myY)
    cv_r_valid = glm.getGLMRegularizationPath(cv_model_valid)

    for l in range(0,len(cv_r['lambdas'])):
        print("comparing coefficients for submodel {0}".format(l))
        pyunit_utils.assertEqualCoeffDicts(cv_r['coefficients'][l], cv_r_valid['coefficients'][l], tol=1e-6)
        pyunit_utils.assertEqualCoeffDicts(cv_r['coefficients_std'][l], cv_r_valid['coefficients_std'][l], tol=1e-6)

if __name__ == "__main__":
    pyunit_utils.standalone_test(glm_alpha_arrays_null_lambda_cv)
else:
    glm_alpha_arrays_null_lambda_cv()
