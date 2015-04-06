import sys
sys.path.insert(1, "../../")
import h2o

def rf_balance_classes(ip,port):
    # Connect to a pre-existing cluster
    h2o.init(ip,port)

    def model(model_object):
        #predicting on test file
        pred = model_object.predict(air_test)
        pred.head()
        #Building confusion matrix for test set
        perf = model_object.model_performance(air_test)
        perf.show()
        print(perf.confusion_matrices())
        print(perf.precision())
        print(perf.accuracy())
        print(perf.auc())

    #uploading data file to h2o
    air = h2o.import_frame(path=h2o.locate("smalldata/airlines/AirlinesTrain.csv.zip"))

    #Constructing validation and train sets by sampling (20/80)
    #creating a column as tall as airlines(nrow(air))
    r = air[0].runif()
    air_train = air[r < 0.8]
    air_valid = air[r >= 0.8]

    myX = ["Origin", "Dest", "Distance", "UniqueCarrier", "fMonth", "fDayofMonth", "fDayOfWeek"]
    myY = "IsDepDelayed"

    rf_no_bal = h2o.random_forest(x=air_train[myX], y=air_train[myY], validation_x= air_valid[myX],
                               validation_y=air_valid[myY], seed=12, ntrees=10, max_depth=20, balance_classes=False)
    rf_no_bal.show()

    rf_bal = h2o.random_forest(x=air_train[myX], y=air_train[myY], validation_x= air_valid[myX],
                               validation_y=air_valid[myY], seed=12, ntrees=10, max_depth=20, balance_classes=True)
    rf_bal.show()

    #uploading test file to h2o
    air_test = h2o.import_frame(path=h2o.locate("smalldata/airlines/AirlinesTest.csv.zip"))

    print("\n\nWITHOUT CLASS BALANCING\n")
    model(rf_no_bal)

    print("\n\nWITH CLASS BALANCING\n")
    model(rf_bal)

if __name__ == "__main__":
    h2o.run_test(sys.argv, rf_balance_classes)
