import sys, os

sys.path.insert(1, os.path.join("..","..","..",".."))
import h2o
from h2o.estimators import H2ODeepLearningEstimator, H2OAutoEncoderEstimator
from tests import pyunit_utils as pu


def test_deeplearning_works_as_autoencoder():
    df = h2o.import_file(path=pu.locate("smalldata/iris/iris_wheader.csv"))
    ae = H2ODeepLearningEstimator(autoencoder=True, hidden=[4], seed=1234)
    ae.train(x=[0, 1, 2, 3], training_frame=df)


def test_autoencoder_works():
    df = h2o.import_file(path=pu.locate("smalldata/iris/iris_wheader.csv"))
    ae = H2OAutoEncoderEstimator(hidden=[4], seed=1234)
    ae.train(x=[0, 1, 2, 3], training_frame=df)


pu.run_tests([
    test_deeplearning_works_as_autoencoder,
    test_autoencoder_works,
])
