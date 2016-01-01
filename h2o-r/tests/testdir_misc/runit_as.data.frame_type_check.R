setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
##
# 
# Check the types of the columns returned from h2o after
# calling as.data.frame.
#
# Should check both VA and FV...
#
# Check that "num"-type columns (double columns) are "num"
# Check that "int"-type columns are "int"
# Check that factor (or character) type columns are factor/character
#
##




# setupRandomSeed(1994831827)

data.frame.type.test <- function() {
   iris.FV <- as.h2o(iris)
   df.iris.FV <- iris

   #Check each column:

   expect_that(typeof(df.iris.FV[,1]), equals("double"))
   expect_that(typeof(df.iris.FV[,2]), equals("double"))
   expect_that(typeof(df.iris.FV[,3]), equals("double"))
   expect_that(typeof(df.iris.FV[,4]), equals("double"))
   expect_that(class(df.iris.FV[,5]), equals("factor"))

   #Check levels:
   expect_that(levels(df.iris.FV[,5]), equals(levels(iris[,5])))

   #Check on prostate data now...
   prostate.FV <- h2o.importFile(h2oTest.locate("smalldata/logreg/prostate.csv"))
   df.prostate.FV <- as.data.frame(prostate.FV)

   #Check each column:
   expect_that(typeof(df.prostate.FV[,1]), equals("integer"))
   expect_that(typeof(df.prostate.FV[,2]), equals("integer"))
   expect_that(typeof(df.prostate.FV[,3]), equals("integer"))
   expect_that(typeof(df.prostate.FV[,4]), equals("integer"))
   expect_that(typeof(df.prostate.FV[,5]), equals("integer"))
   expect_that(typeof(df.prostate.FV[,6]), equals("integer"))
   expect_that(typeof(df.prostate.FV[,7]), equals("double"))
   expect_that(typeof(df.prostate.FV[,8]), equals("double"))
   expect_that(typeof(df.prostate.FV[,9]), equals("integer"))

   
}

h2oTest.doTest("Type check data frame", data.frame.type.test)
