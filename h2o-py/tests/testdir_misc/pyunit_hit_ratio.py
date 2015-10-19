

import h2o, tests

def hit_ratio_test():
    
    

    air_train = h2o.import_file(path=tests.locate("smalldata/airlines/AirlinesTrain.csv.zip"))
    air_valid = h2o.import_file(path=tests.locate("smalldata/airlines/AirlinesTest.csv.zip"))
    air_test = h2o.import_file(path=tests.locate("smalldata/airlines/AirlinesTest.csv.zip"))

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


pyunit_test = hit_ratio_test
