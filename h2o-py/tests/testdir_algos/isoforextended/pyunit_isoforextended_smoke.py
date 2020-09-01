from __future__ import print_function
import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils
from h2o.estimators.extended_isolation_forest import H2OExtendedIsolationForestEstimator


def extended_isolation_forest():
    print("Extended Isolation Forest Smoke Test")

    train = h2o.import_file(pyunit_utils.locate("smalldata/anomaly/single_blob.csv"))

    eif_model = H2OExtendedIsolationForestEstimator(ntrees=7, seed=12, sample_size=5)
    eif_model.train(training_frame=train)
    anomaly_score = eif_model.predict(train)
    anomaly = anomaly_score['anomaly_score'].as_data_frame(use_pandas=True)["anomaly_score"]

    print(eif_model)
    
    # The output of the EIF algorithm is based on randomly generated values. 
    # If the randomization is changed, then the output can be slightly different and it is fine to update them.
    # The link to source paper: https://arxiv.org/pdf/1811.02141.pdf
    assert (abs(anomaly[0] - 0.54)) <= 0.01, "Not expected output: Expected value is about 0.54"
    assert (abs(anomaly[5] - 0.47)) <= 0.01, "Not expected output: Expected value is about 0.47"
    assert (abs(anomaly[33] - 0.46)) <= 0.01, "Not expected output: Expected value is about 0.46"
    assert (abs(anomaly[256] - 0.50)) <= 0.01, "Not expected output: Expected value is about 0.50"
    assert (abs(anomaly[499] - 0.50)) <= 0.01, "Not expected output: Expected value is about 0.50"
                                  
                                                             
if __name__ == "__main__":        
    pyunit_utils.standalone_test(extended_isolation_forest)
else:
    extended_isolation_forest()
