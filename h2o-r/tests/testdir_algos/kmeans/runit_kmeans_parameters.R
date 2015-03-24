setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

check.verify.parameters.slot <- function(conn) {
    Log.info("Getting data...")
    iris.hex <- h2o.uploadFile(conn, locate("smalldata/iris/iris.csv"))
    
    Log.info("Create and and duplicate...")
    iris.km <-  h2o.kmeans(x=1:4, training_frame=iris.hex, k = 3, seed = 1234, destination_key = "f00b4r")
    parameters <- iris.km@parameters
    parameters_unmunged <- iris.km@parameters

    parameters$destination_key <- NULL
    iris.km.cpy <- do.call("h2o.kmeans", parameters)

    wmse.org <- sort.int(iris.km@model$within_mse)
    wmse.cpy <- sort.int(iris.km.cpy@model$within_mse)

    Log.info("Verify outputs...")
    Log.info("centers")
    print(iris.km@model$centers)
    print(iris.km.cpy@model$centers)
    expect_equivalent(iris.km@model$centers, iris.km.cpy@model$centers)

    Log.info("wmse")
    print(wmse.org)
    print(wmse.cpy)
    expect_equal(wmse.org, wmse.cpy)

    Log.info("check that we can replace the old model, and the destination_key parameter is mapped from Key<Model> to character properly")

    iris.km.cpy <- do.call("h2o.kmeans", parameters_unmunged)
    expect_equal(length(h2o.ls()[,1]), 3)
    testEnd()
}

doTest("Kmeans Test: Verify correct parameters passed into model", check.verify.parameters.slot)
