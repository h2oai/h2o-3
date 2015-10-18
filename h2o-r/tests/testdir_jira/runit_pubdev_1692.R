


test.pubdev.1692 <- function() {
  Log.info("Importing glrm_test/msq.csv data...")
  msq.dat <- read.csv(locate("smalldata/glrm_test/msq.csv"), header = FALSE)
  msq.hex <- h2o.importFile(locate("smalldata/glrm_test/msq.csv"), header = FALSE)
  print(summary(msq.hex))
  k <- 10
  
  # Final objective should be roughly as good as result from Madeleine's Julia code
  Log.info("Running GLRM with transform = 'NONE', loss = 'Quadratic', regularization_x = regularization_y = 'None'")
  init <- msq.dat[1:k,]
  fitH2O <- h2o.glrm(msq.hex, k = k, transform = "NONE", init = "User", user_y = init, loss = "Quadratic", regularization_x = "None", regularization_y = "None", max_iterations = 1000)
  Log.info(paste("Total Iterations:", fitH2O@model$iterations))
  Log.info(paste("Final Objective:", fitH2O@model$objective))
  expect_true(fitH2O@model$objective <= 131100)
  
  Log.info("Running GLRM with transform = 'DEMEAN', loss = 'Quadratic', regularization_x = regularization_y = 'None'")
  init <- scale(msq.dat, center = TRUE, scale = FALSE)[1:k,]
  fitH2O_dm <- h2o.glrm(msq.hex, k = k, transform = "DEMEAN", init = "User", user_y = init, loss = "Quadratic", regularization_x = "None", regularization_y = "None", max_iterations = 1000)
  Log.info(paste("Total Iterations:", fitH2O_dm@model$iterations))
  Log.info(paste("Final Objective:", fitH2O_dm@model$objective))
  expect_true(fitH2O_dm@model$objective <= 132900)
  
  Log.info("Running GLRM with transform = 'STANDARDIZE', loss = 'Quadratic', regularization_x = 'None', regularization_y = 'None'")
  init <- scale(msq.dat, center = TRUE, scale = TRUE)[1:k,]
  fitH2O_scale <- h2o.glrm(msq.hex, k = k, transform = "STANDARDIZE", init = "User", user_y = init, loss = "Quadratic", regularization_x = "None", regularization_y = "None", max_iterations = 1000)
  Log.info(paste("Total Iterations:", fitH2O_scale@model$iterations))
  Log.info(paste("Final Objective:", fitH2O_scale@model$objective))
  expect_true(fitH2O_scale@model$objective <= 100200)
  
  
}

doTest("PUBDEV-1692: GLRM final objective too large", test.pubdev.1692)
