import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator

EXPECTED_ERROR_MSG = 'Response value for gamma distribution must be greater than 0.'


def pubdev_5495():
    glm = H2OGeneralizedLinearEstimator(family='gamma')
    frame = h2o.import_file(pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    try:
        glm.train(training_frame=frame, y='CAPSULE')
    except h2o.exceptions.H2OResponseError as e:
        assert EXPECTED_ERROR_MSG in e.args[0].dev_msg, "dev_msg should contain '%s'. Actual dev_msg is '%s'" % (EXPECTED_ERROR_MSG, e.args[0].dev_msg)


if __name__ == "__main__":
    pyunit_utils.standalone_test(pubdev_5495)
else:
    pubdev_5495()
