# setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

RESULT_DIR <- locate('results')
POJO = 'POJO'
MOJO = 'MOJO'


pubdev_4711_test <- function() {
  deeplearning_export()
  gbm_export()
  glm_export()
  glrm_export()
  k_means_export()
  naive_bayes_export()
  pca_export()
  drf_export()
  stacked_ensemble_export()
  word2vec_export()
}

expect_error <- function(model, format) {
  error_risen <- FALSE
  tryCatch(
    {
      if (format == 'POJO') {
        h2o.download_pojo(model, path = RESULT_DIR)
      } else if (format == 'MOJO') {
        h2o.download_mojo(model, path = RESULT_DIR)
      }
    },
    error=function(cond) {
      error_risen <<- TRUE
    }
  )
  if (!(error_risen)) {
    stop("There should be an error when trying to export ", model@model$algo_full_name, " to ", format)
  }
}

deeplearning_export <- function() {
  print("###### DEEPLEARNING ######")
  frame <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"), "prostate")
  model <- h2o.deeplearning(x = c(3, 4, 5, 6, 7, 8, 9), y = 2, training_frame = frame)
  h2o.download_pojo(model, path = RESULT_DIR)
  h2o.download_mojo(model, path = RESULT_DIR)
}

gbm_export <- function() {
  print("###### GBM ######")
  frame <- h2o.uploadFile(locate("smalldata/prostate/prostate.csv.zip"))
  frame[,2] <- as.factor(frame[,2])
  model <- h2o.gbm(x = 3:9, y = 2, training_frame = frame)
}

glm_export <- function() {
  print("###### GLM ######")
  frame <- h2o.uploadFile(locate("smalldata/prostate/prostate.csv.zip"))
  frame[,2] <- as.factor(frame[,2])
  frame[,4] <- as.factor(frame[,4])
  frame[,5] <- as.factor(frame[,5])
  frame[,6] <- as.factor(frame[,6])
  frame[,9] <- as.factor(frame[,9])
  model <- h2o.glm(x = 3:9, y = 2, training_frame = frame, family = "binomial")
  h2o.download_pojo(model, path = RESULT_DIR)
  h2o.download_mojo(model, path = RESULT_DIR)
}

glrm_export <- function() {
  print("###### GLRM ######")
  arrestsR <- read.csv(locate("smalldata/pca_test/USArrests.csv"), header = TRUE)
  arrestsH2O <- h2o.uploadFile(locate("smalldata/pca_test/USArrests.csv"), destination_frame = "arrestsH2O")
  initCent <- scale(arrestsR, center = TRUE, scale = FALSE)[1:4,]
  fitR <- svd(scale(arrestsR, center = TRUE, scale = FALSE))
  model <- h2o.glrm(arrestsH2O, k = 4, init = "User", user_y = initCent, transform = "DEMEAN", loss = "Quadratic", regularization_x = "None", regularization_y = "None", recover_svd = TRUE)
  expect_error(model, POJO)
  h2o.download_mojo(model, path = RESULT_DIR)
}

k_means_export <- function() {
  print("###### K-MEANS ######")
  frame <- h2o.uploadFile( locate("smalldata/logreg/benign.csv"))
  model <- h2o.kmeans(training_frame = frame, k = as.numeric(2))
  h2o.download_pojo(model, path = RESULT_DIR)
  h2o.download_mojo(model, path = RESULT_DIR)
}

naive_bayes_export <- function() {
  print("###### NAIVE BAYES ######")
  frame <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"), destination_frame= "frame")
  frame$CAPSULE <- as.factor(frame$CAPSULE)
  frame$RACE <- as.factor(frame$RACE)
  frame$DCAPS <- as.factor(frame$DCAPS)
  frame$DPROS <- as.factor(frame$DPROS)
  model <- h2o.naiveBayes(x = 3:9, y = 2, training_frame = frame, laplace = 0)
  h2o.download_pojo(model, path = RESULT_DIR)
  expect_error(model, MOJO)
}

pca_export <- function() {
  print("###### PCA ######")
  frame <- h2o.uploadFile(locate("smalldata/pca_test/USArrests.csv"))
  model <- h2o.prcomp(training_frame = frame, k = as.numeric(2))
  h2o.download_pojo(model, path = RESULT_DIR)
  expect_error(model, MOJO)
}

drf_export <- function() {
  print("###### DRF ######")
  frame <- h2o.uploadFile(locate("smalldata/iris/iris22.csv"), "iris.hex")
  model  <- h2o.randomForest(y = 5, x = 1:4, training_frame = frame,
                               ntrees = 50, max_depth = 100)
  h2o.download_pojo(model, path = RESULT_DIR)
  h2o.download_mojo(model, path = RESULT_DIR)
}

stacked_ensemble_export <- function() {
  print("###### STACKED ENSEMBLE ######")
  frame <- h2o.uploadFile(locate("smalldata/testng/higgs_test_5k.csv"),
                          destination_frame = "higgs_frame_5k")
  y <- "response"
  x <- setdiff(names(frame), y)
  frame[,y] <- as.factor(frame[,y])
  nfolds <- 5
  my_gbm <- h2o.gbm(x = x,
                    y = y,
                    training_frame = frame,
                    distribution = "bernoulli",
                    ntrees = 10,
                    nfolds = nfolds,
                    fold_assignment = "Modulo",
                    keep_cross_validation_predictions = TRUE,
                    seed = 1)
  my_rf <- h2o.randomForest(x = x,
                            y = y,
                            training_frame = frame,
                            ntrees = 10,
                            nfolds = nfolds,
                            fold_assignment = "Modulo",
                            keep_cross_validation_predictions = TRUE,
                            seed = 1)
  model <- h2o.stackedEnsemble(x = x,
                                y = y,
                                training_frame = frame,
                                model_id = "my_ensemble_binomial",
                                base_models = list(my_gbm@model_id, my_rf@model_id))
  expect_error(model, POJO)
  h2o.download_mojo(model, path = RESULT_DIR)
}

word2vec_export <- function() {
  print("###### WORD2VEC ENSEMBLE ######")
  frame <- as.h2o(data.frame(
      Word = c("a", "b"), V1 = c(0, 1), V2 = c(1, 0), V3 = c(0.2, 0.8),
      stringsAsFactors = FALSE
  ))
  model <- h2o.word2vec(pre_trained = frame, vec_size = 3)
  expect_error(model, POJO)
  h2o.download_mojo(model, path = RESULT_DIR)
}

isofor_export <- function() {
  print("###### ISOLATION FOREST ######")
  frame <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"), destination_frame= "frame")
  model <- h2o.isolationForest(frame)
  expect_error(model, POJO)
  h2o.download_mojo(model, path = RESULT_DIR)
}

# doTest("Perform the test for pubdev 4711", pubdev_4711_test)
deeplearning_export()
gbm_export()
glm_export()
glrm_export()
k_means_export()
naive_bayes_export()
pca_export()
drf_export()
stacked_ensemble_export()
word2vec_export()
isofor_export()