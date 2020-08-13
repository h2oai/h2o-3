import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator as glm

# Given alpha array and lambda_search=True, build two cross-validation models, one with validation dataset
# and one without for multinomial.  Since they use the metrics from cross-validation, they should come up with
# the same models.
def glm_alpha_array_with_lambda_search_cv():
    # read in the dataset and construct training set (and validation set)
    print("Testing glm cross-validation with alpha array, lambda_search for multiomial models.")
    h2o_data = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/multinomial_10_classes_10_cols_10000_Rows_train.csv"))
    enum_columns = ["C1", "C2", "C3", "C4", "C5"]
    for cname in enum_columns:
        h2o_data[cname] = h2o_data[cname]
    myY = "C11"
    h2o_data["C11"] = h2o_data["C11"].asfactor()
    myX = h2o_data.names.remove(myY)
    data_frames = h2o_data.split_frame(ratios=[0.8])
    training_data = data_frames[0]
    test_data = data_frames[1]
    # build model with CV but no validation dataset
    cv_model = glm(family='multinomial',alpha=[0.1,0.5,0.9], lambda_search=True, nfolds = 3)
    cv_model.train(training_frame=training_data,x=myX,y=myY)
    cv_r = glm.getGLMRegularizationPath(cv_model)
    # build model with CV and with validation dataset
    cv_model_valid = glm(family='multinomial',alpha=[0.1,0.5,0.9], lambda_search=True, nfolds = 3)
    cv_model_valid.train(training_frame=training_data, validation_frame = test_data, x=myX,y=myY)
    cv_r_valid = glm.getGLMRegularizationPath(cv_model_valid)

    for l in range(0,len(cv_r['lambdas'])):
        print("comparing coefficients for submodel {0}".format(l))
        pyunit_utils.assertEqualCoeffDicts(cv_r['coefficients'][l], cv_r_valid['coefficients'][l], tol=1e-6)
        pyunit_utils.assertEqualCoeffDicts(cv_r['coefficients_std'][l], cv_r_valid['coefficients_std'][l], tol=1e-6)


if __name__ == "__main__":
    pyunit_utils.standalone_test(glm_alpha_array_with_lambda_search_cv)
else:
    glm_alpha_array_with_lambda_search_cv()
