library(h2o)
h2o.init()

dataPath <- h2o:::.h2o.locate("smalldata/gbm_test/titanic.csv")
print("Importing titanic data into H2O")
data <- h2o.importFile(path = dataPath, destination_frame = "data")
data$survived <- as.factor(data$survived)

print("Split data into training, validation, testing and target encoding holdout")
splits <- h2o.splitFrame(data, seed = 1234, ratios = c(0.7, 0.1, 0.1),
destination_frames = c("train.hex", "valid.hex", "te_holdout.hex", "test.hex"))
train <- splits[[1]]
valid <- splits[[2]]
te_holdout <- splits[[3]]
test <- splits[[4]]

print("Run GBM without Target Encoding as Baseline")

myX <- setdiff(colnames(train), c("survived", "name", "ticket", "boat", "body"))
te_cols <- c("cabin", "embarked", "home.dest")
inflection_point <- 3
smoothing <- 1

# Since we are not creating any target encoding, we will use both the `train`` and `te_holdout`` frames to train our model
full_train <- h2o.rbind(train, te_holdout)
default_gbm <- h2o.gbm(x = myX, y = "survived",
                       training_frame = full_train, validation_frame = valid,
                       ntrees = 1000, score_tree_interval = 10, model_id = "default_gbm",
                       # Early Stopping
                       stopping_rounds = 5, stopping_metric = "AUC", stopping_tolerance = 0.001,
                       seed = 1)



############################################## LeaveOneOut #############################################################
print("Perform Leave One Out Target Encoding on cabin, embarked, and home.dest")

# For this model we will calculate LOO Target Encoding on the full train
# There is possible data leakage since we are creating the encoding map on the training and applying it to the training
# To mitigate the effect of data leakage without creating a holdout data, we remove the existing value of the row (holdout_type = LeaveOneOut)

loo_train <- full_train
loo_valid <- valid
loo_test <- test

# Create Leave One Out Encoding Map
encoding_map <- h2o.target_encode_fit(loo_train, te_cols, "survived")

# Apply Leave One Out Encoding Map on Training, Validation, Testing Data
loo_train <- h2o.target_encode_transform(frame=loo_train, x = te_cols, y = "survived",
                                        target_encode_map=encoding_map, holdout_type = "loo",
                                        blended_avg=TRUE, inflection_point = inflection_point, smoothing=smoothing,
                                        noise=-1, seed = 1234)

h2o.exportFile(loo_train, "titanic_loo_train_new.csv", force = TRUE)

loo_valid <- h2o.target_encode_transform(frame=loo_valid, x = te_cols, y = "survived",
                                        target_encode_map=encoding_map, holdout_type = "none",
                                        blended_avg=TRUE, inflection_point = inflection_point, smoothing=smoothing,
                                        noise=0)

loo_test <- h2o.target_encode_transform(frame=loo_test, x = te_cols, y = "survived",
                                        target_encode_map=encoding_map, holdout_type = "none",
                                        blended_avg=TRUE, inflection_point = inflection_point, smoothing=smoothing,
                                        noise=0)

print("Run GBM with Leave One Out Target Encoding")
myX <- setdiff(colnames(loo_test), c(te_cols, "survived", "name", "ticket", "boat", "body"))
loo_gbm <- h2o.gbm(x = myX, y = "survived",
                   training_frame = loo_train, validation_frame = loo_valid,
                   ntrees = 1000, score_tree_interval = 10, model_id = "loo_gbm",
                   # Early Stopping
                   stopping_rounds = 5, stopping_metric = "AUC", stopping_tolerance = 0.001,
                   seed = 1)

h2o.varimp_plot(loo_gbm)



############################################## KFold ###################################################################
print("Perform Target Encoding on cabin, embarked, and home.dest with Cross Validation Holdout")

# For this model we will calculate Target Encoding mapping on the full training data with cross validation holdout
# There is possible data leakage since we are creating the encoding map on the training and applying it to the training
# To mitigate the effect of data leakage without creating a holdout data, we remove the existing value of the row (holdout_type = LeaveOneOut)

kfold_train <- full_train
kfold_valid <- valid
kfold_test <- test
kfold_train$fold <- h2o.kfold_column(kfold_train, nfolds = 5, seed = 1234)

encoding_map <- h2o.target_encode_fit(kfold_train, te_cols, "survived", "fold")

# Apply Encoding Map on Training, Validation, Testing Data
kfold_train <- h2o.target_encode_transform(frame=kfold_train, x = te_cols, y = "survived",
                                        target_encode_map=encoding_map, holdout_type = "kfold", fold_column = "fold",
                                        blended_avg=TRUE, inflection_point = inflection_point, smoothing=smoothing,
                                        noise=-1, seed = 1234)

kfold_valid <- h2o.target_encode_transform(frame=kfold_valid, x = te_cols, y = "survived",
                                        target_encode_map=encoding_map, holdout_type = "none", fold_column = "fold",
                                        blended_avg=TRUE, inflection_point = inflection_point, smoothing=smoothing,
                                        noise=0)

kfold_test <- h2o.target_encode_transform(frame=kfold_test, x = te_cols, y = "survived",
                                        target_encode_map=encoding_map, holdout_type = "none", fold_column = "fold",
                                        blended_avg=TRUE, inflection_point = inflection_point, smoothing=smoothing,
                                        noise=0)

print("Run GBM with Cross Validation Target Encoding")
kfold_te_gbm <- h2o.gbm(x = myX, y = "survived",
                    training_frame = kfold_train, validation_frame = kfold_valid,
                    ntrees = 1000, score_tree_interval = 10, model_id = "kfold_te_gbm",
                    # Early Stopping
                    stopping_rounds = 5, stopping_metric = "AUC", stopping_tolerance = 0.001,
                    seed = 1)

h2o.varimp_plot(kfold_te_gbm)



############################################## None holdout ############################################################
print("Perform Target Encoding on cabin, embarked, and home.dest on Separate Holdout Data")

# For this model we will calculate the Target Encoding mapping on the te_holdout data
# Since we are creating the encoding map on the te_holdout data and applying it to the training data,
# we do not need to take data leakage precautions (set `holdout_type = None`)

holdout_train <- train
holdout_valid <- valid
holdout_test <- test

encoding_map <- h2o.target_encode_fit(te_holdout, te_cols, "survived")

# Apply Encoding Map on Training, Validation, and Testing Data
holdout_train <- h2o.target_encode_transform(frame=holdout_train, x = te_cols, y = "survived",
                                            target_encode_map=encoding_map, holdout_type = "none",
                                            blended_avg=TRUE, inflection_point = inflection_point, smoothing=smoothing,
                                            noise=0, seed = 1234)

holdout_valid <- h2o.target_encode_transform(frame=holdout_valid, x = te_cols, y = "survived",
                                            target_encode_map=encoding_map, holdout_type = "none",
                                            blended_avg=TRUE, inflection_point = inflection_point, smoothing=smoothing,
                                            noise=0)

holdout_test <- h2o.target_encode_transform(frame=holdout_test, x = te_cols, y = "survived",
                                            target_encode_map=encoding_map, holdout_type = "none",
                                            blended_avg=TRUE, inflection_point = inflection_point, smoothing=smoothing,
                                            noise=0)

print("Run GBM with Target Encoding on Holdout")
holdout_gbm <- h2o.gbm(x = myX, y = "survived",
                       training_frame = holdout_train, validation_frame = holdout_valid,
                       ntrees = 1000, score_tree_interval = 10, model_id = "holdout_gbm",
                       # Early Stopping
                       stopping_rounds = 5, stopping_metric = "AUC", stopping_tolerance = 0.001,
                       seed = 1)

h2o.varimp_plot(holdout_gbm)

print("Compare AUC of GBM with different types of target encoding")
print(paste0("Default GBM AUC: ", round(h2o.auc(h2o.performance(default_gbm, test)), 3)))
print(paste0("LOO GBM AUC: ", round(h2o.auc(h2o.performance(loo_gbm, loo_test)), 3)))
print(paste0("KFold TE GBM AUC: ", round(h2o.auc(h2o.performance(kfold_te_gbm, kfold_test)), 3)))
print(paste0("Holdout GBM AUC: ", round(h2o.auc(h2o.performance(holdout_gbm, holdout_test)), 3)))

