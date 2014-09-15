setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

#setupRandomSeed(1193410486)
test.slice.rows <- function(conn) {
  Log.info("Importing cars.csv data...\n")
  H <- h2o.importFile(conn, locate("smalldata/junit/cars.csv"), "cars.hex")
#  R <- read.csv(locate("smalldata/cars.csv"))
  R <- as.data.frame(H)
  
  Log.info("Compare H[I,] and R[I,],  range of I is included in range of the data frame.")
  I <- c(30, 1, 5)
  Log.info("head(H[I,])")
  print(head(H[I,2:6]))
  Log.info("head(R[I,])")
  print(head(R[I,2:6]))
  DSlice <- as.data.frame(H[I, 2:6])
  RSlice <- R[I,2:6]
  expect_that(all(DSlice == RSlice), equals(T))
  
  Log.info("Compare H[I,] and R[I,],  range of I is included in range of the data frame.")
  
  I <- sample(1:nrow(R), 1000, replace=T)
  Log.info("head(H[I,])")
  print(head(H[I,2:6]))
  Log.info("head(R[I,])")
  print(head(R[I,2:6]))
  DSlice <- as.data.frame(H[I, 2:6])
  RSlice <- R[I,2:6]
  expect_that(all(DSlice[!is.na(DSlice)] == RSlice[!is.na(RSlice)]), equals(T))

  
  Log.info("Compare H[I,] and R[I,],  range of I goes beyond the range of the data frame.")
  I <- c(nrow(R) + 1, nrow(R) + 2, I)
  Log.info("head(H[I,])")
  print(head(H[I,]))
  Log.info("head(R[I,])")
  print(head(R[I,]))  
  DSlice <- as.data.frame(H[I, 2:6])
  RSlice <- R[I,2:6]
  expect_that(all(is.na(DSlice) == is.na(RSlice)), equals(T))
  expect_that(all(DSlice[!is.na(DSlice)] == RSlice[!is.na(RSlice)]), equals(T))

  Log.info("Compares H[null,]")
  
  testEnd()
}

doTest("Slice Tests: Row slice using R index", test.slice.rows)

