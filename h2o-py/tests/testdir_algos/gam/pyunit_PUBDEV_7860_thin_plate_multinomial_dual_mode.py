import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator

# In this test, we want to check and make sure that the models built with specifying gam_columns as
# ["C1", ["C2","C3"]] and [["C1"], ["C2","C3"]] should give the same results
def test_gam_dual_mode_multinomial():
    train = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/multinomial_10_classes_10_cols_10000_Rows_train.csv"))
    train["C11"] = train["C11"].asfactor()
    train["C1"] = train["C1"].asfactor()
    train["C2"] = train["C2"].asfactor()
    test = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/multinomial_10_classes_10_cols_10000_Rows_train.csv"))
    test["C11"] = test["C11"].asfactor()
    test["C1"] = test["C1"].asfactor()
    test["C2"] = test["C2"].asfactor()
    x = ["C1", "C2"]
    y = "C11"
    gam_cols1 =["C6", ["C7", "C8"], "C9", "C10"]
    gam_cols2 = [["C6"], ["C7", "C8"], ["C9"], ["C10"]]
    h2o_model1 = H2OGeneralizedAdditiveEstimator(family='multinomial', gam_columns=gam_cols1, bs=[1, 1, 0, 0], 
                                                 max_iterations = 2)
    h2o_model1.train(x=x, y=y, training_frame=train, validation_frame=test)
    h2o_model2 = H2OGeneralizedAdditiveEstimator(family='multinomial', gam_columns=gam_cols2, bs=[1, 1, 0, 0], 
                                                 max_iterations = 2)
    h2o_model2.train(x=x, y=y, training_frame=train, validation_frame=test)
    # check that both models produce the same coefficients
    print(h2o_model1.coef())
    print(h2o_model2.coef())
    pyunit_utils.assertCoefDictEqual(h2o_model1.coef()['coefficients'], h2o_model2.coef()['coefficients'], tol=1e-6)
    # check both models product the same validation metrics
    assert abs(h2o_model1.logloss(valid=True) - h2o_model2.logloss(valid=True)) < 1e-6,\
        "Expected validation logloss: {0}, Actual validation logloss: {1}".format(h2o_model1.logloss(valid=True), 
                                                                                  h2o_model2.logloss(valid=True))

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_gam_dual_mode_multinomial)
else:
    test_gam_dual_mode_multinomial()
