import sys
sys.path.insert(1, "../../")
import h2o, tests

def score_history_test():
    
    

    air_train = h2o.import_file(path=h2o.locate("smalldata/airlines/AirlinesTrain.csv.zip"))

    gbm_mult = h2o.gbm(x=air_train[["Origin", "Dest", "Distance", "UniqueCarrier", "IsDepDelayed", "fDayofMonth","fMonth"]],
                       y=air_train["fDayOfWeek"].asfactor(),
                       distribution="multinomial")
    score_history = gbm_mult.score_history()
    print score_history

if __name__ == "__main__":
    tests.run_test(sys.argv, score_history_test)
