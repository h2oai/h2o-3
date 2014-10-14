##
##

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test.match <- function(conn) {

  hex <- as.h2o(conn, iris)

  print(hex)

  sub <- hex$Species %in% c("setosa", "versicolor")
  print(sub)
  
  hh <- hex[sub,]

  print(hh)
  
  print(dim(hh))
    
  testEnd()
}

doTest("test match", test.match)

