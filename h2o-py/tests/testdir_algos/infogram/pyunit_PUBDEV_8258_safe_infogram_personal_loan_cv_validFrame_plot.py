from __future__ import print_function
import os
import sys

sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from h2o.estimators.infogram import H2OInfogram
from tests import pyunit_utils
    
def test_infogram_personal_loan_cv_valid():
    """
    Make sure safe infogram plot works with cv and validation dataset.
    """
    fr = h2o.import_file(path=pyunit_utils.locate("smalldata/admissibleml_test/Bank_Personal_Loan_Modelling.csv"))
    target = "Personal Loan"
    fr[target] = fr[target].asfactor()
    x = ["Experience","Income","Family","CCAvg","Education","Mortgage",
         "Securities Account","CD Account","Online","CreditCard"]
    splits = fr.split_frame(ratios=[0.80])
    train = splits[0]
    test = splits[1]
    infogram_model_cv_v = H2OInfogram(seed = 12345, protected_columns=["Age","ZIP Code"], nfolds=5) 
    infogram_model_cv_v.train(x=x, y=target, training_frame=train, validation_frame=test) # cross-validation, validation
    infogram_model_cv_v.plot(title="Infogram calcuated from training dataset", server=True) # plot infogram from training dataset
    infogram_model_cv_v.plot(train=True, valid=True, title="Infogram calculated from training/validation dataset", 
                             server=True) # plot infogram from validation dataset
    infogram_model_cv_v.plot(train=True, valid=True, xval=True, title="Infogram calculated from "
                                                                      "training/validation/xval holdout dataset",
                             server=True) # plot infogram from cv hold out dataset
    relcmi_train = infogram_model_cv_v.get_admissible_score_frame()
    relcmi_valid = infogram_model_cv_v.get_admissible_score_frame(valid=True)
    assert relcmi_train.nrow==relcmi_valid.nrow
    
if __name__ == "__main__":
    pyunit_utils.standalone_test(test_infogram_personal_loan_cv_valid)
else:
    test_infogram_personal_loan_cv_valid()
