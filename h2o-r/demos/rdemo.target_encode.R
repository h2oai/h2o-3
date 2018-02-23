library(h2o)
h2o.init()

dataPath <- h2o:::.h2o.locate("smalldata/gbm_test/titanic.csv")
print("Importing titanic data into H2O")
data <- h2o.importFile(path = dataPath, destination_frame = "data")
data$survived <- as.factor(data$survived)
data$name <- as.factor(data$name)

print("Print out summary of titanic data")
print(summary(data))

print("Split data into training, validation, testing and target encoding holdout")
splits <- h2o.splitFrame(data, seed = 1234, ratios = c(0.7, 0.1, 0.1), 
                         destination_frames = c("train.hex", "valid.hex", "te_holdout.hex", "test.hex"))
train <- splits[[1]]
valid <- splits[[2]]
te_holdout <- splits[[3]]
test <- splits[[4]]


print("Run GBM without Target Encoding as Baseline")
myX <- setdiff(colnames(train), c("survived", "name", "ticket", "boat", "body"))

# Since we are not creating any target encoding, we will use both the `train`` and `te_holdout`` frames to train our model
full_train <- h2o.rbind(train, te_holdout)
default_gbm <- h2o.gbm(x = myX, y = "survived", 
                       training_frame = full_train, validation_frame = valid, 
                       ntrees = 1000, score_tree_interval = 10, model_id = "default_gbm",
                       # Early Stopping
                       stopping_rounds = 5, stopping_metric = "AUC", stopping_tolerance = 0.001,
                       seed = 1)

print(paste0("Default GBM AUC: ", round(h2o.auc(h2o.performance(default_gbm, test)), 3)))

print("Perform Leave One Out Target Encoding on cabin, embarked, and home.dest")

# For this model we will calculate LOO Target Encoding on the full train
# There is possible data leakage since we are creating the encoding map on the training and applying it to the training 
# To mitigate the effect of data leakage without creating a holdout data, we remove the existing value of the row (holdout_type = LeaveOneOut)

for (i in c("cabin", "embarked", "home.dest")) {
  
  print("Create Leave One Out Encoding Map")
  encoding_map <- h2o.target_encode_create(full_train, i, "survived")
  
  print("Apply Leave One Out Encoding Map on Training, Validation, Testing Data")
  full_train <- h2o.target_encode_apply(full_train, x = i,  y = "survived", encoding_map, holdout_type = "LeaveOneOut", seed = 1234)
  valid <- h2o.target_encode_apply(valid, x = i, y = "survived", encoding_map, holdout_type = "None", noise_level = 0)
  test <- h2o.target_encode_apply(test, x = i, y = "survived", encoding_map, holdout_type = "None", noise_level = 0)
  
  colnames(full_train)[colnames(full_train) == "C1"] <- paste0("LOO_TE_", i)
  colnames(valid)[colnames(valid) == "C1"] <- paste0("LOO_TE_", i)
  colnames(test)[colnames(test) == "C1"] <- paste0("LOO_TE_", i)
}


print("Run GBM with Leave One Out Target Encoding")
loo_x <- c(setdiff(myX, c("cabin", "embarked", "home.dest")), colnames(test)[grep("LOO_TE_", colnames(test))])
loo_gbm <- h2o.gbm(x = loo_x, y = "survived", 
                   training_frame = full_train, validation_frame = valid, 
                   ntrees = 1000, score_tree_interval = 10, model_id = "loo_gbm",
                   # Early Stopping
                   stopping_rounds = 5, stopping_metric = "AUC", stopping_tolerance = 0.001,
                   seed = 1)

print(paste0("Default GBM AUC: ", round(h2o.auc(h2o.performance(default_gbm, test)), 3)))
print(paste0("LOO GBM AUC: ", round(h2o.auc(h2o.performance(loo_gbm, test)), 3)))

h2o.varimp_plot(loo_gbm)

print("Perform Target Encoding on cabin, embarked, and home.dest with Cross Validation Holdout")

# For this model we will calculate Target Encoding mapping on the full training data with cross validation holdout
# There is possible data leakage since we are creating the encoding map on the training and applying it to the training 
# To mitigate the effect of data leakage without creating a holdout data, we remove the existing value of the row (holdout_type = LeaveOneOut)

full_train$fold <- h2o.kfold_column(full_train, nfolds = 5, seed = 1234)
for (i in c("cabin", "embarked", "home.dest")) {
  
  print("Create Leave One Out Encoding Map")
  encoding_map <- h2o.target_encode_create(full_train, i, "survived", "fold")
  
  print("Apply Leave One Out Encoding Map on Training, Validation, Testing Data")
  full_train <- h2o.target_encode_apply(full_train, x = i,  y = "survived", encoding_map, holdout_type = "KFold", fold_column = "fold", seed = 1234)
  valid <- h2o.target_encode_apply(valid, x = i, y = "survived", encoding_map, holdout_type = "None", fold_column = "fold", noise_level = 0)
  test <- h2o.target_encode_apply(test, x = i, y = "survived", encoding_map, holdout_type = "None", fold_column = "fold", noise_level = 0)
  
  colnames(full_train)[colnames(full_train) == "C1"] <- paste0("CV_TE_", i)
  colnames(valid)[colnames(valid) == "C1"] <- paste0("CV_TE_", i)
  colnames(test)[colnames(test) == "C1"] <- paste0("CV_TE_", i)
}


print("Run GBM with Cross Validation Target Encoding")
cvte_x <- c(setdiff(myX, c("cabin", "embarked", "home.dest")), colnames(test)[grep("CV_TE_", colnames(test))])
cvte_gbm <- h2o.gbm(x = cvte_x, y = "survived", 
                    training_frame = full_train, validation_frame = valid, 
                    ntrees = 1000, score_tree_interval = 10, model_id = "cv_te_gbm",
                    # Early Stopping
                    stopping_rounds = 5, stopping_metric = "AUC", stopping_tolerance = 0.001,
                    seed = 1)

print(paste0("Default GBM AUC: ", round(h2o.auc(h2o.performance(default_gbm, test)), 3)))
print(paste0("LOO GBM AUC: ", round(h2o.auc(h2o.performance(loo_gbm,test)), 3)))
print(paste0("CV TE GBM AUC: ", round(h2o.auc(h2o.performance(cvte_gbm, test)), 3)))

h2o.varimp_plot(cvte_gbm)


print("Perform Target Encoding on cabin, embarked, and home.dest on Separate Holdout Data")

# For this model we will calculate the Target Encoding mapping on the te_holdout data
# Since we are creating the encoding map on the te_holdout data and applying it to the training data, 
# we do not need to take data leakage precautions (set `holdout_type = None`)

for (i in c("cabin", "embarked", "home.dest")) {
  
  print("Create Leave One Out Encoding Map")
  encoding_map <- h2o.target_encode_create(te_holdout, i, "survived")
  
  print("Apply Leave One Out Encoding Map on Training, Validation, and Testing Data")
  train <- h2o.target_encode_apply(train, x = i, y = "survived", encoding_map, holdout_type = "None", noise_level = 0)
  valid <- h2o.target_encode_apply(valid, x = i, y = "survived", encoding_map, holdout_type = "None", noise_level = 0)
  test <- h2o.target_encode_apply(test, x = i, y = "survived", encoding_map, holdout_type = "None", noise_level = 0)
  
  colnames(train)[colnames(train) == "C1"] <- paste0("Holdout_TE_", i)
  colnames(valid)[colnames(valid) == "C1"] <- paste0("Holdout_TE_", i)
  colnames(test)[colnames(test) == "C1"] <- paste0("Holdout_TE_", i)
}


print("Run GBM with Target Encoding on Holdout")
holdout_x <- c(setdiff(myX, c("cabin", "embarked", "home.dest")), colnames(test)[grep("Holdout_TE_", colnames(test))])
holdout_gbm <- h2o.gbm(x = holdout_x, y = "survived", 
                       training_frame = train, validation_frame = valid, 
                       ntrees = 1000, score_tree_interval = 10, model_id = "holdout_gbm",
                       # Early Stopping
                       stopping_rounds = 5, stopping_metric = "AUC", stopping_tolerance = 0.001,
                       seed = 1)

print(paste0("Default GBM AUC: ", round(h2o.auc(h2o.performance(default_gbm, test)), 3)))
print(paste0("LOO GBM AUC: ", round(h2o.auc(h2o.performance(loo_gbm,test)), 3)))
print(paste0("CV TE GBM AUC: ", round(h2o.auc(h2o.performance(cvte_gbm, test)), 3)))
print(paste0("Holdout GBM AUC: ", round(h2o.auc(h2o.performance(holdout_gbm, test)), 3)))

h2o.varimp_plot(holdout_gbm)