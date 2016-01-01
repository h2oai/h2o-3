setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
##
##




test.match <- function() {

  hex <- as.h2o(iris)

  #h2oTest.logInfo("Printing the head of the iris data frame.")
  print(hex)

  #h2oTest.logInfo("doing the match: hex$Species %in% c(\"setosa\", \"versicolor\")")
  sub_h2o_in    <- hex$Species %in% c("setosa", "versicolor")
  sub_h2o_match <- match(hex$Species, c("setosa", "versicolor"))
  sub_r         <- iris$Species %in% c("setosa", "versicolor")

  #h2oTest.logInfo("Printing out the subset bit vec from the match call")
  print(sub_h2o_in)
  print(sub_h2o_match)
  head(sub_r)
 
  #h2oTest.logInfo("performing the subsetting: hex[sub,]")
  hh_in    <- hex[sub_h2o_in,]
  hh_match <- hex[sub_h2o_match,]
  hh_r     <- iris[sub_r,]

  #h2oTest.logInfo("print the head of the subsetted frame")
  print(hh_in)
  print(hh_match)
  head(hh_r)
  
  #h2oTest.logInfo("print the dim of the subsetted frame")
  print(dim(hh_in))
  print(dim(hh_match))
  print(dim(hh_r))

  #h2oTest.logInfo("check that the number of rows in the subsetted h2o frames match r")
  expect_true(all(dim(hh_in) == dim(hh_r)))
  expect_true(all(dim(hh_match) == dim(hh_r)))

  #h2oTest.logInfo("doing the match: hex$Sepal.Length %in% c(5.1)
  sub_h2o_in    <- hex$Sepal.Length %in% c(5.1)
  sub_h2o_match <- match(hex$Sepal.Length, c(5.1))
  sub_r         <- iris$Sepal.Length %in% c(5.1)

  #h2oTest.logInfo("Printing out the subset bit vec from the match call")
  print(sub_h2o_in)
  print(sub_h2o_match)
  head(sub_r)

  #h2oTest.logInfo("performing the subsetting: hex[sub,]")
  hh_in    <- hex[sub_h2o_in,]
  hh_match <- hex[sub_h2o_match,]
  hh_r     <- iris[sub_r,]

  #h2oTest.logInfo("print the head of the subsetted frame")
  print(hh_in)
  print(hh_match)
  head(hh_r)

  #h2oTest.logInfo("print the dim of the subsetted frame")
  print(dim(hh_in))
  print(dim(hh_match))
  print(dim(hh_r))

  #h2oTest.logInfo("check that the number of rows in the subsetted h2o frames match r")
  expect_true(all(dim(hh_in) == dim(hh_r)))
  expect_true(all(dim(hh_match) == dim(hh_r)))
    
  
}

h2oTest.doTest("test match", test.match)

