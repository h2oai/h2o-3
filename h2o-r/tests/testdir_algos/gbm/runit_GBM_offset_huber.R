setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
####### This tests huber distribution w offset in gbm ######

test <- function(h) {
  library(MASS)
  data(Insurance)

	offset = log(Insurance$Holders) 
  class(Insurance$Group) <- "factor"
  class(Insurance$Age) <- "factor"
  df = data.frame(Insurance,offset)
  hdf = as.h2o(df,destination_frame = "hdf")

  hh = h2o.gbm(x = 1:3,y = "Claims",distribution ="huber",ntrees = 600,max_depth = 1,min_rows = 1,learn_rate = .1,offset_column = "offset",training_frame = hdf,min_split_improvement=0)
  ph = as.data.frame(h2o.predict(hh,newdata = hdf))
  hh@model$init_f
  mean(ph[,1])
  min(ph[,1])
  max(ph[,1])

  expect_equal(17.37666, hh@model$init_f, tolerance=1e-4)
  expect_equal(25.5357, mean(ph[,1]),tolerance=1e-4 )
  expect_equal(0.6066683, min(ph[,1]) ,tolerance=1e-3)
  expect_equal(60.99921, max(ph[,1]) ,tolerance=1e-3)
}
doTest("GBM offset Test: GBM w/ offset for gamma distribution", test)
