


#setupRandomSeed(1193410486)
test.slice.rows <- function() {
  Log.info("Importing cars.csv data...\n")
  H <- h2o.importFile(locate("smalldata/junit/cars.csv"), "cars.hex")
#  R <- read.csv(locate("smalldata/cars.csv"))
  R <- as.data.frame(H)
  
  Log.info("Compare H[I,] and R[I,],  range of I is included in range of the data frame.")
  I <- c(1, 5, 30)
  Log.info("head(H[I,])")
  print(head(H[I,2:6]))
  print(I)
  print(H[I,2:6])

  Log.info("head(R[I,])")
  print(head(R[I,2:6]))

  print(H[I, 2:6])

  DSlice <- as.data.frame(H[I, 2:6])
  print(DSlice)
  RSlice <- R[I,2:6]
  print(dim(DSlice))
  print(dim(RSlice))
  expect_that(all(DSlice == RSlice), equals(T))
  
  Log.info("Compare H[I,] and R[I,],  range of I is included in range of the data frame.")
  
  I <- sample(1:nrow(R), nrow(R), replace=T)
  Log.info("head(H[I,])")
  print(head(H[I,2:6]))
  Log.info("head(R[I,])")
  print(head(R[I,2:6]))
  DSlice <- as.data.frame(H[I, 2:6])
  
  
}

doTest("Slice Tests: Row slice using R index", test.slice.rows)

