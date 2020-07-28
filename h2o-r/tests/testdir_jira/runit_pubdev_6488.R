setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.tree.fetch <- function() {
  seed <- 1234
  set.seed(seed)
  
  data <- as.h2o(data.frame(
    x1 = c(1, NA, 2),
    x2 = c(2, NA, 1),
    y = c(1, 2,3)
  ))
  
  # Random Tree API test with a randomly generated matrix
  nrows <- 1000
  ntrees <- 50
  response <- sample(0:1,nrows,replace = TRUE)
  data <- data.frame(
    x1 = rnorm(nrows),
    x2 = rbinom(nrows, 1, 0.5),
    x3 = rbinom(nrows, 10, 0.5),
    response = response # Binomial response
  )
  
  data <- as.h2o(data)
  model <- h2o.xgboost(x= c("x1", "x2"), y = "response", training_frame = data, ntrees = ntrees, seed = seed)
  for (i in seq(1,ntrees)) {
    tree <- h2o.getModelTree(model, i)
    expect_false(is.null(tree))
    expect_false(is.null(tree@tree_decision_path))
    expect_false(is.null(tree@decision_paths[1]))
  }
  
  # Random Tree API test with a randomly generated matrix - categorical only
  nrows <- 1000
  ntrees <- 50
  data <- data.frame(
    x1 = rbinom(nrows, 1, 0.5),
    x2 = rbinom(nrows, 1, 0.5),
    response = rbinom(nrows, 1, 0.5) # Binomial response
  )
  
  data <- as.h2o(data)
  data$x1 <- h2o.asfactor(data$x1)
  data$x2 <- h2o.asfactor(data$x2)
  model <- h2o.xgboost(x= c("x1", "x2"), y = "response", training_frame = data, ntrees = ntrees, seed = seed)
  for (i in seq(1,ntrees)) {
    tree <- h2o.getModelTree(model, i)
    expect_false(is.null(tree))
    expect_false(is.null(tree@tree_decision_path))
    expect_false(is.null(tree@decision_paths[1]))
  }
  
  
  # Random Tree API test with a randomly generated matrix - multinomial response
  nrows <- 1000
  ntrees <- 50
  response <- sample(1:10,nrows,replace = TRUE)
  domain <- unique(response)
  data <- data.frame(
    x1 = rnorm(nrows),
    x2 = rbinom(nrows, 1, 0.5),
    response = response # Multinomial response
  )
  
  data <- as.h2o(data)
  data$response <- h2o.asfactor(data$response)
  model <- h2o.xgboost(x= c("x1", "x2"), y = "response", training_frame = data, ntrees = ntrees, seed = seed)
  for (i in seq(1,ntrees)) {
    for(clazz in domain){
    tree <- h2o.getModelTree(model, i,as.character(clazz))
    expect_false(is.null(tree))
    expect_false(is.null(tree@tree_decision_path))
    expect_false(is.null(tree@decision_paths[1]))
    }
  }
  
  # Random Tree API test with a randomly generated matrix with NAs - multinomial response
  nrows <- 1000
  ntrees <- 50
  states <- sample(state.name, 10)
  data <- data.frame(
    x1 = rnorm(nrows),
    x2 = rbinom(nrows, 1, 0.5),
    response = states # Multinomial response
  )
  
  # 10 percent of NAs
  for(i in sample(1:nrows, nrows / 10)){
    data$x1[i] <- NA
  }
  
  data <- as.h2o(data)
  data$response <- h2o.asfactor(data$response)
  model <- h2o.xgboost(x= c("x1", "x2"), y = "response", training_frame = data, ntrees = ntrees, seed = seed)
  for (i in seq(1,ntrees)) {
    for(clazz in states){
      tree <- h2o.getModelTree(model, i,as.character(clazz))
      expect_false(is.null(tree))
      expect_false(is.null(tree@tree_decision_path))
      expect_false(is.null(tree@decision_paths[1]))
    }
  }
}

doTest("Tree API fetch & parse test", test.tree.fetch)
