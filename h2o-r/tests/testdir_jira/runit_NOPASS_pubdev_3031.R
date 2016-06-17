setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.import.strange.characters <- function() {

  df = data.frame(C1 = c(0,1,1), C2 = c("a","b","a"), CÃ = c(0.1, 0.5, 2.3))
  df.hex = as.h2o(df)
  print("Dimension of test data frame...")
  print(dim(df))
  print(df)
  print("Dimension of test data frame uploaded into h2o...")
  print(dim(df.hex))
  print(df.hex)
  if(checkEqualsNumeric(dim(df),dim(df.hex))) stop("Dimension of frame in R doesn't match dimension as h2o frame")
  
}

doTest("Error Importing Frame with Strange Character in Headers ", test.import.strange.characters)
