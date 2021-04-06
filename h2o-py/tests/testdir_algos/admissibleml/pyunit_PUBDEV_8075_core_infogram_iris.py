from __future__ import print_function
import os
import sys

from h2o.estimators.infogram import H2OInfogram

sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils
    
def test_infogram_iris():
    """
    Simple Iris test to check that core infogram is working:
     1. it generates the correct lists as Deep's original code.  
     2. check and make sure the frame contains the correct information.
     3. check the admissible features contains cmi and relevance >= 0.1
    :return: 
    """
    deep_rel = [0.009010006, 0.011170417, 0.755170945, 1.000000000]
    deep_cmi = [0.1038524, 0.7135458, 0.5745915, 1.0000000]
    fr = h2o.import_file(path=pyunit_utils.locate("smalldata/admissibleml_test/irisROriginal.csv"))
    target = "Species"
    fr[target] = fr[target].asfactor()
    x = fr.names
    x.remove(target)
    
    infogram_model = H2OInfogram(seed = 12345, distribution = 'multinomial') # build infogram model with default settings
    infogram_model.train(x=x, y=target, training_frame=fr)
        
    # make sure frame returning all predictors, relevance and cmi contains correct value
    pred_names, rel = infogram_model.get_all_predictor_relevance()
    x, cmi = infogram_model.get_all_predictor_cmi()
    predictor_rel_cmi_frame = infogram_model.get_relevance_cmi_frame()  # get relevance and cmi frame
    assert_list_frame_equal(cmi, rel, predictor_rel_cmi_frame)

    # make sure our result matches Deep's
    assert deep_rel.sort() == rel.sort(), "Expected: {0}, actual: {1}".format(deep_rel, rel)
    assert deep_cmi.sort() == cmi.sort(), "Expected: {0}, actual: {1}".format(deep_cmi, cmi)
    
    # check admissible features values >= 0.1
    admissible_rel = infogram_model.get_admissible_relevance()
    admissible_cmi = infogram_model.get_admissible_cmi()
    for index in range(0, len(admissible_rel)):
        assert admissible_rel[index] >= 0.1, "Admissible relevance should equal or exceed 0.1 but is not.  Actual admissible" \
                                      " relevance is {0}".format(admissible_rel[index])
        assert admissible_cmi[index] >= 0.1, "Admissible cmi should equal or exceed 0.1 but is not.  Actual admissible cmi" \
                                      " is {0}".format(admissible_cmi[index])
    

def assert_list_frame_equal(cmi, rel, predictor_rel_cmi_frame, tol=1e-6):
    rel_frame = predictor_rel_cmi_frame[3].as_data_frame(use_pandas=False)
    cmi_frame = predictor_rel_cmi_frame[4].as_data_frame(use_pandas=False)
    count = 1
    for one_cmi in cmi:
        assert abs(float(cmi_frame[count][0])-one_cmi) < tol, "expected: {0}, actual: {1} and they are " \
                                                              "different".format(float(cmi_frame[count][0]), one_cmi) 
        assert abs(float(rel_frame[count][0])-rel[count-1]) < tol, "expected: {0}, actual: {1} and they are " \
                                                                   "different".format(float(rel_frame[count][0]), rel[count-1])
        count += 1


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_infogram_iris)
else:
    test_infogram_iris()
