from __future__ import print_function
import os
import sys

sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from h2o.estimators.infogram import H2OInfogram
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
from tests import pyunit_utils
    
def test_infogram_iris_x_attributes():
    """
    Test to showcase that we can specify predictors using infogram model
    """
    fr = h2o.import_file(path=pyunit_utils.locate("smalldata/admissibleml_test/irisROriginal.csv"))
    target = "Species"
    fr[target] = fr[target].asfactor()
    x = fr.names
    x.remove(target)
    
    infogram_model = H2OInfogram(seed = 12345, distribution = 'multinomial') # build infogram model with default settings
    infogram_model.train(x=x, y=target, training_frame=fr)
    
    glm_model1 = H2OGeneralizedLinearEstimator(family = 'multinomial')
    glm_model1.train(x=infogram_model._extract_x_from_model(), y=target, training_frame=fr)
    coef1 = glm_model1.coef()
    glm_model2 = H2OGeneralizedLinearEstimator(family = 'multinomial')
    glm_model2.train(x=infogram_model, y=target, training_frame=fr)
    coef2 = glm_model2.coef()
    coef_classes = coef1.keys()
    for key in coef_classes:
        pyunit_utils.assertCoefDictEqual(coef1[key], coef2[key], tol=1e-6)

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_infogram_iris_x_attributes)
else:
    test_infogram_iris_x_attributes()
