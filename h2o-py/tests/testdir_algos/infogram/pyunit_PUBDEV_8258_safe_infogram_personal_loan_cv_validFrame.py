from __future__ import print_function
import os
import sys

sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from h2o.estimators.infogram import H2OInfogram
from tests import pyunit_utils
    
def test_infogram_personal_loan_cv_valid():
    """
    Make sure safe infogram works with validation frame and supports cross-validation
    """
    fr = h2o.import_file(path=pyunit_utils.locate("smalldata/admissibleml_test/Bank_Personal_Loan_Modelling.csv"))
    target = "Personal Loan"
    fr[target] = fr[target].asfactor()
    x = ["Experience","Income","Family","CCAvg","Education","Mortgage",
         "Securities Account","CD Account","Online","CreditCard"]
    splits = fr.split_frame(ratios=[0.80])
    train = splits[0]
    test = splits[1]
    infogram_model = H2OInfogram(seed = 12345, protected_columns=["Age","ZIP Code"]) # model on training dataset
    infogram_model.train(x=x, y=target, training_frame=train)
    infogram_model_v = H2OInfogram(seed = 12345, protected_columns=["Age","ZIP Code"]) # model with validation dataset
    infogram_model_v.train(x=x, y=target, training_frame=train, validation_frame=test)
    infogram_model_cv = H2OInfogram(seed = 12345, protected_columns=["Age","ZIP Code"], nfolds=3) 
    infogram_model_cv.train(x=x, y=target, training_frame=train)  # model with cross-vdaliation
    infogram_model_cv_v = H2OInfogram(seed = 12345, protected_columns=["Age","ZIP Code"], nfolds=3) 
    infogram_model_cv_v.train(x=x, y=target, training_frame=train, validation_frame=test) # cross-validation, validation
    
    print("compare rel cmi from training dataset")
    relcmi_train = infogram_model.get_relevance_cmi_frame()
    relcmi_train_v = infogram_model_v.get_relevance_cmi_frame()
    relcmi_train_cv = infogram_model_cv.get_relevance_cmi_frame()
    relcmi_train_cv_v = infogram_model_cv_v.get_relevance_cmi_frame()
    pyunit_utils.compare_frames_local(relcmi_train, relcmi_train_v, prob=1.0)
    pyunit_utils.compare_frames_local(relcmi_train_cv, relcmi_train_cv_v, prob=1.0)
    pyunit_utils.compare_frames_local(relcmi_train_cv, relcmi_train, prob=1.0)

    print("compare rel cmi from validation dataset")
    relcmi_valid_v = infogram_model_v.get_relevance_cmi_frame(valid=True)
    relcmi_valid_cv_v = infogram_model_cv_v.get_relevance_cmi_frame(valid=True)
    pyunit_utils.compare_frames_local(relcmi_valid_v, relcmi_valid_cv_v, prob=1.0)
    
    print("compare rel cmi from cross-validation hold out")
    relcmi_cv_cv = infogram_model_cv.get_relevance_cmi_frame(xval=True)
    relcmi_cv_cv_v = infogram_model_cv_v.get_relevance_cmi_frame(xval=True)
    pyunit_utils.compare_frames_local(relcmi_cv_cv, relcmi_cv_cv_v, prob=1.0)
    
if __name__ == "__main__":
    pyunit_utils.standalone_test(test_infogram_personal_loan_cv_valid)
else:
    test_infogram_personal_loan_cv_valid()
