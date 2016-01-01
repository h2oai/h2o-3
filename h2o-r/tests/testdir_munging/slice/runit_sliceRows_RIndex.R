setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



#setupRandomSeed(1193410486)
test.slice.rows <- function() {
  h2oTest.logInfo("Importing cars.csv data...\n")
  H <- h2o.importFile(h2oTest.locate("smalldata/junit/cars.csv"), "cars.hex")
#  R <- read.csv(h2oTest.locate("smalldata/cars.csv"))
  R <- as.data.frame(H)
  
  h2oTest.logInfo("Compare H[I,] and R[I,],  range of I is included in range of the data frame.")
  I <- c(1, 5, 30)
  h2oTest.logInfo("head(H[I,])")
  print(head(H[I,2:6]))
  print(I)
  print(H[I,2:6])

  h2oTest.logInfo("head(R[I,])")
  print(head(R[I,2:6]))

  print(H[I, 2:6])

  DSlice <- as.data.frame(H[I, 2:6])
  print(DSlice)
  RSlice <- R[I,2:6]
  print(dim(DSlice))
  print(dim(RSlice))
  expect_that(all(DSlice == RSlice), equals(T))
  
  h2oTest.logInfo("Compare H[I,] and R[I,],  range of I is included in range of the data frame.")
  
  I <- sample(1:nrow(R), nrow(R), replace=T)
  #h2oTest.logInfo("head(H[I,])")
  #print(head(H[I,2:6]))
  h2oTest.logInfo("head(R[I,])")
  print(head(R[I,2:6]))
  #DSlice <- as.data.frame(H[I, 2:6])

}

h2oTest.doTest("Slice Tests: Row slice using R index", test.slice.rows)

