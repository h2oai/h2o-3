import h2o
# Airlines dataset
air = h2o.import_frame(path=h2o.locate("smalldata/airlines/AirlinesTrain.csv.zip"))
# Construct validation and training datasets by sampling (20/80)
r = air[0].runif()
air_train = air[r < 0.8]
air_valid = air[r >= 0.8]

myX = ["Origin", "Dest", "Distance", "UniqueCarrier", "fMonth", "fDayofMonth", "fDayOfWeek"]
myY = "IsDepDelayed"
# Build gbm
gbm = h2o.gbm(x=air_train[myX], 
              y=air_train[myY], 
              validation_x=air_valid[myX],
              validation_y=air_valid[myY],
              distribution="bernoulli", 
              ntrees=100, 
              max_depth=3, 
              learn_rate=0.01)
# Show various confusion matrices for training dataset (based on metric(s))
print gbm.confusion_matrix() # maximum f1 threshold chosen by default

print gbm.confusion_matrix(metrics="f2")

print gbm.confusion_matrix(metrics="precision")

cms = gbm.confusion_matrix(metrics=["accuracy", "f0point5"])
print cms[0]
print cms[1]
# Show various confusion matrices for training dataset (based on threshold(s))
print gbm.confusion_matrix(thresholds=0.77) 

cms = gbm.confusion_matrix(thresholds=[0.1, 0.5, 0.99])
print cms[0]
print cms[1]
print cms[2]
# Show various confusion matrices for validation dataset (based on metric(s))
print gbm.confusion_matrix(metrics="f2", valid=True)

print gbm.confusion_matrix(metrics="precision", valid=True)

cms = gbm.confusion_matrix(metrics=["accuracy", "f0point5"], valid=True)
print cms[0]
print cms[1]
# Show various confusion matrices for validation dataset (based on threshold(s))
print gbm.confusion_matrix(thresholds=0.77) 

cms = gbm.confusion_matrix(thresholds=[0.25, 0.33, 0.44])
print cms[0]
print cms[1]
print cms[2]
# Show various confusion matrices for validation dataset (based on metric(s) AND threshold(s))
cms = gbm.confusion_matrix(thresholds=0.77, metrics="f1") 
print cms[0]
print cms[1]

cms = gbm.confusion_matrix(thresholds=[0.25, 0.33], metrics=["f2", "f0point5"])
print cms[0]
print cms[1]
print cms[2]
print cms[3]
# Test dataset
air_test = h2o.import_frame(path=h2o.locate("smalldata/airlines/AirlinesTest.csv.zip"))

# Test performance
gbm_perf = gbm.model_performance(air_test)
# Show various confusion matrices for test dataset (based on metric(s))
print gbm_perf.confusion_matrix(metrics="f0point5")

print gbm_perf.confusion_matrix(metrics="min_per_class_accuracy")

cms = gbm_perf.confusion_matrix(metrics=["accuracy", "f0point5"])
print cms[0]
print cms[1]
# Show various confusion matrices for test dataset (based on threshold(s))
print gbm_perf.confusion_matrix(thresholds=0.5) 

cms = gbm_perf.confusion_matrix(thresholds=[0.01, 0.75, .88])
print cms[0]
print cms[1]
print cms[2]
# Convert a ConfusionMatrix to a python list of lists: [ [tns,fps], [fns,tps] ]
cm = gbm.confusion_matrix()
print cm.to_list()

cm = gbm_perf.confusion_matrix()
print cm.to_list()
