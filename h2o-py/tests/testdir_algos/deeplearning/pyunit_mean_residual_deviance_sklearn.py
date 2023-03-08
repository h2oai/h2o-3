import os
import sys

from sklearn.metrics import mean_poisson_deviance

import h2o
from h2o.estimators import H2ODeepLearningEstimator
from tests import pyunit_utils, assert_equals

sys.path.insert(1, os.path.join("..", "..", ".."))


def mean_residual_deviance_sklearn():
    print("poisson")
    fre = h2o.import_file(path=pyunit_utils.locate("smalldata/glm_test/freMTPL2freq.csv.zip"))
    fre['VehPower'] = fre['VehPower'].asfactor()
    dle = H2ODeepLearningEstimator(training_frame=fre, response_column="ClaimNb", hidden=[5, 5], epochs=1,
                                   train_samples_per_iteration=-1, validation_frame=fre, activation="Tanh",
                                   distribution="poisson", score_training_samples=0, nfolds=3)
    dle.train(x=fre.col_names[4:12], y="ClaimNb", training_frame=fre, validation_frame=fre)

    dle_mrd = dle.mean_residual_deviance(train=True, valid=True, xval=True)
    assert isinstance(dle_mrd['train'], float), "Expected training mean residual deviance to be a float, but got " \
                                                "{0}".format(type(dle_mrd['train']))
    assert isinstance(dle_mrd['valid'], float), "Expected validation mean residual deviance to be a float, but got " \
                                                "{0}".format(type(dle_mrd['valid']))
    assert isinstance(dle_mrd['xval'], float), "Expected cross-validation mean residual deviance to be a float, " \
                                               "but got {0}".format(type(dle_mrd['xval']))
    print("train: ", dle_mrd['train'])
    print("valid: ", dle_mrd['valid'])
    print("xval: ", dle_mrd['xval'])

    pred = dle.predict(fre)
    sklearn_nrd = mean_poisson_deviance(fre.as_data_frame()["ClaimNb"], pred.as_data_frame()['predict'])
    print("sklearn: ", sklearn_nrd)

    assert_equals(sklearn_nrd, dle_mrd['train'], delta=1e-5)
    assert_equals(sklearn_nrd, dle_mrd['valid'], delta=1e-5)
    assert_equals(sklearn_nrd, dle_mrd['xval'], delta=1e0)


if __name__ == "__main__":
    pyunit_utils.standalone_test(mean_residual_deviance_sklearn)
else:
    mean_residual_deviance_sklearn()
