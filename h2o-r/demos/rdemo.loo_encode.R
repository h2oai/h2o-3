library(h2o)
h2o.init()

airlinesPath <- h2o:::.h2o.locate("smalldata/airlines/AirlinesTrain.csv.zip")
print("Importing airlines data into H2O")
airlines.hex <- h2o.importFile(path = airlinesPath, destination_frame = "airlines.hex")

print("Print out summary of airlines data")
print(summary(airlines.hex))

print("Split data into training and testing")
splits.hex <- h2o.splitFrame(airlines.hex, seed = 1234, destination_frames = c("train.hex", "test.hex"))
train.hex <- splits.hex[[1]]
test.hex <- splits.hex[[2]]


print("Run GBM without Leave One Out Encoding as Baseline")
myX <- setdiff(colnames(train.hex), c("IsDepDelayed", "IsDepDelayed_REC", "fDayofMonth"))
default.gbm <- h2o.gbm(x = myX, y = "IsDepDelayed", 
                       training_frame = train.hex, validation_frame = test.hex, 
                       ntrees = 100, score_tree_interval = 10, model_id = "default_gbm.hex",
                       # Early Stopping
                       stopping_rounds = 5, stopping_metric = "AUC", stopping_tolerance = 0.001)

print(paste0("Default GBM AUC: ", round(h2o.auc(default.gbm, valid = TRUE), 3)))

print("Peform LOO Encoding on Origin and Destination")

for(i in c("Origin", "Dest")){
  
  print("Create Leave One Out Encoding Map")
  encoding_map <- h2o.loo_encode_create(train.hex[[i]], train.hex$IsDepDelayed)
  
  print("Apply Leave One Out Encoding Map on Training and Testing Data")
  encoding_train <- h2o.loo_encode_apply(train.hex[[i]], train.hex$IsDepDelayed, encoding_map,
                                         train = TRUE, seed = 1234)
  encoding_test <- h2o.loo_encode_apply(test.hex[[i]], test.hex$IsDepDelayed, encoding_map,
                                        train = FALSE)
  
  print("Add Target Encoding to Training and Testing Frame")
  train.hex <- h2o.cbind(train.hex, encoding_train)
  colnames(train.hex)[ncol(train.hex)] <- paste0(i, "_TE")
  
  test.hex <- h2o.cbind(test.hex, encoding_test)
  colnames(test.hex)[ncol(test.hex)] <- paste0(i, "_TE")
}


print("Run GBM with Leave One Out Encoding")
myX <- setdiff(colnames(train.hex), c("IsDepDelayed", "IsDepDelayed_REC", "fDayofMonth", "Origin", "Dest"))
loo.gbm <- h2o.gbm(x = myX, y = "IsDepDelayed", 
                   training_frame = train.hex, validation_frame = test.hex, 
                   ntrees = 1000, score_tree_interval = 10, model_id = "loo_gbm.hex",
                   # Early Stopping
                   stopping_rounds = 5, stopping_metric = "AUC", stopping_tolerance = 0.001)

print(paste0("Default GBM AUC: ", round(h2o.auc(default.gbm, valid = TRUE), 3)))
print(paste0("LOO GBM AUC: ", round(h2o.auc(loo.gbm, valid = TRUE), 3)))

h2o.varimp_plot(loo.gbm)
