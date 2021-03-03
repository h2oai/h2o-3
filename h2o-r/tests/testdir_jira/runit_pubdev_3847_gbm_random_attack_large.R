setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
######################################################################################
# Turn the PUBDEV-3847 issue into the test and check if it fails
######################################################################################
pubdev.3847.test <-
  function() {
      file <- locate("smalldata/jira/pubdev_3847.csv")o
      data <- h2o.importFile(file, destination_frame = "pubdev3847.data")
      response <- "class"
      features <- setdiff(names(data), response)

      ntrees <- 100
      max_depth <- 6
      min_rows <- 5
      learn_rate <- 0.1
      sample_rate <- 0.8
      col_sample_rate_per_tree <- 0.6
      nfolds <- 2
      min_split_improvement <- 1e-04

      Log.info("Create 10 GBM models with random seed to test if it fails.")
      for (i in 1:10){
          seed <- sample(1000:2000, 1)
          Log.info(paste(i, ": create GBM model with random seed:", seed))
          model <- h2o.gbm(x = features,y = response,training_frame = data,model_id ="amodel",ntrees = ntrees,
                        max_depth =max_depth ,min_rows = min_rows,learn_rate = learn_rate,
                        sample_rate =sample_rate ,col_sample_rate_per_tree =col_sample_rate_per_tree ,
                        nfolds = nfolds,min_split_improvement = min_split_improvement, seed=seed)
      }
  }

doTest("PUBDEV-3847", pubdev.3847.test)
