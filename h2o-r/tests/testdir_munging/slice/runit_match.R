##
##

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test.match <- function(conn) {

  hex <- as.h2o(conn, iris)


  Log.info("Printing the head of the iris data frame.")
  print(hex)


  Log.info("doing the match: hex$Species %in% c(\"setosa\", \"versicolor\")")
  sub <- hex$Species %in% c("setosa", "versicolor")

  Log.info("Printing out the subset bit vec from the match call")
  print(sub)
 
  Log.info("performing the subsetting: hex[sub,]") 
  hh <- hex[sub,]

  Log.info("print the head of the subsetted frame")
  print(hh)
  
  Log.info("print the dim of the subsetted frame")
  print(dim(hh))
    
  testEnd()
}

doTest("test match", test.match)

