setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

# Connect to a cluster
# Set this to True if you want to fetch the data directly from S3.
# This is useful if your cluster is running in EC2.
data_source_is_s3 = F

locate_source <- function(s) {
  if (data_source_is_s3)
    myPath <- paste0("s3n://h2o-public-test-data/", s)
  else
    myPath <- locate(s)
}

test.whd_zip.demo <- function(conn) {
  missing_frac <- 0.2
  train_frac <- 0.8
  k_dim <- 5
  
  Log.info("Import and parse ACS 2013 5-year DP02 demographic data...")
  acs_orig <- h2o.uploadFile(locate_source("bigdata/laptop/census/ACS_13_5YR_DP02_cleaned.zip"), col.types = c("enum", rep("numeric", 149)))
  print(summary(acs_orig))
  acs_zcta_col <- acs_orig$ZCTA5
  acs_full <- acs_orig[,-which(colnames(acs_orig) == "ZCTA5")]
  
  Log.info("Import and parse WHD 2014-2015 labor violations data...")
  whd_zcta <- h2o.uploadFile(locate_source("bigdata/laptop/census/whd_zcta_cleaned.zip"), col.types = c(rep("enum", 7), rep("numeric", 97)))
  print(summary(whd_zcta))
  
  # Log.info(paste0("Create validation data with ", 100*missing_frac, "% missing entries"))
  # acs_miss <- h2o.uploadFile(locate_source("bigdata/laptop/census/ACS_13_5YR_DP02_cleaned.zip"), col.types = c("enum", rep("numeric", 149)))
  # acs_miss <- acs_miss[,-which(colnames(acs_miss) == "ZCTA5")]
  # acs_miss <- h2o.insertMissingValues(data = acs_miss, fraction = missing_frac, seed = SEED)
  # print(summary(acs_miss))
  
  Log.info(paste("Run GLRM to reduce ZCTA demographics to k =", k_dim, "archetypes"))
  # Log.info("Grid search for optimal regularization weights")
  # gamma_x_grid <- c(0.25, 0.5)
  # gamma_y_grid <- c(0.5, 0.75)
  # search_params = list(gamma_x = gamma_x_grid, gamma_y = gamma_y_grid)
  # acs_grid <- h2o.grid("glrm", is_supervised = FALSE, training_frame = acs_miss, validation_frame = acs_full, 
  #                     k = k_dim, transform = "STANDARDIZE", init = "PlusPlus", loss = "Quadratic", max_iterations = 100, 
  #                     regularization_x = "Quadratic", regularization_y = "L1", seed = SEED, hyper_params = search_params)
  # grid_models <- lapply(acs_grid@model_ids, function(i) { model = h2o.getModel(i) })
  
  # Log.info("Select model that achieves lowest total validation error")
  # valid_numerr <- sapply(grid_models, function(m) { m@model$validation_metrics@metrics$numerr })
  # valid_caterr <- sapply(grid_models, function(m) { m@model$validation_metrics@metrics$caterr })
  # acs_best <- grid_models[[which.min(valid_numerr + valid_caterr)]]
  # acs_best_full <- h2o.glrm(training_frame = acs_full, k = k_dim, transform = "STANDARDIZE", init = "PlusPlus",
  #                       loss = "Quadratic", max_iterations = 100, regularization_x = "Quadratic", regularization_y = "L1",
  #                       gamma_x = acs_best@parameters$gamma_x, gamma_y = acs_best@parameters$gamma_y, seed = SEED)
  acs_best_full <- h2o.glrm(training_frame = acs_full, k = k_dim, transform = "STANDARDIZE", init = "PlusPlus",
                       loss = "Quadratic", max_iterations = 100, regularization_x = "Quadratic", regularization_y = "L1",
                       gamma_x = 0.25, gamma_y = 0.5, seed = SEED)
  print(acs_best_full)
  
  Log.info("Embedding of ZCTAs into archetypes (X):")
  zcta_arch_x <- h2o.getFrame(acs_best_full@model$loading_key$name)
  print(head(zcta_arch_x))
  Log.info("Archetype to full feature mapping (Y):")
  arch_feat_y <- acs_best_full@model$archetypes
  print(arch_feat_y)
  
  Log.info(paste0("Split WHD data into test/train with ratio = ", 100*(1-train_frac), "/", 100*train_frac))
  split <- h2o.runif(whd_zcta)
  train <- whd_zcta[split <= train_frac,]
  test <- whd_zcta[split > train_frac,]
  
  Log.info("Build a GBM model to predict repeat violators and score")
  myY <- "flsa_repeat_violator"
  myX <- setdiff(4:ncol(train), which(colnames(train) == myY))
  orig_time <- system.time(gbm_orig <- h2o.gbm(x = myX, y = myY, training_frame = train, validation_frame = test, 
                                ntrees = 10, max_depth = 6, distribution = "multinomial"))
  
  Log.info("Replace zcta5_cd column in WHD data with GLRM archetypes")
  zcta_arch_x$zcta5_cd <- acs_zcta_col
  whd_arch <- h2o.merge(whd_zcta, zcta_arch_x, all.x = TRUE, all.y = FALSE)
  whd_arch$zcta5_cd <- NULL
  print(summary(whd_arch))
  
  Log.info(paste0("Split modified WHD data into test/train with ratio = ", 100*(1-train_frac), "/", 100*train_frac))
  train_mod <- whd_arch[split <= train_frac,]
  test_mod <- whd_arch[split > train_frac,]
  
  Log.info("Build a GBM model on modified WHD data to predict repeat violators and score")
  myX <- setdiff(4:ncol(train_mod), which(colnames(train_mod) == myY))
  mod_time <- system.time(gbm_mod <- h2o.gbm(x = myX, y = myY, training_frame = train_mod, validation_frame = test_mod, 
                              ntrees = 10, max_depth = 6, distribution = "multinomial"))

  Log.info("Performance comparison:")
  gbm_sum <- data.frame(original  = c(orig_time[3], gbm_orig@model$training_metric@metrics$MSE, gbm_orig@model$validation_metric@metrics$MSE),
                        reduced   = c(mod_time[3], gbm_mod@model$training_metric@metrics$MSE, gbm_mod@model$validation_metric@metrics$MSE),
                        row.names = c("runtime", "train_mse", "test_mse"))
  print(gbm_sum)
}

doTest("Test out WHD Labor Violations Demo", test.whd_zip.demo)
