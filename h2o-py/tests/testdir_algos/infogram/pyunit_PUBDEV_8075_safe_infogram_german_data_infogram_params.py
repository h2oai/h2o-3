from __future__ import print_function
import os
import sys

sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from h2o.estimators.infogram import H2OInfogram
from tests import pyunit_utils
    
def test_infogram_german_data():
    """
    Simple german data test to check that safe infogram is working:
     1. it generates the correct lists as Deep's original code.  
     2. when model and infogram parameters are specified, it uses the correct specification.
    :return: 
    """
    deep_rel = [1.00000000, 0.58302027, 0.43431236, 0.66177924, 0.53677082, 0.25084764, 0.34379833, 0.13251726, 
               0.11473028, 0.09548423, 0.20398740, 0.16432640, 0.06875276, 0.04870468, 0.12573930, 0.01382682, 
               0.04496173, 0.01273963]
    deep_cmi = [0.84946975, 0.73020930, 0.58553936, 0.75780528, 1.00000000, 0.38461582, 0.57575695, 0.30663930, 
               0.07604779, 0.19979514, 0.42293369, 0.20628365, 0.25316918, 0.15096705, 0.24501686, 0.11296778, 
               0.13068605, 0.03841617]
    fr = h2o.import_file(path=pyunit_utils.locate("smalldata/admissibleml_test/german_credit.csv"))
    target = "BAD"
    fr[target] = fr[target].asfactor()
    x = fr.names
    x.remove(target)
    x.remove("status_gender")
    x.remove( "age")
    infogram_model = H2OInfogram(seed = 12345, protected_columns=["status_gender", "age"], top_n_features=50)
    infogram_model.train(x=x, y=target, training_frame=fr)

    # make sure our result matches Deep's
    pred_names, rel = infogram_model.get_all_predictor_relevance()
    x, cmi = infogram_model.get_all_predictor_cmi()
    assert deep_rel.sort() == rel.sort(), "Expected: {0}, actual: {1}".format(deep_rel, rel)
    assert deep_cmi.sort() == cmi.sort(), "Expected: {0}, actual: {1}".format(deep_cmi, cmi)

    gbm_params = {'ntrees':3}
    infogram_model_gbm_glm = H2OInfogram(seed = 12345, protected_columns=["status_gender", "age"], top_n_features=50, 
                                                  algorithm='gbm', algorithm_params=gbm_params)
    infogram_model_gbm_glm.train(x=x, y=target, training_frame=fr)
    x, cmi_gbm_glm = infogram_model_gbm_glm.get_all_predictor_cmi()
    assert abs(cmi_gbm_glm[1]-cmi[1]) > 0.01, "CMI from infogram model with gbm using different number of trees should" \
                                              " be different but are not."
    
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
    pyunit_utils.standalone_test(test_infogram_german_data)
else:
    test_infogram_german_data()
