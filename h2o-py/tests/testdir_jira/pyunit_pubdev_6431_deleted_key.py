import h2o

from h2o.estimators.glm import H2OGeneralizedLinearEstimator
from h2o.grid.grid_search import H2OGridSearch
from tests import pyunit_utils


def pubdev_6431_deleted_key():
    frame = h2o.import_file(pyunit_utils.locate("smalldata/logreg/benign.csv"))
    Y = 3
    X = [0, 1, 2, 4, 5, 6, 7, 8, 9, 10]
    model = H2OGeneralizedLinearEstimator(family="binomial", alpha=0, Lambda=1e-5)
    model.train(x=X, y=Y, training_frame=frame)
    assert 3 == len(h2o.cluster().list_jobs()[0])

    h2o.rm(model)
    assert 3 == len(h2o.cluster().list_jobs()[0])

    hyper_parameters = {
        'alpha': [0, 0.3],
    }
    grid_search = H2OGridSearch(H2OGeneralizedLinearEstimator, hyper_params=hyper_parameters)
    grid_search.train(x=X, y=Y, training_frame=frame)
    assert 3 == len(h2o.cluster().list_jobs()[0])


if __name__ == "__main__":
    pyunit_utils.standalone_test(pubdev_6431_deleted_key)
else:
    pubdev_6431_deleted_key()
