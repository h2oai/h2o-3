from __future__ import print_function
from builtins import range
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator
import requests
from h2o.exceptions import H2OConnectionError

job_request_counter = 0


def test_job_poll_retry():

    df = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    train = df.drop("ID")
    train['CAPSULE'] = train['CAPSULE'].asfactor()

    def flaky_request(method, url, **kwargs):
        if method == "GET" and url.find("/3/Jobs/") != -1:
            global job_request_counter
            job_request_counter += 1
            if job_request_counter == 2:
                raise H2OConnectionError("Simulated connection failure")
        return requests.request_orig(method, url, **kwargs)

    try:
        requests.request_orig = requests.request
        requests.request = flaky_request

        my_gbm = H2OGradientBoostingEstimator(ntrees=500, learn_rate=0.0001)
        my_gbm.train(x=list(range(1, train.ncol)),
                     y="CAPSULE", training_frame=train)
    finally:
        requests.request = requests.request_orig

    global job_request_counter
    assert job_request_counter > 2


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_job_poll_retry)
else:
    test_job_poll_retry()
