import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator

def hit_ratio_test():
    air_train = h2o.import_file(path=pyunit_utils.locate("smalldata/airlines/AirlinesTrain.csv.zip"))
    air_valid = h2o.import_file(path=pyunit_utils.locate("smalldata/airlines/AirlinesTest.csv.zip"))
    air_test = h2o.import_file(path=pyunit_utils.locate("smalldata/airlines/AirlinesTest.csv.zip"))
    air_train["fDayOfWeek"] = air_train["fDayOfWeek"].asfactor()
    air_valid["fDatOfWeek"] = air_valid["fDayOfWeek"].asfactor()

    gbm_mult = H2OGradientBoostingEstimator(distribution="multinomial")
    gbm_mult.train(x=["Origin", "Dest", "Distance", "UniqueCarrier", "IsDepDelayed", "fDayofMonth","fMonth"], y= "fDayOfWeek",
                   training_frame=air_train, validation_frame=air_valid)

    training_hit_ratio_table = gbm_mult.hit_ratio_table(train=True)
    training_hit_ratio_table.show()

    validation_hit_ratio_table = gbm_mult.hit_ratio_table(valid=True)
    validation_hit_ratio_table.show()

    perf = gbm_mult.model_performance(air_test)
    test_hit_ratio_table = perf.hit_ratio_table()
    test_hit_ratio_table.show()


if __name__ == "__main__":
    pyunit_utils.standalone_test(hit_ratio_test)
else:
    hit_ratio_test()
