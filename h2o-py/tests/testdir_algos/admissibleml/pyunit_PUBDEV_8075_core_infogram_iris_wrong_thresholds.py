from __future__ import print_function
import os
import sys

from h2o.estimators.infogram import H2OInfogram

sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils
    
def test_infogram_iris_wrong_thresholds():
    """
    Simple Iris test to check that when wrong thresholds are specified for core infogram, warnings will
    be received
    """
    fr = h2o.import_file(path=pyunit_utils.locate("smalldata/admissibleml_test/irisROriginal.csv"))
    target = "Species"
    fr[target] = fr[target].asfactor()
    x = fr.names
    x.remove(target)
    with pyunit_utils.catch_warnings() as ws:
        infogram_model = H2OInfogram(seed = 12345, distribution = 'multinomial', safety_index_threshold=0.2,
                                     relevance_index_threshold=0.2, top_n_features=len(x)) # build infogram model with default settings
        infogram_model.train(x=x, y=target, training_frame=fr)
        assert len(ws) == 2, "Expected two warnings but received {0} warnings instead.".format(len(ws))
        assert pyunit_utils.contains_warning(ws, 'index_threshold for core infogram runs.')
    
if __name__ == "__main__":
    pyunit_utils.standalone_test(test_infogram_iris_wrong_thresholds)
else:
    test_infogram_iris_wrong_thresholds()
