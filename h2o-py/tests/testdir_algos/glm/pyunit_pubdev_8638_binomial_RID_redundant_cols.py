import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator

def test_gaussian_rid_redundant_cols():
    '''
    In this test, I run GLM model with duplicate columns and get its RID.  Next, I run GLM model without the duplicate
    columns and get its RID.  The two RID frames should equal.
    '''
    d = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/binomial_20_cols_10KRows.csv"))
    d["C1"] = d["C1"].asfactor()
    d["C2"] = d["C2"].asfactor()
    d["C21"] = d["C21"].asfactor()
    # add duplicate predictors
    tempEnum = d["C1"] # generate extra enum columns
    tempEnum.set_names(["dup_enum"])
    tempNum1 = d["C11"]+0.5*d["C13"] # generate extra numerical columns
    tempNum1.set_names(["dup_num1"])
    tempNum2 = d["C12"]-0.5*d["C14"]
    tempNum2.set_names(["dup_num2"])
    a = tempEnum.cbind(tempNum1)
    b = a.cbind(tempNum2)
    d = b.cbind(d)
    my_y = "C21"
    my_x = d.names
    my_x.remove(my_y)
    glm_model = H2OGeneralizedLinearEstimator(family="binomial", seed=1234, influence="dfbetas", standardize=False, 
                                              lambda_=0.0)
    glm_model.train(x=my_x, y=my_y, training_frame=d)
    rid_frame = glm_model.get_regression_influence_diagnostics()
    my_x = ['C2', 'C1','dup_num1', 'dup_num2', 'C3', 'C4', 'C5', 'C6', 'C7', 'C8', 'C9', 'C10', 'C11', 'C12', 'C15', 
            'C16', 'C17', 'C18', 'C19', 'C20']
    glm_model2 = H2OGeneralizedLinearEstimator(family="binomial", seed=1234, influence="dfbetas", standardize=False,
                                              lambda_=0.0)
    glm_model2.train(x=my_x, y=my_y, training_frame=d)
    rid_frame2 = glm_model2.get_regression_influence_diagnostics()
    coeffs = glm_model.coef()
    coeffs2 = glm_model2.coef()
    pyunit_utils.assertCoefDictEqual(coeffs2, coeffs)
    cols2Compare = ['DFBETA_C2.1', 'DFBETA_C2.2', 'DFBETA_C2.3', 'DFBETA_C2.4', 'DFBETA_C2.5', 'DFBETA_C2.6', 
                    'DFBETA_C2.7', 'DFBETA_C1.1', 'DFBETA_C1.2', 'DFBETA_C1.3', 'DFBETA_C1.4', 'DFBETA_C1.5', 
                    'DFBETA_dup_num1', 'DFBETA_dup_num2', 'DFBETA_C3', 'DFBETA_C4', 'DFBETA_C5', 'DFBETA_C6', 
                    'DFBETA_C7', 'DFBETA_C8', 'DFBETA_C9', 'DFBETA_C10', 'DFBETA_C11', 'DFBETA_C12', 'DFBETA_C15',
                    'DFBETA_C16', 'DFBETA_C17', 'DFBETA_C18', 'DFBETA_C19', 'DFBETA_C20', 'DFBETA_Intercept']
    for ind in range(0, len(cols2Compare)):
        pyunit_utils.compare_frames_local(rid_frame[cols2Compare[ind]], rid_frame2[cols2Compare[ind]], prob=1)


if __name__ == "__main__":
  pyunit_utils.standalone_test(test_gaussian_rid_redundant_cols)
else:
    test_gaussian_rid_redundant_cols()
