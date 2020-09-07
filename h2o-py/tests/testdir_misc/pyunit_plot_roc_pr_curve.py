import sys
sys.path.insert(1,"../../")
import h2o
import os
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator


def plot_test():
    air = h2o.import_file(pyunit_utils.locate("smalldata/airlines/AirlinesTrain.csv.zip"))

    # Constructing test and train sets by sampling (20/80)
    s = air[0].runif()
    air_train = air[s <= 0.8]
    air_valid = air[s > 0.8]

    myX = ["Origin", "Dest", "Distance", "UniqueCarrier", "fMonth", "fDayofMonth", "fDayOfWeek"]
    myY = "IsDepDelayed"

    air_gbm = H2OGradientBoostingEstimator(distribution="bernoulli", ntrees=100, max_depth=3, learn_rate=0.01)
    air_gbm.train(x=myX, y=myY, training_frame=air_train, validation_frame=air_valid)

    # Plot ROC for train set
    perf_train = air_gbm.model_performance(train=True)
    perf_train.plot(type="roc", server=True)
    perf_train.plot(type="pr", server=True)

    # Plot ROC for valid set
    perf_valid = air_gbm.model_performance(valid=True)
    perf_valid.plot(type="roc", server=True)
    perf_valid.plot(type="pr", server=True)

    # Plot ROC for test set
    air_test = h2o.import_file(pyunit_utils.locate("smalldata/airlines/AirlinesTest.csv.zip"))
    perf_test = air_gbm.model_performance(air_test)
    perf_test.plot(type="roc", server=True)
    perf_test.plot(type="pr", server=True)

    # Test file saving
    fn = "pr_plot.png"
    perf_test.plot(type="roc", server=False, save_to_file=fn)
    if os.path.isfile(fn):
        os.remove(fn)
    perf_test.plot(type="pr", server=False, save_to_file=fn)
    if os.path.isfile(fn):
        os.remove(fn)
        
     # Test no plot parameter
    (fprs, tprs) = perf_test.plot(type="roc", server=True, plot=False)
    assert len(fprs) == len(tprs), "Expected fprs and tprs to have the same shape but they are not."
    (recalls, precisions) = perf_test.plot(type="pr", server=True, plot=False)
    assert len(recalls) == len(precisions), "Expected recall and precision to have the same shape but they are not."

if __name__ == "__main__":
    pyunit_utils.standalone_test(plot_test)
else:
    plot_test()
