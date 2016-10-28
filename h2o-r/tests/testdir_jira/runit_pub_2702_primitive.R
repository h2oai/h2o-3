setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
##
# Test for JIRA PUBDEV-2702
##

test.prim <- function(){
 fr <- h2o.uploadFile(locate("smalldata/iris/iris_train.csv"), "train.hex")

 #Check primitives that have been added from PUBDEV-2702:
 expect_equal(h2o.cos(fr[,1]),cos(fr[,1]))
 expect_equal(h2o.sin(fr[,1]),sin(fr[,1]))
 expect_equal(h2o.acos(fr[,1]),acos(fr[,1]))
 expect_equal(h2o.cosh(fr[,1]),cosh(fr[,1]))
 expect_equal(h2o.tan(fr[,1]),tan(fr[,1]))
 expect_equal(h2o.tanh(fr[,1]),tanh(fr[,1]))
 expect_equal(h2o.exp(fr[,1]),exp(fr[,1]))
 expect_equal(h2o.log(fr[,1]),log(fr[,1]))
 expect_equal(h2o.sqrt(fr[,1]),sqrt(fr[,1]))
 expect_equal(h2o.abs(fr[,1]),abs(fr[,1]))
 expect_equal(h2o.ceiling(fr[,1]),ceiling(fr[,1]))
 expect_equal(h2o.floor(fr[,1]),floor(fr[,1]))
 expect_equal(h2o.sum(fr[,1]),sum(fr[,1]))
 expect_equal(h2o.prod(fr[,1]),prod(fr[,1]))
 expect_equal(h2o.all(fr[,1] < 1000),all(fr[,1 < 1000]))
 expect_equal(h2o.any(fr[,1] < 1000),any(fr[,1] < 1000))
 expect_equal(h2o.min(fr[,1]),min(fr[,1]))
 expect_equal(h2o.max(fr[,1]),max(fr[,1]))
 expect_equal(h2o.nrow(fr[,1]),nrow(fr[,1]))
 expect_equal(h2o.ncol(fr[,1]),ncol(fr[,1]))
 expect_equal(h2o.length(fr[,1]),length(fr[,1]))
 expect_equal(h2o.range(fr[,1]),range(fr[,1]))

 #Check if NA's are present with na.rm = TRUE
 x = as.h2o(c(1,2,3,4,5,6,7,8,9,10,NA,NA,NA))
 expect_equal(h2o.max(x,na.rm=TRUE),max(x,na.rm=TRUE))
 expect_equal(h2o.min(x,na.rm=TRUE),min(x,na.rm=TRUE))
 expect_equal(h2o.range(x,na.rm=TRUE),range(x,na.rm=TRUE))

}
doTest("Primitive PUBDEV-2702", test.prim)