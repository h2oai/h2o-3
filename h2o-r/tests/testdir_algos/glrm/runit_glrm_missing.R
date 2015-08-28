setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test.glrm.missing <- function() {
  seed <- 1234
  missing_frac <- seq(from = 0.1, to = 0.9, by = 0.1)
  
  obj_val   <- rep(0, length(missing_frac))
  obj_delta <- rep(0, length(missing_frac))
  iters     <- rep(0, length(missing_frac))
  step      <- rep(0, length(missing_frac))
  
  for(i in 1:length(missing_frac)) {
    f <- missing_frac[i]
    Log.info("Importing USArrests.csv data...\n")
    arrests.hex <- h2o.uploadFile(locate("smalldata/pca_test/USArrests.csv"))
    
    Log.info(paste("Inserting ", 100 * f, "% missing entries:\n", sep = ""))
    h2o.insertMissingValues(data = arrests.hex, fraction = f, seed = seed)
    print(summary(arrests.hex))
    
    Log.info(paste("H2O GLRM with ", 100 * f, "% missing entries:\n", sep = ""))
    arrests.glrm <- h2o.glrm(training_frame = arrests.hex, k = 4, gamma_x = 0, gamma_y = 0, 
                             init = "PlusPlus", max_iterations = 2000, min_step_size = 1e-6, seed = seed)
    
    obj_val[i] <- arrests.glrm@model$objective
    obj_delta[i] <- arrests.glrm@model$avg_change_obj
    iters[i] <- arrests.glrm@model$iterations
    step[i] <- arrests.glrm@model$step_size
    
    h2o.rm(arrests.glrm@model$loading_key$name)    # Remove loading matrix to free memory
  }
  
  print(data.frame(Fraction = missing_frac, 
                   Objective = obj_val,
                   AvgChangeObj = obj_delta,
                   Iterations = iters, 
                   StepSize = step))
  testEnd()
}

doTest("GLRM Test: USArrests Data with Missing Entries Inserted", test.glrm.missing)
