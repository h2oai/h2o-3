

test.glrm.stream <- function() {
  m <- 1000; n <- 100; k <- 10; f <- 0.8
  Y <- matrix(rnorm(k*n), nrow = k, ncol = n)
  X <- matrix(rnorm(m*k), nrow = m, ncol = k)
  
  df <- X %*% Y
  train <- df[1:(0.8*m),]
  strm <- df[(0.8*m+1):nrow(df),]
  
  Log.info(paste("Uploading training matrix with rows =", nrow(train), "and cols =", ncol(train)))
  train.h2o <- as.h2o(train)
  
  Log.info(paste("Build a GLRM of rank k =", k, "on train with quadratic loss and no regularization"))
  glrm.train <- h2o.glrm(training_frame = train.h2o, k = k, init = "PlusPlus", loss = "Quadratic", regularization_x = "None", regularization_y = "None")
  print(glrm.train)
  train.y <- as.matrix(glrm.train@model$archetypes)
  train.x <- h2o.getFrame(glrm.train@model$representation_name)
  
  Log.info(paste("Uploading streaming matrix with rows =", nrow(strm), "and cols =", ncol(strm)))
  strm.h2o <- as.h2o(strm)
  
  Log.info(paste("Build a GLRM of rank k =", k, "on stream using prior model's archetypes as initial Y and single X update"))
  glrm.strm <- h2o.glrm(training_frame = strm.h2o, k = k, init = "User", user_y = train.y, loss = "Quadratic", regularization_x = "None", regularization_y = "None", max_updates = 1)
  print(glrm.strm)
  strm.y <- as.matrix(glrm.strm@model$archetypes)
  strm.x <- h2o.getFrame(glrm.strm@model$representation_name)
  
  sqerr.y <- sum((train.y - strm.y)^2) / prod(dim(train.y))
  Log.info(paste("Average squared error between old and new Y =", sqerr.y))
  expect_true(sqerr.y <= 1e-6)
  
  Log.info("Copy streaming matrix and insert missing values")
  strm.miss <- strm; strm.miss[1,1] <- NA   # TODO: Seed some random percentage of missing values
  strm.miss.h2o <- as.h2o(strm.miss)
  
  Log.info("Build GLRM on stream with missing values and validate against full stream")
  glrm.miss <- h2o.glrm(training_frame = strm.miss.h2o, k = k, init = "User", user_y = train.y, loss = "Quadratic", regularization_x = "None", regularization_y = "None", max_updates = 1)
  print(glrm.miss)
  strm.miss.y <- as.matrix(glrm.miss@model$archetypes)
  strm.miss.x <- h2o.getFrame(glrm.miss@model$representation_name)
  
  sqerr.y2 <- sum((train.y - strm.miss.y)^2 / prod(dim(train.y)))
  Log.info(paste("Average squared error between old and new Y =", sqerr.y2))
  expect_true(sqerr.y2 <= 1e-6)
}

doTest("GLRM Test: Online Streaming with Reconstruction", test.glrm.stream)
