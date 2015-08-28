import sys
sys.path.insert(1, "../../")
import h2o, tests

def plot_test(ip,port):
    
    
    kwargs = {}
    kwargs['server'] = True

    air = h2o.import_file(h2o.locate("smalldata/airlines/AirlinesTrain.csv.zip"))

    # Constructing test and train sets by sampling (20/80)
    s = air[0].runif()
    air_train = air[s <= 0.8]
    air_valid = air[s > 0.8]

    myX = ["Origin", "Dest", "Distance", "UniqueCarrier", "fMonth", "fDayofMonth", "fDayOfWeek"]
    myY = "IsDepDelayed"

    air_gbm = h2o.gbm(x=air_train[myX], y=air_train[myY], validation_x=air_valid[myX], validation_y=air_valid[myY],
                      distribution="bernoulli", ntrees=100, max_depth=3, learn_rate=0.01)

    # Plot ROC for training and validation sets
    air_gbm.plot(type="roc", train=True, **kwargs)
    air_gbm.plot(type="roc", valid=True, **kwargs)

    air_test = h2o.import_file(h2o.locate("smalldata/airlines/AirlinesTest.csv.zip"))
    perf = air_gbm.model_performance(air_test)

    #Plot ROC for test set
    perf.plot(type="roc", **kwargs)

if __name__ == "__main__":
    tests.run_test(sys.argv, plot_test)
