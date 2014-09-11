##
# Test: Saving and Loading GLM Model (HEX-1775)
# Description: Build GLM model, save model in R, copy model and load in R
##

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
# setwd("/Users/tomk/0xdata/ws/h2o/R/tests/testdir_jira")

source('../findNSourceUtils.R')

test.hex_1775 <- function(conn) {
  temp_dir = tempdir()
  temp_subdir1 = paste(temp_dir, "tmp", sep = .Platform$file.sep)
  temp_subdir2 = paste(temp_dir, "tmp2", sep = .Platform$file.sep)
  dir.create(temp_subdir1)
  
  # Test saving and loading of GLM model
  Log.info("Importing prostate.csv...")
  prostate.hex = h2o.importFile(conn, normalizePath(locate('smalldata/logreg/prostate.csv')))
  
  # Build GLM, RandomForest, GBM, Naive Bayes, and Deep Learning models
  Log.info("Build GLM model")
  prostate.glm = h2o.glm(y = "CAPSULE", x = c("AGE","RACE","PSA","DCAPS"), data = prostate.hex, family = "binomial", nfolds = 0, alpha = 0.5)
  Log.info("Build GBM model")
  prostate.gbm = h2o.gbm(y = 2, x = 3:9, data = prostate.hex, nfolds = 5)

  # Predict on models and save results in R
  Log.info("Scoring on models and saving predictions to R")
  glm.pred = h2o.predict(object = prostate.glm, newdata = prostate.hex)
  glm.pred.df = as.data.frame(glm.pred)
  gbm.pred = h2o.predict(object = prostate.gbm, newdata = prostate.hex)
  gbm.pred.df = as.data.frame(gbm.pred)
  
  # Save models to disk
  Log.info("Saving models to disk")
  prostate.glm.path = h2o.saveModel(object = prostate.glm, dir = temp_subdir1, save_cv  = FALSE, force = TRUE)
  prostate.gbm.path = h2o.saveModel(object = prostate.gbm, dir = temp_subdir1, save_cv  = TRUE, force = TRUE)
  # All keys removed to test that cross validation models are actually being loaded
  h2o.removeAll(object = conn)
 
  # Proving we can move files from one directory to another and not affect the load of the model
  Log.info(paste("Moving GLM model from", temp_subdir1, "to", temp_subdir2))
  file.rename(temp_subdir1, temp_subdir2)
  prostate.glm.path = paste(temp_subdir2,basename(prostate.glm.path),sep = .Platform$file.sep)
  prostate.gbm.path = paste(temp_subdir2,basename(prostate.gbm.path),sep = .Platform$file.sep)
  
  Log.info(paste("Model saved in", temp_subdir2))
  prostate.glm2 = h2o.loadModel(conn, prostate.glm.path)
  prostate.gbm2 = h2o.loadModel(conn, prostate.gbm.path)
  
  # Check to make sure predictions made on loaded model is the same as glm.pred
  prostate.hex = h2o.importFile(conn, normalizePath(locate('smalldata/logreg/prostate.csv')))
  glm.pred2 = h2o.predict(object = prostate.glm2, newdata = prostate.hex)
  glm.pred2.df = as.data.frame(glm.pred2)
  gbm.pred2 = h2o.predict(object = prostate.gbm2, newdata = prostate.hex)
  gbm.pred2.df = as.data.frame(gbm.pred2)
  expect_equal(nrow(glm.pred.df), 380)
  expect_equal(glm.pred.df, glm.pred2.df)
  expect_equal(nrow(gbm.pred.df), 380)
  expect_equal(gbm.pred.df, gbm.pred2.df)
  
  testEnd()
}

doTest("HEX-1775 Test: Save and Load GLM Model", test.hex_1775)
