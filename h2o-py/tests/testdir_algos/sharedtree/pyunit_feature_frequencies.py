from builtins import range
import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from h2o.estimators.random_forest import H2ORandomForestEstimator
from h2o.estimators.isolation_forest import H2OIsolationForestEstimator


def feature_frequencies():

    prostate_train = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate_train.csv"))
    prostate_train["CAPSULE"] = prostate_train["CAPSULE"].asfactor()
    features = list(range(1,prostate_train.ncol))

    gbm = H2OGradientBoostingEstimator(ntrees=5)
    gbm.train(x=features, y="CAPSULE", training_frame=prostate_train)
    ff_gbm = gbm.feature_frequencies(prostate_train)
    
    print(ff_gbm.shape)
    
    assert ff_gbm.shape == (prostate_train.nrow, len(features))
    
    drf = H2ORandomForestEstimator(ntrees=5)
    drf.train(x=features, y="CAPSULE", training_frame=prostate_train)
    ff_drf = drf.feature_frequencies(prostate_train)

    assert ff_drf.shape == (prostate_train.nrow, len(features))

    iforest = H2OIsolationForestEstimator(ntrees=5)
    iforest.train(x=features, training_frame=prostate_train)
    ff_iforest = drf.feature_frequencies(prostate_train)

    assert ff_iforest.shape == (prostate_train.nrow, len(features))


if __name__ == "__main__":
    pyunit_utils.standalone_test(feature_frequencies)
else:
    feature_frequencies()
