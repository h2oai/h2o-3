from __future__ import print_function
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator


def score_history_test():
    air_train = h2o.import_file(path=pyunit_utils.locate("smalldata/airlines/AirlinesTrain.csv.zip"))
    air_train["fDayOfWeek"]=air_train["fDayOfWeek"].asfactor()
    gbm_mult =H2OGradientBoostingEstimator(distribution="multinomial")
    gbm_mult.train(x=["Origin", "Dest", "Distance", "UniqueCarrier", "IsDepDelayed", "fDayofMonth", "fMonth"],y="fDayOfWeek",training_frame=air_train)
    score_history = gbm_mult.scoring_history()
    print(score_history)



if __name__ == "__main__":
    pyunit_utils.standalone_test(score_history_test)
else:
    score_history_test()
