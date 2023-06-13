from __future__ import print_function
import os
import sys

sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from h2o.estimators.infogram import H2OInfogram
from tests import pyunit_utils
    
def test_cv_warning_messages():
    """
    In this test, I will enable cv with a validation dataset.  There should not be warning on weights because the
    training set does not contains weight column even though cv is enabled.
    """
    fr = h2o.import_file(path=pyunit_utils.locate("smalldata/admissibleml_test/Bank_Personal_Loan_Modelling.csv"))
    target = "Personal Loan"
    fr[target] = fr[target].asfactor()
    x = ["Experience","Income","Family","CCAvg","Education","Mortgage",
         "Securities Account","CD Account","Online","CreditCard"]
    splits = fr.split_frame(ratios=[0.80])
    train = splits[0]
    test = splits[1]
    infogram_model_cv_v = H2OInfogram(seed = 12345, protected_columns=["Age","ZIP Code"], nfolds=3) 
    infogram_model_cv_v.train(x=x, y=target, training_frame=train, validation_frame=test)
    
    pyunit_utils.checkLogWeightWarning("infogram_internal_cv_weights_", wantWarnMessage=False)
    
    
if __name__ == "__main__":
    pyunit_utils.standalone_test(test_cv_warning_messages)
else:
    test_cv_warning_messages()
