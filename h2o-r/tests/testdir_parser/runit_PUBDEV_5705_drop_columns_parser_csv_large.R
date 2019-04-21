setwd(normalizePath(dirname(
  R.utils::commandArgs(asValues = TRUE)$"f"
)))
source("../../scripts/h2o-r-test-setup.R")

# Tests parsing with skipped columns
test.parseSkippedColumns<- function() {
  nrow <- 10000
  ncol <- 500
  seed <- 987654321
  frac1 <- 0.16
  f1 <-
    h2o.createFrame(
      rows = nrow,
      cols = ncol,
      randomize = TRUE,
      categorical_fraction = frac1,
      integer_fraction = frac1,
      binary_fraction = frac1,
      time_fraction = frac1,
      string_fraction = frac1,
      seed = seed,
      missing_fraction = 0.1
    )
  filePath <- getwd()
  fileName <- paste0(filePath, '/tempFrame.csv')
  
  h2o.downloadCSV(f1, fileName) # save generated file into csv
  fullFrameR <- as.data.frame(f1)
  skip_front <- c(1)
  skip_end <- c(h2o.ncol(f1))
  set.seed <- seed
  onePermute <- sample(h2o.ncol(f1))
  skipall <- onePermute
  skip99Per <- onePermute[1:floor(h2o.ncol(f1) * 0.99)]
  
  # test skipall for h2o.importFile
  e <-
    tryCatch(
      assertCorrectSkipColumns(fileName, fullFrameR, skipall, TRUE),
      error = function(x)
        x
    )
  print(e)
  # test skipall for h2o.uploadFile
  e2 <-
    tryCatch(
      assertCorrectSkipColumns(fileName, fullFrameR, skipall, FALSE),
      error = function(x)
        x
    )
  print(e2)
  
  # skip 99% of the columns randomly
  print("Testing skipping 99% of columns")
  assertCorrectSkipColumns(fileName, fullFrameR, skip99Per, TRUE, h2o.getTypes(f1)) # test importFile
  assertCorrectSkipColumns(fileName, fullFrameR, skip99Per, FALSE, h2o.getTypes(f1)) # test uploadFile

  if (file.exists(fileName))
    file.remove(fileName)
}

doTest("Test CSV Parse with skipped columns", test.parseSkippedColumns)
