setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.rdoc_save_model.golden <- function(H2Oserver) {
  prosPath = system.file("extdata", "prostate.csv", package="h2o")
  prostate.hex = h2o.importFile(H2Oserver, path = prosPath, key = "prostate.hex")
  
  if(ncol(prostate.hex) != 9) stop('import done incorrectly')
  newcols = setdiff(names(prostate.hex), c('ID', 'GLEASON'))
  
  # Remove ID and GLEASON column from prostate data
  prostate.hex = h2o.removeVecs(prostate.hex, c('ID', 'GLEASON'))
  
  if(ncol(prostate.hex) != 7) stop('there should be only 7 columns')
  if(TRUE %in% (names(prostate.hex) != newcols)) stop('incorrect columns removed')  
  
  testEnd()
}

doTest("R Doc Save Model", test.rdoc_save_model.golden)

