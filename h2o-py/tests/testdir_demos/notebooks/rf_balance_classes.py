# This is a demo of H2O's GLM function
# It imports a data set, parses it, and prints a summary
# Then, it runs GLM with a binomial link function
import h2o
air = h2o.upload_file(path=h2o.locate("smalldata/airlines/AirlinesTrain.csv.zip"))
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
air_test = h2o.import_frame(path=h2o.locate("smalldata/airlines/AirlinesTest.csv.zip"))
def model(model_object, test):
        #predicting on test file
        pred = model_object.predict(test)
        pred.head()
        #Building confusion matrix for test set
        perf = model_object.model_performance(test)
        perf.show()
        print(perf.confusion_matrix())
        print(perf.precision())
        print(perf.accuracy())
        print(perf.auc())
print("\n\nWITHOUT CLASS BALANCING\n")
model(rf_no_bal, air_test)
print("\n\nWITH CLASS BALANCING\n")
model(rf_bal, air_test)
