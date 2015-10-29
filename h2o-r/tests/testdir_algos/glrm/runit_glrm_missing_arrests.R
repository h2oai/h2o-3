


test.glrm.arrests_miss <- function() {
  missing_frac <- seq(from = 0.1, to = 0.8, by = 0.1)
  stats_names <- c("Fraction", "Objective", "AvgChangeObj", "Iterations", "StepSize", "TrainSSE", "ValidSSE", "MissingASE")
  model_stats <- data.frame(matrix(0, nrow = length(missing_frac), ncol = length(stats_names)))
  colnames(model_stats) <- stats_names
  
  Log.info("Importing USArrests.csv data and saving for validation...\n")
  arrests.full <- h2o.uploadFile(locate("smalldata/pca_test/USArrests.csv"))
  totobs <- nrow(arrests.full) * ncol(arrests.full)
  
  for(i in 1:length(missing_frac)) {
    f <- missing_frac[i]
    
    Log.info(paste("Copying data and inserting ", 100 * f, "% missing entries:\n", sep = ""))
    arrests.miss <- h2o.assign(arrests.full, "arrests.miss")
    h2o.insertMissingValues(data = arrests.miss, fraction = f, seed = SEED)
    print(summary(arrests.miss))
    
    Log.info(paste("H2O GLRM with ", 100 * f, "% missing entries:\n", sep = ""))
    arrests.glrm <- h2o.glrm(training_frame = arrests.miss, validation_frame = arrests.full, ignore_const_cols = FALSE, k = 4, loss = "Quadratic", 
                             regularization_x = "None", regularization_y = "None", init = "PlusPlus", max_iterations = 10, min_step_size = 1e-6, seed = SEED)
    
    # Check imputed data and error metrics
    trainmm <- arrests.glrm@model$training_metrics@metrics
    validmm <- arrests.glrm@model$validation_metrics@metrics
    checkGLRMPredErr(arrests.glrm, arrests.miss, arrests.full, tolerance = 1e-6)
    expect_equal(trainmm$numerr, arrests.glrm@model$objective, tolerance = 1e-6)
    expect_equal(trainmm$caterr, 0)
    expect_equal(validmm$caterr, 0)
    expect_true(validmm$numcnt > trainmm$numcnt)
    expect_equal(validmm$numcnt, totobs)
    h2o.rm(arrests.glrm@model$representation_name)    # Remove X matrix to free memory
    
    # Save relevant information from this run
    misserr <- (validmm$numerr - trainmm$numerr) / (validmm$numcnt - trainmm$numcnt)   # Average squared error over missing entries only
    model_stats[i,] <- c(missing_frac[i], arrests.glrm@model$objective, arrests.glrm@model$avg_change_obj, 
                         arrests.glrm@model$iterations, arrests.glrm@model$step_size, trainmm$numerr, 
                         validmm$numerr, misserr)
  }
  print(model_stats)
  
}

doTest("GLRM Test: USArrests Data with Missing Entries Inserted", test.glrm.arrests_miss)
