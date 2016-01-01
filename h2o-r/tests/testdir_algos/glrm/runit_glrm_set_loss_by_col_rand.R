setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



NUM_LOSS <- c("Quadratic", "Absolute", "Huber", "Poisson", "Periodic")
CAT_LOSS <- c("Categorical", "Ordinal")
BOOL_LOSS <- c("Hinge", "Logistic")

test.glrm.loss_by_col <- function() {
  h2oTest.logInfo("Importing prostate_cat.csv data...")
  prostate.hex <- h2o.uploadFile(h2oTest.locate("smalldata/prostate/prostate_cat.csv"), destination_frame= "prostate.hex", na.strings = rep("NA", 8))
  print(summary(prostate.hex))
  ncols <- ncol(prostate.hex)
  CAT_COLS <- c(1, 3, 4, 5)
  NUM_COLS <- setdiff(1:ncols, CAT_COLS)
  
  # Fully specify every column's loss function (no need for loss_by_col_idx)
  loss_all <- rep(NA, ncols)
  loss_all[CAT_COLS] <- sample(CAT_LOSS, size = length(CAT_COLS), replace = TRUE)
  loss_all[NUM_COLS] <- sample(NUM_LOSS, size = length(NUM_COLS), replace = TRUE)
  h2oTest.logInfo(paste("Run GLRM with loss_by_col =", paste(loss_all, collapse = ", ")))
  h2o.glrm(training_frame = prostate.hex, k = 5, loss_by_col = loss_all)
  
  # Randomly set columns and loss functions
  cat_size <- sample(1:length(CAT_COLS), 1)
  num_size <- sample(1:length(NUM_COLS), 1)
  cat_idx <- sample(CAT_COLS-1, size = cat_size)  # Because H2O uses zero-indexing
  num_idx <- sample(NUM_COLS-1, size = num_size)
  loss_by_col_cat <- sample(CAT_LOSS, size = cat_size, replace = TRUE)
  loss_by_col_num <- sample(NUM_LOSS, size = num_size, replace = TRUE)
  
  # Permute order for testing purposes
  loss_idx_all <- c(cat_idx, num_idx)
  loss_all <- c(loss_by_col_cat, loss_by_col_num)
  perm_idx <- sample(1:length(loss_all), length(loss_all))
  loss_idx_all <- loss_idx_all[perm_idx]
  loss_all <- loss_all[perm_idx]
  
  h2oTest.logInfo("Error if number of loss functions not equal to number of column indices to set")
  if(length(loss_all) < ncols)
    expect_error(h2o.glrm(training_frame = prostate.hex, k = 5, loss_by_col = loss_all))
  expect_error(h2o.glrm(training_frame = prostate.hex, k = 5, loss_by_col_idx = loss_idx_all))
  expect_error(h2o.glrm(training_frame = prostate.hex, k = 5, loss_by_col = c("Absolute", "Ordinal", "Huber"), loss_by_col_idx = c(1,2)))
  expect_error(h2o.glrm(training_frame = prostate.hex, k = 5, loss_by_col = c("Absolute", "Ordinal"), loss_by_col_idx = c(1,2,5)))
  
  h2oTest.logInfo("Error if column index out of bounds (check zero indexing)")
  expect_error(h2o.glrm(training_frame = prostate.hex, k = 5, loss_by_col = "Absolute", loss_by_col_idx = ncols))
  
  h2oTest.logInfo("Error if incorrect loss function for numeric/categorical column")
  expect_error(h2o.glrm(training_frame = prostate.hex, k = 5, loss_by_col = sample(NUM_LOSS, 1), loss_by_col_idx = sample(CAT_COLS-1, 1)))
  expect_error(h2o.glrm(training_frame = prostate.hex, k = 5, loss_by_col = sample(CAT_LOSS, 1), loss_by_col_idx = sample(NUM_COLS-1, 1)))
  
  h2oTest.logInfo(paste("Run GLRM with loss_by_col =", paste(loss_all, collapse = ", "), "and loss_by_col_idx =", paste(loss_idx_all, collapse = ", ")))
  h2o.glrm(training_frame = prostate.hex, k = 5, loss_by_col = loss_all, loss_by_col_idx = loss_idx_all)
  
}

h2oTest.doTest("GLRM Test: Set loss function by column", test.glrm.loss_by_col)
