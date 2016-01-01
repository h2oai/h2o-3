setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



rmVecs <- function() {
  prosPath <- h2oTest.locate("smalldata/logreg/prostate.csv")

  prostate.hex = h2o.importFile(path = prosPath)

  if(ncol(prostate.hex) != 9) stop('import done incorrectly')
  newcols <- setdiff(names(prostate.hex), c('ID', 'GLEASON'))

  # Remove ID and GLEASON column from prostate data
  prostate.hex <- prostate.hex[-c(1,9)]

  if(ncol(prostate.hex) != 7) stop('there should be only 7 columns')
  if(TRUE %in% (names(prostate.hex) != newcols)) stop('incorrect columns removed')

  
}

h2oTest.doTest("Column removal on prostate : ", rmVecs)
