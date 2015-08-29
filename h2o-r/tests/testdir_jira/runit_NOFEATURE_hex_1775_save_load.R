##
# Test: Saving and Loading GLM Model (HEX-1775)
# Description: Build GLM model, save model in R, copy model and load in R
##

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
# setwd("/Users/tomk/0xdata/ws/h2o/R/tests/testdir_jira")
#setwd("/Users/Amy/Documents/h2o/R/tests/testdir_jira")

source('../h2o-runit.R')
conn = h2o.init()

test.hex_1775 <- function() {
  temp_dir = tempdir()
  temp_subdir1 = paste(temp_dir, "tmp", sep = .Platform$file.sep)
  temp_subdir2 = paste(temp_dir, "tmp2", sep = .Platform$file.sep)
  dir.create(temp_subdir1)

  # Test saving and loading of GLM model
  Log.info("Importing prostate.csv...")
  prostate.hex = h2o.uploadFile(normalizePath(locate('smalldata/logreg/prostate.csv')))
  iris.hex = h2o.uploadFile(normalizePath(locate('smalldata/iris/iris.csv')))

  # Build GLM, RandomForest, GBM, Naive Bayes, and Deep Learning models
  Log.info("Build GLM model")
  prostate.glm = h2o.glm(y = "CAPSULE", x = c("AGE","RACE","PSA","DCAPS"), training_frame = prostate.hex, family = "binomial", nfolds = 0, alpha = 0.5)
  Log.info("Build GBM model")
  prostate.gbm = h2o.gbm(y = 2, x = 3:9, training_frame = prostate.hex, nfolds = 5, distribution = "multinomial")
  Log.info("Build Speedy Random Forest Model")
  iris.speedrf = h2o.randomForest(x = c(2,3,4), y = 5, data = iris.hex, ntree = 10, depth = 20, type = "fast")
  Log.info("Build BigData Random Forest Model")
  iris.rf = h2o.randomForest(x = c(2,3,4), y = 5, data = iris.hex, ntree = 10, depth = 20, nfolds = 5, type = "BigData")
  Log.info("Build Naive Bayes Model")
  iris.nb = h2o.naiveBayes(y = 5, x = 1:4, data = iris.hex)
  Log.info("Build Deep Learning model")
  iris.dl = h2o.deeplearning(y = 5, x= 1:4, data = iris.hex)

  # Predict on models and save results in R
  Log.info("Scoring on models and saving predictions to R")

  pred_df <- function(object, newdata) {
    h2o_pred = h2o.predict(object, newdata)
    as.data.frame(h2o_pred)
  }

  glm.pred = pred_df(object = prostate.glm , newdata = prostate.hex)
  gbm.pred = pred_df(object = prostate.gbm, newdata = prostate.hex)
  speedrf.pred = pred_df(object = iris.speedrf, newdata = iris.hex)
  rf.pred = pred_df(object = iris.rf, newdata = iris.hex)
  nb.pred = pred_df(object = iris.nb, newdata = iris.hex)
  dl.pred = pred_df(object = iris.dl, newdata = iris.hex)

  # Save models to disk
  Log.info("Saving models to disk")
  prostate.glm.path = h2o.saveModel(object = prostate.glm, dir = temp_subdir1, save_cv  = FALSE, force = TRUE)
  prostate.gbm.path = h2o.saveModel(object = prostate.gbm, dir = temp_subdir1, save_cv  = TRUE, force = TRUE)
  iris.speedrf.path = h2o.saveModel(object = iris.speedrf, dir = temp_subdir1, save_cv  = FALSE, force = TRUE)
  iris.rf.path = h2o.saveModel(object = iris.rf, dir = temp_subdir1, save_cv  = TRUE, force = TRUE)
  iris.nb.path = h2o.saveModel(object = iris.nb, dir = temp_subdir1, save_cv  = FALSE, force = TRUE)
  iris.dl.path = h2o.saveModel(object = iris.dl, dir = temp_subdir1, save_cv  = FALSE, force = TRUE)

  # All keys removed to test that cross validation models are actually being loaded
  h2o.removeAll()

  # Proving we can move files from one directory to another and not affect the load of the model
  Log.info(paste("Moving models from", temp_subdir1, "to", temp_subdir2))
  file.rename(temp_subdir1, temp_subdir2)

  model_paths = c(prostate.glm.path, prostate.gbm.path, iris.speedrf.path, iris.rf.path, iris.nb.path, iris.dl.path)
  new_model_paths = {}
  for (path in model_paths) {
    new_path = paste(temp_subdir2,basename(path),sep = .Platform$file.sep)
    new_model_paths = append(new_model_paths, new_path)
  }

  # Check to make sure predictions made on loaded model is the same as glm.pred
  prostate.hex = h2o.importFile(normalizePath(locate('smalldata/logreg/prostate.csv')))
  iris.hex = h2o.importFile(normalizePath(locate('smalldata/iris/iris.csv')))

  Log.info(paste("Model saved in", temp_subdir2))

  reloaded_models = {}
  for(path in new_model_paths) {
    Log.info(paste("Loading model from",path,sep=" "))
    model_obj = h2o.loadModel(path)
    reloaded_models = append(x = reloaded_models, values = model_obj)
  }

  Log.info("Running Predictions for Loaded Models")
  glm2 = reloaded_models[[1]]
  gbm2 = reloaded_models[[2]]
  speedrf2 = reloaded_models[[3]]
  rf2 = reloaded_models[[4]]
  nb2 = reloaded_models[[5]]
  dl2 = reloaded_models[[6]]

  glm.pred2 = pred_df(object = glm2, newdata = prostate.hex)
  gbm.pred2 = pred_df(object = gbm2, newdata = prostate.hex)
  speedrf.pred2 = pred_df(object = speedrf2, newdata = iris.hex)
  rf.pred2 = pred_df(object = rf2, newdata = iris.hex)
  nb.pred2 = pred_df(object = nb2, newdata = iris.hex)
  dl.pred2 = pred_df(object = dl2, newdata = iris.hex)

## Check to make sure scores are the same
  expect_equal(nrow(glm.pred), 380)
  expect_equal(glm.pred, glm.pred2)
  expect_equal(nrow(gbm.pred), 380)
  expect_equal(gbm.pred, gbm.pred2)
  expect_equal(nrow(rf.pred), 150)
  expect_equal(rf.pred, rf.pred2)
  expect_equal(nrow(nb.pred), 150)
  expect_equal(nb.pred, nb.pred2)
  expect_equal(nrow(dl.pred), 150)
  expect_equal(dl.pred, dl.pred2)
  expect_equal(nrow(speedrf.pred), 150)
  expect_equal(speedrf.pred, speedrf.pred2)

  testEnd()
}

doTest("HEX-1775 Test: Save and Load GLM Model", test.hex_1775)
