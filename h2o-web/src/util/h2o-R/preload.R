library(h2o)

localH2O = h2o.init(ip = "localhost", port = 54321, startH2O = FALSE)

######################
# AirlinesTrain models
######################
airlines_train.hex =  h2o.importFile.FV(localH2O, path = "./smalldata/airlines/AirlinesTrain.csv.zip", key="airlines_train.hex")
airlines_test.hex =  h2o.importFile.FV(localH2O, path = "./smalldata/airlines/AirlinesTest.csv.zip", key="airlines_test.hex")

print("Generating AirlinesTrain GLM2 model. . .")
h2o.glm.FV(y = "IsDepDelayed", x = c("Origin", "Dest", "fDayofMonth", "fYear", "UniqueCarrier", "fDayOfWeek", "fMonth", "DepTime", "ArrTime", "Distance"), data = airlines_train.hex, family = "binomial", alpha=0.05, lambda=1.0e-2, standardize=FALSE, nfolds=0)

print("Generating AirlinesTrain simple GBM model. . .")
h2o.gbm(y = "IsDepDelayed", x = c("Origin", "Dest", "fDayofMonth", "fYear", "UniqueCarrier", "fDayOfWeek", "fMonth", "DepTime", "ArrTime", "Distance"), data = airlines_train.hex, n.trees=3, interaction.depth=1, distribution="multinomial", n.minobsinnode=2, shrinkage=.1)

print("Generating AirlinesTrain complex GBM model. . .")
h2o.gbm(y = "IsDepDelayed", x = c("Origin", "Dest", "fDayofMonth", "fYear", "UniqueCarrier", "fDayOfWeek", "fMonth", "DepTime", "ArrTime", "Distance"), data = airlines_train.hex, n.trees=50, interaction.depth=5, distribution="multinomial", n.minobsinnode=2, shrinkage=.1)

print("Generating AirlinesTrain simple DRF model. . .")
h2o.randomForest.FV(y = "IsDepDelayed", x = c("Origin", "Dest", "fDayofMonth", "fYear", "UniqueCarrier", "fDayOfWeek", "fMonth", "DepTime", "ArrTime", "Distance"), data = airlines_train.hex, ntree=5, depth=2)

print("Generating AirlinesTrain complex DRF model. . .")
h2o.randomForest.FV(y = "IsDepDelayed", x = c("Origin", "Dest", "fDayofMonth", "fYear", "UniqueCarrier", "fDayOfWeek", "fMonth", "DepTime", "ArrTime", "Distance"), data = airlines_train.hex, ntree=50, depth=10)

print("Generating AirlinesTrain DeepLearning model. . .")
# h2o.deeplearning(y = "IsDepDelayed", x = c("Origin", "Dest", "fDayofMonth", "fYear", "UniqueCarrier", "fDayOfWeek", "fMonth", "DepTime", "ArrTime", "Distance"), data = airlines_train.hex, classification=TRUE, layers=c(10, 10))
print("SKIPPED BECAUSE IT IS CRASHING")

print("Generating AirlinesTrain GLM2 model with different response column. . .")
#h2o.glm.FV(y = "IsDepDelayed_REC", x = c("Origin", "Dest", "fDayofMonth", "fYear", "UniqueCarrier", "fDayOfWeek", "fMonth", "DepTime", "ArrTime", "Distance"), data = airlines_train.hex, family = "binomial", alpha=0.05, lambda=1.0e-2, standardize=FALSE, nfolds=0)
print("SKIPPED BECAUSE IT IS CRASHING")


#################
# Prostate models
#################
prostate.hex = h2o.importFile.FV(localH2O, path = "./smalldata/logreg/prostate.csv", key = "prostate.hex")

print("Generating Prostate GLM2 model. . .")
h2o.glm.FV(y = "CAPSULE", x = c("AGE","RACE","PSA","DCAPS"), data = prostate.hex, family = "binomial", nfolds = 0, alpha = 0.5)

print("Generating Prostate simple DRF model. . .")
h2o.randomForest.FV(y = "CAPSULE", x = c("AGE","RACE","DCAPS"), data = prostate.hex, ntree=10, depth=5)

print("Generating Prostate GLM2 regression model. . .")
h2o.glm.FV(y = "AGE", x = c("CAPSULE","RACE","PSA","DCAPS"), data = prostate.hex, family = "gaussian", nfolds = 0, alpha = 0.5)

