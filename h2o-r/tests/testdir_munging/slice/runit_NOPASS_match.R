##
##

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test.match <- function() {

  hex <- as.h2o(iris)

  #Log.info("Printing the head of the iris data frame.")
  print(hex)

  #Log.info("doing the match: hex$Species %in% c(\"setosa\", \"versicolor\")")
  sub_h2o_in    <- hex$Species %in% c("setosa", "versicolor")
  sub_h2o_match <- match(hex$Species, c("setosa", "versicolor"))
  sub_r         <- iris$Species %in% c("setosa", "versicolor")

  #Log.info("Printing out the subset bit vec from the match call")
  print(sub_h2o_in)
  print(sub_h2o_match)
  head(sub_r)
 
  #Log.info("performing the subsetting: hex[sub,]")
  hh_in    <- hex[sub_h2o_in,]
  hh_match <- hex[sub_h2o_match,]
  hh_r     <- iris[sub_r,]

  #Log.info("print the head of the subsetted frame")
  print(hh_in)
  print(hh_match)
  head(hh_r)
  
  #Log.info("print the dim of the subsetted frame")
  print(dim(hh_in))
  print(dim(hh_match))
  print(dim(hh_r))

  #Log.info("check that the number of rows in the subsetted h2o frames match r")
  expect_true(all(dim(hh_in) == dim(hh_r)))
  expect_true(all(dim(hh_match) == dim(hh_r)))

  #Log.info("doing the match: hex$Sepal.Length %in% c(5.1)
  sub_h2o_in    <- hex$Sepal.Length %in% c(5.1)
  sub_h2o_match <- match(hex$Sepal.Length, c(5.1))
  sub_r         <- iris$Sepal.Length %in% c(5.1)

  #Log.info("Printing out the subset bit vec from the match call")
  print(sub_h2o_in)
  print(sub_h2o_match)
  head(sub_r)

  #Log.info("performing the subsetting: hex[sub,]")
  hh_in    <- hex[sub_h2o_in,]
  hh_match <- hex[sub_h2o_match,]
  hh_r     <- iris[sub_r,]

  #Log.info("print the head of the subsetted frame")
  print(hh_in)
  print(hh_match)
  head(hh_r)

  #Log.info("print the dim of the subsetted frame")
  print(dim(hh_in))
  print(dim(hh_match))
  print(dim(hh_r))

  #Log.info("check that the number of rows in the subsetted h2o frames match r")
  expect_true(all(dim(hh_in) == dim(hh_r)))
  expect_true(all(dim(hh_match) == dim(hh_r)))
    
  testEnd()
}

doTest("test match", test.match)

