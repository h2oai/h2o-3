setwd(normalizePath(dirname(
  R.utils::commandArgs(asValues = TRUE)$"f"
)))
source("../../scripts/h2o-r-test-setup.R")

# Tests parsing with skipped columns
test.parseSkippedColumnsFolder <- function() {
  originalFull <- h2o.importFile(locate("smalldata/synthetic_perfect_separation"))
  allColnames <- h2o.names(originalFull)
  allTypeDict <- h2o.getTypes(originalFull)
  pathHeader <- locate("smalldata/synthetic_perfect_separation")
  
  set.seed <- 12345
  onePermute <- sample(h2o.ncol(originalFull))
  skipall <- onePermute
  skip50Per <- onePermute[1:floor(h2o.ncol(originalFull) * 0.5)]
  skipAll <- c(1:h2o.ncol(originalFull))

  # test skipall for h2o.importFile
  e <-
    tryCatch(
      assertCorrectSkipColumns(pathHeader, originalFull, skipall, TRUE, h2o.getTypes(originalFull)),
      error = function(x)
        x
    )
  print(e)
  
  # skip 50% of the columns randomly
  print("Testing skipping 50% of columns")
  assertCorrectSkipColumns( pathHeader, as.data.frame(originalFull), skip50Per,TRUE, h2o.getTypes(originalFull)) 
}

doTest("Test parsing a folder", test.parseSkippedColumnsFolder)
