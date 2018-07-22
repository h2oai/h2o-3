library(h2o)
h2o.init()

dataPath <- h2o:::.h2o.locate("smalldata/airlines/AirlinesTrain.csv.zip")
dataPathTest <- h2o:::.h2o.locate("smalldata/airlines/AirlinesTest.csv.zip")
print("Importing airlines data into H2O")
data <- h2o.importFile(path = dataPath, destination_frame = "data")
dataTest <- h2o.importFile(path = dataPathTest, destination_frame = "dataTest")

print("Split data into training, validation, testing and target encoding holdout")
splits <- h2o.splitFrame(data, seed = 1234, ratios = c(0.8, 0.1),
                         destination_frames = c("train.hex", "valid.hex", "te_holdout.hex"))
train <- splits[[1]]
valid <- splits[[2]]
te_holdout <- splits[[3]]
test <- dataTest

print("Run GBM without Target Encoding as Baseline")
myX <- setdiff(colnames(train), c("IsDepDelayed", "IsDepDelayed_REC"))

# Since we are not creating any target encoding, we will use both the `train`` and `te_holdout`` frames to train our model
full_train <- h2o.rbind(train, te_holdout)
default_gbm <- h2o.gbm(x = myX, y = "IsDepDelayed",
                       training_frame = full_train, validation_frame = valid,
                       ntrees = 1000, score_tree_interval = 10, model_id = "default_gbm",
                       # Early Stopping
                       stopping_rounds = 5, stopping_metric = "AUC", stopping_tolerance = 0.001,
                       seed = 1)

print("Perform Leave One Out Target Encoding on cabin, embarked, and home.dest")

te_cols <- list("Origin", "Dest")


# For this model we will calculate LOO Target Encoding on the full train
# There is possible data leakage since we are creating the encoding map on the training and applying it to the training
# To mitigate the effect of data leakage without creating a holdout data, we remove the existing value of the row (holdout_type = LeaveOneOut)

loo_train <- full_train
loo_valid <- valid
loo_test <- test

# Create Leave One Out Encoding Map
encoding_map <- h2o.target_encode_create(full_train, te_cols, "IsDepDelayed")

# Apply Leave One Out Encoding Map on Training, Validation, Testing Data
loo_train <- h2o.target_encode_apply(loo_train, x = te_cols,  y = "IsDepDelayed", encoding_map, holdout_type = "LeaveOneOut", seed = 1234)
loo_valid <- h2o.target_encode_apply(loo_valid, x = te_cols, y = "IsDepDelayed", encoding_map, holdout_type = "None", noise_level = 0)
loo_test <- h2o.target_encode_apply(loo_test, x = te_cols, y = "IsDepDelayed", encoding_map, holdout_type = "None", noise_level = 0)

print("Run GBM with Leave One Out Target Encoding")
myX <- setdiff(colnames(loo_test), c(te_cols, "IsDepDelayed", "IsDepDelayed_REC"))
loo_gbm <- h2o.gbm(x = myX, y = "IsDepDelayed",
                   training_frame = loo_train, validation_frame = loo_valid,
                   ntrees = 1000, score_tree_interval = 10, model_id = "loo_gbm",
                   # Early Stopping
                   stopping_rounds = 5, stopping_metric = "AUC", stopping_tolerance = 0.001,
                   seed = 1)

h2o.varimp_plot(loo_gbm)

print("Perform Target Encoding on cabin, embarked, and home.dest with Cross Validation Holdout")

# For this model we will calculate Target Encoding mapping on the full training data with cross validation holdout
# There is possible data leakage since we are creating the encoding map on the training and applying it to the training 
# To mitigate the effect of data leakage without creating a holdout data, we remove the existing value of the row (holdout_type = LeaveOneOut)

cv_train <- full_train
cv_valid <- valid
cv_test <- test
cv_train$fold <- h2o.kfold_column(cv_train, nfolds = 5, seed = 1234)

# Create Encoding Map
encoding_map <- h2o.target_encode_create(cv_train, te_cols, "IsDepDelayed", "fold")

# Apply Encoding Map on Training, Validation, Testing Data
cv_train <- h2o.target_encode_apply(cv_train, x = te_cols,  y = "IsDepDelayed", encoding_map, holdout_type = "KFold", fold_column = "fold", seed = 1234)
cv_valid <- h2o.target_encode_apply(cv_valid, x = te_cols, y = "IsDepDelayed", encoding_map, holdout_type = "None", fold_column = "fold", noise_level = 0)
cv_test <- h2o.target_encode_apply(cv_test, x = te_cols, y = "IsDepDelayed", encoding_map, holdout_type = "None", fold_column = "fold", noise_level = 0)

print("Run GBM with Cross Validation Target Encoding")
cvte_gbm <- h2o.gbm(x = myX, y = "IsDepDelayed",
                    training_frame = cv_train, validation_frame = cv_valid,
                    ntrees = 1000, score_tree_interval = 10, model_id = "cv_te_gbm",
                    # Early Stopping
                    stopping_rounds = 5, stopping_metric = "AUC", stopping_tolerance = 0.001,
                    seed = 1)

h2o.varimp_plot(cvte_gbm)

print("Perform Target Encoding on cabin, embarked, and home.dest on Separate Holdout Data")

# For this model we will calculate the Target Encoding mapping on the te_holdout data
# Since we are creating the encoding map on the te_holdout data and applying it to the training data, 
# we do not need to take data leakage precautions (set `holdout_type = None`)

holdout_train <- train
holdout_valid <- valid
holdout_test <- test

# Create Leave One Out Encoding Map
encoding_map <- h2o.target_encode_create(te_holdout, te_cols, "IsDepDelayed")

# Apply Encoding Map on Training, Validation, and Testing Data
holdout_train <- h2o.target_encode_apply(holdout_train, x = te_cols, y = "IsDepDelayed", encoding_map, holdout_type = "None", noise_level = 0)
holdout_valid <- h2o.target_encode_apply(holdout_valid, x = te_cols, y = "IsDepDelayed", encoding_map, holdout_type = "None", noise_level = 0)
holdout_test <- h2o.target_encode_apply(holdout_test, x = te_cols, y = "IsDepDelayed", encoding_map, holdout_type = "None", noise_level = 0)

print("Run GBM with Target Encoding on Holdout")
holdout_gbm <- h2o.gbm(x = myX, y = "IsDepDelayed",
                       training_frame = holdout_train, validation_frame = holdout_valid,
                       ntrees = 1000, score_tree_interval = 10, model_id = "holdout_gbm",
                       # Early Stopping
                       stopping_rounds = 5, stopping_metric = "AUC", stopping_tolerance = 0.001,
                       seed = 1)

h2o.varimp_plot(holdout_gbm)

print("Compare AUC of GBM with different types of target encoding")
print(paste0("Default GBM AUC: ", round(h2o.auc(h2o.performance(default_gbm, test)), 3)))
print(paste0("LOO GBM AUC: ", round(h2o.auc(h2o.performance(loo_gbm, loo_test)), 3)))
print(paste0("CV TE GBM AUC: ", round(h2o.auc(h2o.performance(cvte_gbm, cv_test)), 3)))
print(paste0("Holdout GBM AUC: ", round(h2o.auc(h2o.performance(holdout_gbm, holdout_test)), 3)))

