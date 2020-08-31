from __future__ import division
from __future__ import print_function
import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator
from h2o.estimators.glm import H2OGeneralizedLinearEstimator

# In this test, we check and make sure that we can print out all relevevant model metrics for Binomial
def test_gam_model_predict():
    path = pyunit_utils.locate("bigdata/laptop/lending-club/loan.csv")
    col_types = {'bad_loan': 'enum'}
    frame = h2o.import_file(path=path, col_types=col_types) # import from url
    frame.describe() # summarize table
    # split into training and test for cross validation
    train, test = frame.split_frame(ratios=[.7])
    # assign target and inputs for logistic regression
    y = 'bad_loan'
    X = [name for name in train.columns if name != y]
    train[y] = train[y].asfactor()
    test[y] = test[y].asfactor()

    # initialize
    loan_glm = H2OGeneralizedLinearEstimator(family='binomial',
                                         solver='IRLSM',
                                         standardize=True,
                                         lambda_search=True)
    # train 
    loan_glm.train(X, y, training_frame=train)
    # initialize
    loan_gam = H2OGeneralizedAdditiveEstimator(family='binomial',
                                           solver='IRLSM',
                                           standardize=True,
                                           lambda_search=True,
                                           gam_columns=['annual_inc','loan_amnt'],
                                           num_knots=[5,5],
                                           bs=[0,0],
                                           scale=[0.1,0.1])
    # train 
    loan_gam.train(X, y, training_frame=train)

    # measure train and test accuracy
    print("GLM training logloss: {0}, GAM training logloss: {1}".format(loan_glm.logloss(), loan_gam.logloss()))
    print("GLM test logloss: {0}, GAM test logloss: {1}".format(loan_glm.model_performance(test_data=test).logloss(),
                                                                loan_gam.model_performance(test_data=test).logloss()))
    # measure train and test accuracy
    print("GLM training accuracy: {0}, GAM training accuracy: {1}".format(loan_glm.accuracy(train=True), 
                                                                          loan_gam.accuracy(train=True)))
    print("GLM test accuracy: {0}, GAM test accuracy: {1}".format(loan_glm.model_performance(test_data=test).accuracy(),
                                                                  loan_gam.model_performance(test_data=test).accuracy()))
    print("GLM training AUC: {0}, GAM training AUC: {1}".format(loan_glm.auc(train=True),
                                                                          loan_gam.auc(train=True)))
    print("GLM test AUC: {0}, GAM test AUC: {1}".format(loan_glm.model_performance(test_data=test).auc(),
                                                                  loan_gam.model_performance(test_data=test).auc()))
    print("GLM training PR-AUC: {0}, GAM training PR-AUC: {1}".format(loan_glm.pr_auc(train=True),
                                                                loan_gam.pr_auc(train=True)))
    print("GLM test PR-AUC: {0}, GAM test PR-AUC: {1}".format(loan_glm.model_performance(test_data=test).pr_auc(),
                                                    loan_gam.model_performance(test_data=test).pr_auc()))
    print("GLM training AIC: {0}, GAM training AIC: {1}".format(loan_glm.aic(train=True),
                                                                      loan_gam.aic(train=True)))
    print("GLM test AIC: {0}, GAM test AIC: {1}".format(loan_glm.model_performance(test_data=test).aic(),
                                                          loan_gam.model_performance(test_data=test).aic()))
    print("******* GLM variable importance: ")
    print(loan_glm.varimp())
    print("******* GAM variable importance: ")
    print(loan_gam.varimp())
    print("******* GLM confusion matrix: ")
    print(loan_glm.confusion_matrix())
    print("******* GAM confusion matrix: ")
    print(loan_gam.confusion_matrix())
    assert loan_glm.logloss()>=loan_gam.logloss() or abs(loan_glm.logloss()-loan_gam.logloss())<0.05,\
        "GAM logloss: {0}, exceeds GLM logloss: {1} by too much.".format(loan_gam.logloss(), loan_glm.logloss())

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_gam_model_predict)
else:
    test_gam_model_predict()
