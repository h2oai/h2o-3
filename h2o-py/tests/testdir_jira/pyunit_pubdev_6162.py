
import h2o
import os
from tests import pyunit_utils


def pubdev_6162():
    access_key_id = os.environ['AWS_ACCESS_KEY_ID']
    secret_access_key = os.environ['AWS_SECRET_ACCESS_KEY']
    assert access_key_id is not None
    assert secret_access_key is not None
    
    iris = h2o.import_file("s3a://{}:{}@test.0xdata.com/h2o-unit-tests/iris.csv".format(access_key_id, secret_access_key));
    assert iris is not None
    assert iris.nrow > 0

    two_iris_datasets = h2o.import_file("s3a://{}:{}@test.0xdata.com/h2o-unit-tests/".format(access_key_id, secret_access_key));
    assert two_iris_datasets is not None
    # Should import two files, the original iris.csv and its exact copy, resulting in twice as many rows in the frame
    assert two_iris_datasets.nrow == iris.nrow * 2
    
if __name__ == "__main__":
    pyunit_utils.standalone_test(pubdev_6162)
else:
    pubdev_6162()
