setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.eq2.h2o.assign<-
function() {
    iris.hex <- h2o.importFile(h2oTest.locate("smalldata/iris/iris.csv"), "iris.hex")
    dim(iris.hex)
    h2oTest.logInfo("Slicing out some columns and rows from iris.hex")
    h2oTest.logInfo("Slicing out rows 20,40,60,80")
    h2oTest.logInfo("Slicing out columns, 3,4,5")
    h2oTest.logInfo("Assigning to new H2OFrame: slicedIris.hex")
    irisSlice <- iris.hex[c(20,40,60,80),c(3,4,5)]
    print(dim(irisSlice))
    irisSlice <- h2o.assign(irisSlice, "slicedIris.hex")

    h2oTest.logInfo("Check that \"slicedIris.hex\" is in the user store.")
    print(h2o.ls())
    keys <- as.vector(h2o.ls()[,1])
    expect_true(any(grepl("slicedIris.hex", keys)))
    expect_true(grepl("slicedIris.hex", h2o.getId(irisSlice)))
    h2o.removeAll()

    iris.hex <- h2o.importFile(h2oTest.locate("smalldata/iris/iris.csv"), "iris.hex")
    h2oTest.logInfo("Now slicing out rows 1:50 and columns 2:5")
    print(dim(iris.hex))
    iris.hex <- iris.hex[c(1:50), c(2,3,4,5)]
    print(dim(iris.hex))
    h2oTest.logInfo("Assign the sliced dataset to the same H2OFrame, \"iris.hex\"")
    keyList <- h2o.ls()

    h2oTest.logInfo("Check that the byte sizes of the temporary last.value and the new re-assigned iris.hex are the same")
    h2oTest.logInfo("Note that this check is OK since we cleared all keys and these should be the only two in the user store.")

    expect_that(dim(keyList)[1], equals(2))
    h2oTest.logInfo("Check that the dimension of this subsetted iris.hex is 50x4")
    print(dim(iris.hex))
    expect_that(dim(iris.hex), equals(c(50,4)))
    
}

h2oTest.doTest("Test h2o.assign(data,id)", test.eq2.h2o.assign)

