from __future__ import print_function
from builtins import range
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator

#This test is used to check if a model summary can be extracted and manipulated
def model_summary():

    df = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    df.describe()

    #Remove ID from training frame
    train = df.drop("ID")

    #For VOL & GLEASON, a zero really means "missing"
    vol = train['VOL']
    vol[vol == 0] = None
    gle = train['GLEASON']
    gle[gle == 0] = None

    #Convert CAPSULE to a logical factor
    train['CAPSULE'] = train['CAPSULE'].asfactor()

    #See that the data is ready
    train.describe()

    #Run GBM
    my_gbm = H2OGradientBoostingEstimator(ntrees=50,
                                          learn_rate=0.1,
                                          distribution="bernoulli")
    my_gbm.train(x=list(range(1, train.ncol)),
                 y="CAPSULE",
                 training_frame=train,
                 validation_frame=train)

    summary = my_gbm.summary()

    #Set empty array that is size of model summary and try to extract metrics. If successful then we are returning
    #a model summary that can be manipulated for further use.
    metrics = [None] * 10
    for i in range(0,10):
        metrics[i] = summary[i]

    #Check extracted are actual
    for i in range(0,10):
        assert metrics[i] == summary[i], "Expected equal metrics in model summary and extracted model summary " \
                                         "but got: {0}".format(metrics[i])

if __name__ == "__main__":
    pyunit_utils.standalone_test(model_summary)
else:
    model_summary()
