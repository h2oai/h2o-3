import h2o
#uploading data file to h2o
air = h2o.import_frame(path=h2o.locate("smalldata/airlines/AirlinesTrain.csv.zip"))
# Constructing validation and train sets by sampling (20/80)
# creating a column as tall as air.nrow()
r = air[0].runif()
air_train = air[r < 0.8]
air_valid = air[r >= 0.8]

myX = ["Origin", "Dest", "Distance", "UniqueCarrier", "fMonth", "fDayofMonth", "fDayOfWeek"]
myY = "IsDepDelayed"
#gbm
gbm = h2o.gbm(x=air_train[myX], 
              y=air_train[myY], 
              validation_x=air_valid[myX],
              validation_y=air_valid[myY],
              distribution="bernoulli", 
              ntrees=100, 
              max_depth=3, 
              learn_rate=0.01)
gbm.show()
#glm
glm = h2o.glm(x=air_train[myX], 
              y=air_train[myY],
              validation_x=air_valid[myX],
              validation_y=air_valid[myY],
              family = "binomial", 
              solver="L_BFGS")
glm.pprint_coef()
    
#uploading test file to h2o
air_test = h2o.import_frame(path=h2o.locate("smalldata/airlines/AirlinesTest.csv.zip"))
# predicting & performance on test file
gbm_pred = gbm.predict(air_test)
print "GBM predictions: "
gbm_pred.head()

gbm_perf = gbm.model_performance(air_test)
print "GBM performance: "
gbm_perf.show()

glm_pred = glm.predict(air_test)
print "GLM predictions: "
glm_pred.head()

glm_perf = glm.model_performance(air_test)
print "GLM performance: "
glm_perf.show()
# Building confusion matrix for test set
gbm_CM = gbm_perf.confusion_matrix()
print(gbm_CM)
print

glm_CM = glm_perf.confusion_matrix()
print(glm_CM)
# ROC for test set
print('GBM Precision: {0}'.format(gbm_perf.precision()))
print('GBM Accuracy: {0}'.format(gbm_perf.accuracy()))
print('GBM AUC: {0}'.format(gbm_perf.auc()))
print
print('GLM Precision: {0}'.format(glm_perf.precision()))
print('GLM Accuracy: {0}'.format(glm_perf.accuracy()))
print('GLM AUC: {0}'.format(glm_perf.auc()))
