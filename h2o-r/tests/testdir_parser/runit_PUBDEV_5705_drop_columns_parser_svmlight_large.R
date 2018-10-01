setwd(normalizePath(dirname(
  R.utils::commandArgs(asValues = TRUE)$"f"
)))
source("../../scripts/h2o-r-test-setup.R")

# Tests parsing with skipped columns
test.parseSkippedColumnsSVMLight <- function() {
  f1svn <-
    h2o.importFile(locate("bigdata/laptop/parser/anSVMFile.csv"))
  svmfilepath <- locate("bigdata/laptop/parser/anSVMFile.svm")
  
  # test skipped columns for h2o.importFile
  e <-
    tryCatch(
      h2o.importFile(svmfilepath, skipped_columns=c(1,2)),
      error = function(x)
        x
    )
  print(e)
  # test skipped columns for h2o.uploadFile
  e2 <-
    tryCatch(
      h2o.uploadFile(svmfilepath, skipped_columns=c(1,2)),
      error = function(x)
        x
    )
  print(e2)
  
  assertCorrectSkipColumns(svmfilepath, f1svn, TRUE) # test importFile
  assertCorrectSkipColumns(svmfilepath, f1svn, FALSE) # test uploadFile
}

assertCorrectSkipColumns <-
  function(inputFileName, f1R,
           use_import) {
    if (use_import) {
      wholeFrame <<- h2o.importFile(inputFileName)
      wholeFrame2 <<-
        h2o.importFile(inputFileName, skipped_columns = c())
    } else  {
      wholeFrame <<- h2o.uploadFile(inputFileName)
      wholeFrame2 <<-
        h2o.uploadFile(inputFileName, skipped_columns = c())
    }
    
    expect_true(h2o.nrow(wholeFrame) == nrow(f1R))
    expect_true(h2o.nrow(wholeFrame2) == nrow(f1R))
    
    compareFrames(as.data.frame(wholeFrame), as.data.frame(wholeFrame2)) # returned frame without skipped_column and with empty skipped_column should be the same
    compareFramesSVM(f1R, wholeFrame) # compare return svm parser frame with original frame
   }

doTest("Test svmlight Parse with skipped columns", test.parseSkippedColumnsSVMLight)
