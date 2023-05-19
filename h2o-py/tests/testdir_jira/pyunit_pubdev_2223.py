import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.deeplearning import H2ODeepLearningEstimator

def pubdev_2223():
    covtype = h2o.import_file(pyunit_utils.locate("smalldata/covtype/covtype.20k.data"))
    covtype[54] = covtype[54].asfactor()
    dlmodel = H2ODeepLearningEstimator(hidden=[17,191], epochs=1, balance_classes=False, reproducible=True, seed=1234, export_weights_and_biases=True)
    dlmodel.train(x=list(range(54)),y=54,training_frame=covtype)

    print("Normalization/Standardization multipliers for numeric predictors: {0}\n".format(dlmodel.normmul()))
    print("Normalization/Standardization offsets for numeric predictors: {0}\n".format(dlmodel.normsub()))
    print("Normalization/Standardization multipliers for numeric response: {0}\n".format(dlmodel.respmul()))
    print("Normalization/Standardization offsets for numeric response: {0}\n".format(dlmodel.respsub()))
    print("Categorical offsets for one-hot encoding: {0}\n".format(dlmodel.catoffsets()))



if __name__ == "__main__":
    pyunit_utils.standalone_test(pubdev_2223)
else:
    pubdev_2223()
