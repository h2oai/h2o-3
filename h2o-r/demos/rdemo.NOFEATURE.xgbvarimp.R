if (! require(xgboost)) {
  stop("xgboost package not available, please run `install.packages('xgboost')`")
}

library(h2o)
library(xgboost)

# Get path to data file
locate_source <- function(file) {
    file_path <- try(h2o:::.h2o.locate(file), silent = TRUE)
    if (inherits(file_path, "try-error")) {
        file_path <- paste0("https://s3.amazonaws.com/h2o-public-test-data/", file)
    }
    file_path
}

milsongs_path <- locate_source("bigdata/laptop/milsongs/milsongs-test.csv.gz")

h2o.init()

milsongs <- h2o.importFile(milsongs_path)

# Train an XGBoost model in H2O
h2o_xgb_model <- h2o.xgboost(training_frame = milsongs, y = "C2", max_depth = 5, reg_lambda = 1, learn_rate = 0.1, seed = 42, ntree = 5)

# Inspect the parameters passed to the native code
print(h2o_xgb_model@model$native_parameters)

# Prepare DMatrix for Native XGBoost
milsongs_df <- as.data.frame(milsongs)
milsongs_matrix <- as.matrix(milsongs_df[,c(1,3:91)])
milsongs_dmatrix <- xgb.DMatrix(data = milsongs_matrix, label = milsongs_df$C2)

# Set same parameters for Native XGBoost
param <- list(
  max_depth = 5,
  lambda = 1,
  eta = 0.1,
  silent = 1,
  objective = "reg:linear",
  seed = 42,
  min_child_weight = 1,
  max_delta_step = 0
)

# Train a native XGBoost model
r_xgb_model <- xgb.train(param, milsongs_dmatrix, nrounds = 5)

# Compare variable importances returned by H2O and Native XGBoost
r_xgb_imp <- xgb.importance(colnames(milsongs_dmatrix), r_xgb_model)
print(r_xgb_imp)
h2o_xgb_imp <- h2o.varimp(h2o_xgb_model)
print(h2o_xgb_imp)

# We expect the order of variables to be the same in Native XGBoost and H2O XGBoost (ordered by Gain)
if (length(which(r_xgb_imp$Feature != h2o_xgb_imp$variable) != 0)) {
  stop("Order of variables is not the same!")
}

r_xgb_gain_rel <- r_xgb_imp$Gain
h2o_xgb_gain_rel <- h2o_xgb_imp$relative_importance / sum(h2o_xgb_imp$relative_importance) # normalize Gain returned by H2O

max_diff <- max(abs(r_xgb_gain_rel - h2o_xgb_gain_rel))

# The imortances should be the same up to tolerance
if (max_diff > 1e-5) {
  stop("Variable importance is different")
}

cat("Variable importances match!\n")
