


test.glrm.prostate_miss <- function() {
  missing_frac <- seq(from = 0.1, to = 0.8, by = 0.1)
  stats_names <- c("Fraction", "Objective", "AvgChangeObj", "Iterations", "StepSize", 
                   "TrainNumSSE", "ValidNumSSE", "TrainCatErr", "ValidCatErr", 
                   "MissingNumASE", "MissingCatErr")
  model_stats <- data.frame(matrix(0, nrow = length(missing_frac), ncol = length(stats_names)))
  colnames(model_stats) <- stats_names
  
  Log.info("Importing prostate_cat.csv data and saving for validation...")
  prostate.full <- h2o.uploadFile(locate("smalldata/prostate/prostate_cat.csv"), destination_frame= "prostate.hex", na.strings = rep("NA", 8))
  totobs <- sum(!is.na(prostate.full))
  print(summary(prostate.full))
  
  for(i in 1:length(missing_frac)) {
    f <- missing_frac[i]
    
    Log.info(paste("Copying data and inserting ", 100 * f, "% missing entries:\n", sep = ""))
    prostate.miss <- h2o.assign(prostate.full, "prostate.miss")
    h2o.insertMissingValues(data = prostate.miss, fraction = f, seed = SEED)
    print(summary(prostate.miss))
    
    Log.info(paste("H2O GLRM with ", 100 * f, "% missing entries:\n", sep = ""))
    prostate.glrm <- h2o.glrm(training_frame = prostate.miss, validation_frame = prostate.full, ignore_const_cols = FALSE, k = 8, init = "SVD", loss = "Quadratic",
                              max_iterations = 2000, gamma_x = 0.5, gamma_y = 0.5, regularization_x = "L1", regularization_y = "L1", min_step_size = 1e-6, seed = SEED)
    
    # Check imputed data and error metrics
    trainmm <- prostate.glrm@model$training_metrics@metrics
    validmm <- prostate.glrm@model$validation_metrics@metrics
    checkGLRMPredErr(prostate.glrm, prostate.miss, prostate.full, tolerance = 1e-5)
    expect_true(validmm$numcnt >= trainmm$numcnt)
    expect_true(validmm$catcnt >= trainmm$catcnt)
    expect_true((trainmm$numcnt + validmm$numcnt) < totobs)
    expect_equal(validmm$numcnt + validmm$catcnt, totobs)
    h2o.rm(prostate.glrm@model$representation_name)    # Remove X matrix to free memory
    
    # Save relevant information from this run
    miss_numerr <- (validmm$numerr - trainmm$numerr) / (validmm$numcnt - trainmm$numcnt)   # Average squared error over missing entries only
    miss_caterr <- (validmm$caterr - trainmm$caterr) / (validmm$catcnt - trainmm$catcnt)   # Average misclassifications over categorical missing entries
    model_stats[i,] <- c(missing_frac[i], prostate.glrm@model$objective, prostate.glrm@model$avg_change_obj, 
                         prostate.glrm@model$iterations, prostate.glrm@model$step_size, trainmm$numerr, validmm$numerr, 
                         trainmm$caterr, validmm$caterr, miss_numerr, miss_caterr)
  }
  print(model_stats)
  
}

doTest("GLRM Test: Prostate Data with Missing Values Inserted", test.glrm.prostate_miss)
