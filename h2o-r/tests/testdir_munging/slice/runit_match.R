##
##

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test.match <- function(conn) {

  hex <- as.h2o(conn, iris)

  print(hex)

  
  hh <- hex[hex$Species %in% c("setosa","versicolor"),]

  print(hh)
  
  print(dim(hh))
    
  testEnd()
}

doTest("test cbind", test.match)

