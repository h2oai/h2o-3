setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.rdoc_save_model.golden <- function() {
  prosPath <- locate("smalldata/extdata/prostate.csv", package="h2o")
  prostate.hex <- h2o.uploadFile(path = prosPath, destination_frame = "prostate.hex")

  if(ncol(prostate.hex) != 9) stop('import done incorrectly')
  newcols <- setdiff(names(prostate.hex), c('ID', 'GLEASON'))

  # Remove ID and GLEASON column from prostate data
  prostate.hex <- h2o.removeVecs(prostate.hex, c('ID', 'GLEASON'))

  if(ncol(prostate.hex) != 7) stop('there should be only 7 columns')
  if(TRUE %in% (names(prostate.hex) != newcols)) stop('incorrect columns removed')

  
}

doTest("R Doc Save Model", test.rdoc_save_model.golden)

