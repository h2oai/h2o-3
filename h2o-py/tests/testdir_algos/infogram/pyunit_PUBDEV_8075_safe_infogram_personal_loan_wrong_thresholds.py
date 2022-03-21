from __future__ import print_function
import os
import sys

sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from h2o.estimators.infogram import H2OInfogram
from tests import pyunit_utils
    
def test_infogram_personal_loan():
    """
    Simple Perosnal loan test to check that when wrong thresholds are specified, warnings should be
    generated.
    :return: 
    """
    fr = h2o.import_file(path=pyunit_utils.locate("smalldata/admissibleml_test/Bank_Personal_Loan_Modelling.csv"))
    target = "Personal Loan"
    fr[target] = fr[target].asfactor()
    x = ["Experience","Income","Family","CCAvg","Education","Mortgage",
         "Securities Account","CD Account","Online","CreditCard"]
    with pyunit_utils.catch_warnings() as ws:
        infogram_model = H2OInfogram(seed = 12345, protected_columns=["Age","ZIP Code"], top_n_features=len(x),
                                     net_information_threshold=0.2, total_information_threshold=0.2)
        infogram_model.train(x=x, y=target, training_frame=fr)
        assert len(ws) == 2, "Expected two warnings but received {0} warnings instead.".format(len(ws))
        assert pyunit_utils.contains_warning(ws, 'information_threshold for fair infogram runs.')

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_infogram_personal_loan)
else:
    test_infogram_personal_loan()
