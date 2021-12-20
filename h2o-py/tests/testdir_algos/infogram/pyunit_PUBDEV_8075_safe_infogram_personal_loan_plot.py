from __future__ import print_function
import os
import sys
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from h2o.estimators.infogram import H2OInfogram
from tests import pyunit_utils
    
def test_infogram_personal_loan_plot():
    """
    checking plotting function of infogram for fair model
    """
    fr = h2o.import_file(path=pyunit_utils.locate("smalldata/admissibleml_test/Bank_Personal_Loan_Modelling.csv"))
    target = "Personal Loan"
    fr[target] = fr[target].asfactor()
    x = ["Experience","Income","Family","CCAvg","Education","Mortgage",
         "Securities Account","CD Account","Online","CreditCard"]
    infogram_model = H2OInfogram(seed = 12345, protected_columns=["Age","ZIP Code"])
    infogram_model.train(x=x, y=target, training_frame=fr)
    infogram_model.plot(server=True)

    infogram_model2 = H2OInfogram(seed = 12345, protected_columns=["Age","ZIP Code"], safety_index_threshold=0.05,
                                  relevance_index_threshold=0.05)
    infogram_model2.train(x=x, y=target, training_frame=fr)
    infogram_model2.plot(server=True)
    assert len(infogram_model.get_admissible_cmi()) <= len(infogram_model2.get_admissible_cmi())
    
if __name__ == "__main__":
    pyunit_utils.standalone_test(test_infogram_personal_loan_plot)
else:
    test_infogram_personal_loan_plot()
