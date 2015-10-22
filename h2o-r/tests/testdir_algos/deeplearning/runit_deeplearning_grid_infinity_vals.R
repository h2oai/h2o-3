setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test.grid.infinity.values <- function(conn){
  prostate <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"))
  prostate[,5] <- as.factor(prostate[,5])

  grid_space <- list()
  grid_space$max_w2 <- c(-Inf,Inf)
  g <- h2o.grid(algo="deeplearning", x=c(2,3,4),y=5,training_frame=prostate, hyper_params=grid_space)

  expect_true(-Inf %in% lapply(g@model_ids, function(x) h2o.getModel(x)@allparameters$max_w2))
  expect_true(Inf %in% lapply(g@model_ids, function(x) h2o.getModel(x)@allparameters$max_w2))

  
}

doTest("Grid infinity values", test.grid.infinity.values)

