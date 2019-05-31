import h2o

from h2o.estimators.glm import H2OGeneralizedLinearEstimator
from h2o.grid.grid_search import H2OGridSearch
from tests import pyunit_utils


def pubdev_6431_deleted_key():
    jobs_count_start = len(h2o.cluster().list_jobs()[0])

    input_frame = h2o.import_file(pyunit_utils.locate("smalldata/flow_examples/abalone.csv.gz"))
    model = H2OGeneralizedLinearEstimator(alpha=0.3)
    model.train(y="C9", training_frame=input_frame, validation_frame=input_frame)
    assert (jobs_count_start+2) == len(h2o.cluster().list_jobs()[0]), \
        "(after train) expected {0} jobs but found {1} - {2}".format((jobs_count_start+2), len(h2o.cluster().list_jobs()[0]), h2o.cluster().list_jobs())

    h2o.remove(model)
    assert (jobs_count_start+2) == len(h2o.cluster().list_jobs()[0]), \
        "(after rm) expected {0} jobs but found {1} - {2}".format((jobs_count_start+2), len(h2o.cluster().list_jobs()[0]), h2o.cluster().list_jobs())

    hyper_parameters = {
        'alpha': [0, 0.3],
    }
    grid_search = H2OGridSearch(H2OGeneralizedLinearEstimator, hyper_params=hyper_parameters)
    grid_search.train(y="C9", training_frame=input_frame, validation_frame=input_frame)
    assert (jobs_count_start+3) == len(h2o.cluster().list_jobs()[0]), \
        "(after grid) expected {0} jobs but found {1}- {2}".format((jobs_count_start+3), len(h2o.cluster().list_jobs()[0]), h2o.cluster().list_jobs())


if __name__ == "__main__":
    pyunit_utils.standalone_test(pubdev_6431_deleted_key)
else:
    pubdev_6431_deleted_key()
