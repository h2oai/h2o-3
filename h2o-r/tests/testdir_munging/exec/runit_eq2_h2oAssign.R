


test.eq2.h2o.assign<-
function() {
    iris.hex <- h2o.importFile(locate("smalldata/iris/iris.csv"), "iris.hex")
    dim(iris.hex)
    Log.info("Slicing out some columns and rows from iris.hex")
    Log.info("Slicing out rows 20,40,60,80")
    Log.info("Slicing out columns, 3,4,5")
    Log.info("Assigning to new Frame: slicedIris.hex")
    irisSlice <- iris.hex[c(20,40,60,80),c(3,4,5)]
    print(dim(irisSlice))
    irisSlice <- h2o.assign(irisSlice, "slicedIris.hex")

    Log.info("Check that \"slicedIris.hex\" is in the user store.")
    print(h2o.ls())
    keys <- as.vector(h2o.ls()[,1])
    expect_true(any(grepl("slicedIris.hex", keys)))
    expect_true(grepl("slicedIris.hex", h2o.getId(irisSlice)))
    h2o.removeAll()

    iris.hex <- h2o.importFile(locate("smalldata/iris/iris.csv"), "iris.hex")
    Log.info("Now slicing out rows 1:50 and columns 2:5")
    print(dim(iris.hex))
    iris.hex <- iris.hex[c(1:50), c(2,3,4,5)]
    print(dim(iris.hex))
    Log.info("Assign the sliced dataset to the same Frame, \"iris.hex\"")
    keyList <- h2o.ls()

    Log.info("Check that the byte sizes of the temporary last.value and the new re-assigned iris.hex are the same")
    Log.info("Note that this check is OK since we cleared all keys and these should be the only two in the user store.")

    expect_that(dim(keyList)[1], equals(2))
    Log.info("Check that the dimension of this subsetted iris.hex is 50x4")
    print(dim(iris.hex))
    expect_that(dim(iris.hex), equals(c(50,4)))
    
}

doTest("Test h2o.assign(data,id)", test.eq2.h2o.assign)

