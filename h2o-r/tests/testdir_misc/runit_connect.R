setwd(normalizePath(dirname(
  R.utils::commandArgs(asValues = TRUE)$"f"
)))

h2oRDir <- normalizePath(paste(dirname(R.utils::commandArgs(asValues=TRUE)$"f"),"..","..",sep=.Platform$file.sep))
to_src <- c("aggregator.R", "classes.R", "connection.R","config.R", "constants.R", "logging.R", "communication.R",
            "kvstore.R", "frame.R", "targetencoder.R", "astfun.R","automl.R", "import.R", "parse.R", "export.R", "models.R", "edicts.R",
            "coxph.R", "coxphutils.R", "gbm.R", "glm.R", "gam.R", "glrm.R", "kmeans.R", "deeplearning.R", "randomforest.R", "generic.R",
            "naivebayes.R", "pca.R", "svd.R", "locate.R", "grid.R", "word2vec.R", "w2vutils.R", "stackedensemble.R", "rulefit.R",
            "predict.R", "xgboost.R", "isolationforest.R", "psvm.R", "segment.R", "tf-idf.R", "explain.R", "permutation_varimp.R", 
            "extendedisolationforest.R", "upliftrandomforest.R", "pipeline.R")
src_path <- paste(h2oRDir,"h2o-package","R",sep=.Platform$file.sep)
invisible(lapply(to_src,function(x){source(paste(src_path, x, sep = .Platform$file.sep))}))

source("../runitUtils/utilsR.R")
default.packages()
Log.info("Loaded default packages. Additional required packages must be loaded explicitly.")

h2o_connect_test <- function() {

  e <- tryCatch({
    h2o.connect()
  }, error = function(x) x)
  
  expect_false(is.null(e))
  print(e)
  err_message <- e[[1]]
  expect_true("Cannot connect to H2O server. Please check that H2O is running at http://localhost:54321/" == err_message)
}

doTest("Test that h2o.connect() throws error when connecting to a non-existent cluster", h2o_connect_test)
