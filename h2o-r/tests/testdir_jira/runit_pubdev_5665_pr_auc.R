setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

######################################################################################
#    This pyunit test is written to ensure that the newly exposed field pr_auc accessible
# to any binomial classifiers.  It is okay not to have assert or expect statement here too.
# We are just trying to make sure the pr_auc field is accessible for all binomial classifiers.
######################################################################################
pubdev_5665_test <-
function() {
  seed <- 123456789
  print("*************  starting max_runtime_test for GBM")
  training1_data <- h2o.importFile(locate("smalldata/logreg/prostate_train.csv"))
  training1_data["CAPSULE"] <- as.factor(training1_data["CAPSULE"])
  y_index <- "CAPSULE"
  x_indices <- c("AGE", "RACE", "DPROS", "DCAPS", "PSA", "VOL", "GLEASON")

  gbm_model <- h2o.gbm(x=x_indices, y=y_index, training_frame=training1_data, distribution="bernoulli", seed=seed)
  gbm_pr_auc = gbm_model@model$training_metrics@metrics$pr_auc
  print(gbm_model)
  expect_equal(gbm_pr_auc, h2o.pr_auc(gbm_model))
  glm_model <- h2o.glm(y=y_index, x=x_indices, training_frame=training1_data, family='binomial', seed=seed)
  glm_pr_auc = glm_model@model$training_metrics@metrics$pr_auc
  print(glm_model)
  expect_equal(glm_pr_auc, h2o.pr_auc(glm_model))
  rf_model <- h2o.randomForest(y=y_index, x=x_indices, training_frame=training1_data, ntrees=10, score_tree_interval=0)
  rf_pr_auc = rf_model@model$training_metrics@metrics$pr_auc
  print(rf_model)
  expect_equal(rf_pr_auc, h2o.pr_auc(rf_model))
  dl_model <- h2o.deeplearning(x=x_indices,y=y_index,training_frame=training1_data, distribution='bernoulli', seed=seed, hidden=c(2,2))
  dl_pr_auc = dl_model@model$training_metrics@metrics$pr_auc
  print(dl_model)
  expect_equal(dl_pr_auc, h2o.pr_auc(dl_model))
  
  print("pr_auc for gbm, glm, randomforest and deeplearning are")
  print(c(gbm_pr_auc, glm_pr_auc, rf_pr_auc, dl_pr_auc))
}

doTest("Perform the test for pubdev 5665", pubdev_5665_test)
