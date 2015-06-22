import sys
sys.path.insert(1, "../../")
import h2o

def hit_ratio_test(ip,port):
    # Connect to h2o
    h2o.init(ip,port)

    air_train = h2o.import_frame(path=h2o.locate("smalldata/airlines/AirlinesTrain.csv.zip"))
    air_valid = h2o.import_frame(path=h2o.locate("smalldata/airlines/AirlinesTest.csv.zip"))
    air_test = h2o.import_frame(path=h2o.locate("smalldata/airlines/AirlinesTest.csv.zip"))

    gbm_mult = h2o.gbm(x=air_train[["Origin", "Dest", "Distance", "UniqueCarrier", "IsDepDelayed", "fDayofMonth","fMonth"]],
                       y=air_train["fDayOfWeek"].asfactor(),
                       validation_x=air_valid[["Origin", "Dest", "Distance", "UniqueCarrier", "IsDepDelayed", "fDayofMonth",
                                              "fMonth"]],
                       validation_y=air_valid["fDayOfWeek"].asfactor(),
                       distribution="multinomial")

    training_hit_ratio_table = gbm_mult.hit_ratio_table(train=True)
    training_hit_ratio_table.show()

    validation_hit_ratio_table = gbm_mult.hit_ratio_table(valid=True)
    validation_hit_ratio_table.show()

    perf = gbm_mult.model_performance(air_test)
    test_hit_ratio_table = perf.hit_ratio_table()
    test_hit_ratio_table.show()

if __name__ == "__main__":
    h2o.run_test(sys.argv, hit_ratio_test)