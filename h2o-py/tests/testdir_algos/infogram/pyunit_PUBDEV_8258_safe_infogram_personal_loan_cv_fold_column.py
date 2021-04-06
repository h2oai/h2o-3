from __future__ import print_function
import os
import sys

sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from h2o.estimators.infogram import H2OInfogram
from tests import pyunit_utils
    
def test_infogram_personal_loan_cv_fold_column():
    """
    Make sure safe infogram works with validation frame and supports cross-validation
    """
    fr = h2o.import_file(path=pyunit_utils.locate("smalldata/admissibleml_test/Bank_Personal_Loan_Modelling.csv"))
    target = "Personal Loan"
    fr[target] = fr[target].asfactor()
    x = ["Experience","Income","Family","CCAvg","Education","Mortgage",
         "Securities Account","CD Account","Online","CreditCard"]
    n_fold = 3
    infogram_model_cv = H2OInfogram(seed = 12345, protected_columns=["Age","ZIP Code"], nfolds=n_fold, 
                                    fold_assignment='modulo') 
    infogram_model_cv.train(x=x, y=target, training_frame=fr)  # model with cross-vdaliation

    fold_numbers = fr.modulo_kfold_column(n_folds=n_fold)
    fold_numbers.set_names(["fold_numbers"])
    fr = fr.cbind(fold_numbers)
    
    infogram_model_cv_fold_column = H2OInfogram(seed = 12345, protected_columns=["Age","ZIP Code"], fold_column="fold_numbers") 
    infogram_model_cv_fold_column.train(x=x, y=target, training_frame=fr) # cross-validation, validation
    
    print("compare rel cmi from training dataset")
    relcmi_train_cv = infogram_model_cv.get_relevance_cmi_frame()
    relcmi_train_cv_fold_column = infogram_model_cv_fold_column.get_relevance_cmi_frame()
    pyunit_utils.compare_frames_local(relcmi_train_cv, relcmi_train_cv_fold_column, prob=1.0)
    
    print("compare rel cmi from cross-validation hold out")
    relcmi_cv_cv = infogram_model_cv.get_relevance_cmi_frame(xval=True)
    relcmi_cv_cv_fold_column = infogram_model_cv_fold_column.get_relevance_cmi_frame(xval=True)
    pyunit_utils.compare_frames_local(relcmi_cv_cv, relcmi_cv_cv_fold_column, prob=1.0)
    
if __name__ == "__main__":
    pyunit_utils.standalone_test(test_infogram_personal_loan_cv_fold_column)
else:
    test_infogram_personal_loan_cv_fold_column()
