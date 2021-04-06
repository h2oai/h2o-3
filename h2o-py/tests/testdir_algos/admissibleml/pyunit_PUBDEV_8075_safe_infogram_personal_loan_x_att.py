from __future__ import print_function
import os
import sys

from h2o.estimators.infogram import H2OInfogram
from h2o.estimators.glm import H2OGeneralizedLinearEstimator

sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils
    
def test_infogram_personal_loan():
    """
    Test to make sure predictor can be specified using infogram model. 
    """
    fr = h2o.import_file(path=pyunit_utils.locate("smalldata/admissibleml_test/Bank_Personal_Loan_Modelling.csv"))
    target = "Personal Loan"
    fr[target] = fr[target].asfactor()
    x = ["Experience","Income","Family","CCAvg","Education","Mortgage",
         "Securities Account","CD Account","Online","CreditCard"]
    infogram_model = H2OInfogram(seed = 12345, protected_columns=["Age","ZIP Code"])
    infogram_model.train(x=x, y=target, training_frame=fr)

    glm_model1 = H2OGeneralizedLinearEstimator()
    glm_model1.train(x=infogram_model._extract_x_from_model(), y=target, training_frame=fr)
    coef1 = glm_model1.coef()
    glm_model2 = H2OGeneralizedLinearEstimator()
    glm_model2.train(x=infogram_model, y=target, training_frame=fr)
    coef2 = glm_model2.coef()

    pyunit_utils.assertCoefDictEqual(coef1, coef2, tol=1e-6)


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_infogram_personal_loan)
else:
    test_infogram_personal_loan()
