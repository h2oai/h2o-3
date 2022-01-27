import sys
sys.path.insert(1,"../../")
import h2o
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

    air_gbm.plot_gains_lift(server=True)
    air_gbm.plot_gains_lift(type="gains", server=True)
    air_gbm.plot_gains_lift(type="lift", server=True)

    # Plot for train set
    perf_train = air_gbm.model_performance(train=True)
    perf_train.plot(type="gains_lift", server=True)
    perf_train.plot_gains_lift(server=True)
    perf_train.plot_gains_lift(type="gains", server=True)
    perf_train.plot_gains_lift(type="lift", server=True)

    # Plot for valid set
    perf_valid = air_gbm.model_performance(valid=True)
    perf_valid.plot(type="gains_lift", server=True)
    perf_valid.plot_gains_lift(server=True)
    perf_valid.plot_gains_lift(type="gains", server=True)
    perf_valid.plot_gains_lift(type="lift", server=True)


pyunit_utils.standalone_test(plot_test)
