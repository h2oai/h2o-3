setwd(normalizePath(dirname(
  R.utils::commandArgs(asValues = TRUE)$"f"
)))
source("../../scripts/h2o-r-test-setup.R")

# Tests parsing with skipped columns
test.parseSkippedColumnsParquet<- function() {
  f1 <-
    h2o.importFile(locate("smalldata/airlines/AirlinesTrain.csv.zip"))
  fileName <- locate("smalldata/parser/parquet/airlines-simple.snappy.parquet")

  parquetFile <- h2o.importFile(fileName)
  compareFrames(as.data.frame(f1), as.data.frame(parquetFile))

  fullFrameR <- as.data.frame(f1)
  skip_front <- c(1)
  skip_end <- c(h2o.ncol(f1))
  set.seed <- 12345
  onePermute <- sample(h2o.ncol(f1))
  skipall <- onePermute
  skip90Per <- onePermute[1:floor(h2o.ncol(f1) * 0.9)]
  
  # test skipall for h2o.importFile
  e <-
    tryCatch(
      assertCorrectSkipColumns(fileName, fullFrameR, skipall, TRUE, h2o.getTypes(f1)),
      error = function(x)
        x
    )
  print(e)
  # test skipall for h2o.uploadFile
  e2 <-
    tryCatch(
      assertCorrectSkipColumns(fileName, fullFrameR, skipall, FALSE, h2o.getTypes(f1)),
      error = function(x)
        x
    )
  print(e2)
  
  # skip 90% of the columns randomly
  print("Testing skipping 99% of columns")
  assertCorrectSkipColumns(fileName, fullFrameR, skip90Per, TRUE, h2o.getTypes(f1)) # test importFile
  assertCorrectSkipColumns(fileName, fullFrameR, skip90Per, FALSE, h2o.getTypes(f1)) # test uploadFile
}

compareFrames <- function(frame1, frame2, prob=0.5, tolerance=1e-6) {
  expect_true(nrow(frame1) == nrow(frame2) && ncol(frame1) == ncol(frame2), info="frame1 and frame2 are different in size.")
  for (colInd in range(1, ncol(frame1))) {
    temp1=as.numeric(frame1[,colInd])
    temp2=as.numeric(frame2[,colInd])
    for (rowInd in range(1,nrow(frame1))) {
      if (runif(1,0,1) < prob)
        if (is.na(temp1[rowInd])) {
          expect_true(is.na(temp2[rowInd]), info=paste0("Errow at row ", rowInd, ". Frame is value is na but Frame 2 value is ", temp2[rowInd]))
        } else {
          expect_true((abs(temp1[rowInd]-temp2[rowInd])/max(1,abs(temp1[rowInd]), abs(temp2[rowInd])))< tolerance, info=paste0("Error at row ", rowInd, ". Frame 1 value ", temp1[rowInd], ". Frame 2 value ", temp2[rowInd]))
        }
    }
  }
}

doTest("Test Parquet Parse with skipped columns", test.parseSkippedColumnsParquet)
