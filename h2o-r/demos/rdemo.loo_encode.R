library(h2o)
h2o.init()

airlinesPath <- h2o:::.h2o.locate("smalldata/airlines/AirlinesTrain.csv.zip")
print("Importing airlines data into H2O")
airlines <- h2o.importFile(path = airlinesPath, destination_frame = "airlines.hex")

print("Print out summary of airlines data")
print(summary(airlines))

print("Split data into training, validation, testing and target encoding holdout")
splits <- h2o.splitFrame(airlines, seed = 1234, ratios = c(0.7, 0.1, 0.1), 
                         destination_frames = c("train.hex", "valid.hex", "te_holdout.hex", "test.hex"))
train <- splits[[1]]
valid <- splits[[2]]
te_holdout <- splits[[3]]
test <- splits[[4]]


print("Run GBM without Leave One Out Encoding as Baseline")
myX <- setdiff(colnames(train), c("IsDepDelayed", "IsDepDelayed_REC", "fDayofMonth"))

# Since we are not creating any target encoding, we will use both the `train`` and `te_holdout`` frames to train our model
full_train <- h2o.rbind(train, te_holdout)
default_gbm <- h2o.gbm(x = myX, y = "IsDepDelayed", 
                       training_frame = full_train, validation_frame = valid, 
                       ntrees = 100, score_tree_interval = 10, model_id = "default_gbm.hex",
                       # Early Stopping
                       stopping_rounds = 5, stopping_metric = "AUC", stopping_tolerance = 0.001)

print(paste0("Default GBM AUC: ", round(h2o.auc(h2o.performance(default_gbm, test)), 3)))

print("Peform LOO Encoding on Origin and Destination")

# For this model we will calculate LOO Encoding mapping on the full train
# There is possible data leakage since we are creating the encoding map on the training and applying it to the training 
# To mitigate the effect of data leakage without creating a holdout data, we add noise and blended_avg (on when `using_holdout = FALSE`)

for(i in c("Origin", "Dest")){
  
  print("Create Leave One Out Encoding Map")
  encoding_map <- h2o.loo_encode_create(full_train, i, "IsDepDelayed")
  
  print("Apply Leave One Out Encoding Map on Training, Validation, Testing Data")
  full_train <- h2o.loo_encode_apply(full_train, encoding_map, using_holdout = FALSE, seed = 1234, y = "IsDepDelayed")
  valid <- h2o.loo_encode_apply(valid, encoding_map, using_holdout = TRUE)
  test <- h2o.loo_encode_apply(test, encoding_map, using_holdout = TRUE)
  
  colnames(full_train)[colnames(full_train) == "C1"] <- paste0(i, "_TE")
  colnames(valid)[colnames(valid) == "C1"] <- paste0(i, "_TE")
  colnames(test)[colnames(test) == "C1"] <- paste0(i, "_TE")
}


print("Run GBM with Leave One Out Encoding")
myX <- setdiff(colnames(full_train), c("IsDepDelayed", "IsDepDelayed_REC", "fDayofMonth", "Origin", "Dest"))
loo_gbm <- h2o.gbm(x = myX, y = "IsDepDelayed", 
                   training_frame = full_train, validation_frame = valid, 
                   ntrees = 1000, score_tree_interval = 10, model_id = "loo_gbm.hex",
                   # Early Stopping
                   stopping_rounds = 5, stopping_metric = "AUC", stopping_tolerance = 0.001)

print(paste0("Default GBM AUC: ", round(h2o.auc(h2o.performance(default_gbm, test)), 3)))
print(paste0("LOO GBM AUC: ", round(h2o.auc(h2o.performance(loo_gbm, test)), 3)))

h2o.varimp_plot(loo_gbm)

print("Peform LOO Encoding on Origin and Destination on Separate Holdout Data")

# For this model we will calculate LOO Encoding mapping on the te_holdout data
# Since we are creating the encoding map on the te_holdout data and applying it to the training data, 
# we do not need to take data leakage precautions (set `using_holdout = TRUE`)

for(i in c("Origin", "Dest")){
  
  print("Create Leave One Out Encoding Map")
  encoding_map <- h2o.loo_encode_create(te_holdout, i, "IsDepDelayed")
  
  print("Apply Leave One Out Encoding Map on Training, Validation, and Testing Data")
  train <- h2o.loo_encode_apply(train, encoding_map, using_holdout = TRUE)
  valid <- h2o.loo_encode_apply(valid, encoding_map, using_holdout = TRUE)
  test <- h2o.loo_encode_apply(test, encoding_map, using_holdout = TRUE)
  
  colnames(train)[colnames(train) == "C1"] <- paste0(i, "_TE")
  colnames(valid)[colnames(valid) == "C1"] <- paste0(i, "_TE")
  colnames(test)[colnames(test) == "C1"] <- paste0(i, "_TE")
}


print("Run GBM with Leave One Out Encoding")
myX <- setdiff(colnames(train), c("IsDepDelayed", "IsDepDelayed_REC", "fDayofMonth", "Origin", "Dest"))
loo_holdout_gbm <- h2o.gbm(x = myX, y = "IsDepDelayed", 
                           training_frame = train, validation_frame = valid, 
                           ntrees = 1000, score_tree_interval = 10, model_id = "loo_holdout_gbm.hex",
                           # Early Stopping
                           stopping_rounds = 5, stopping_metric = "AUC", stopping_tolerance = 0.001)

print(paste0("Default GBM AUC: ", round(h2o.auc(h2o.performance(default_gbm, test)), 3)))
print(paste0("LOO GBM AUC: ", round(h2o.auc(h2o.performance(loo_gbm,test)), 3)))
print(paste0("LOO Holdout GBM AUC: ", round(h2o.auc(h2o.performance(loo_holdout_gbm, test)), 3)))

h2o.varimp_plot(loo_holdout_gbm)

# In this case, performing Leave One Out Encoding on a holdout dataset improves the accuracy by preventing any leakage
