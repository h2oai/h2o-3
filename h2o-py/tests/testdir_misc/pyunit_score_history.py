import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils




def score_history_test():
    
    

    air_train = h2o.import_file(path=pyunit_utils.locate("smalldata/airlines/AirlinesTrain.csv.zip"))

    gbm_mult = h2o.gbm(x=air_train[["Origin", "Dest", "Distance", "UniqueCarrier", "IsDepDelayed", "fDayofMonth","fMonth"]],
                       y=air_train["fDayOfWeek"].asfactor(),
                       distribution="multinomial")
    score_history = gbm_mult.score_history()
    print score_history



if __name__ == "__main__":
    pyunit_utils.standalone_test(score_history_test)
else:
    score_history_test()
