setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

check.verify.parameters.slot <- function(conn) {
    Log.info("Getting data...")
    iris.hex <- h2o.uploadFile(conn, locate("smalldata/iris/iris.csv"))
    
    Log.info("Create and and duplicate...")
    iris.km <-  h2o.kmeans(x=1:4, training_frame=iris.hex, k = 3, seed = 1234)
    parameters <- iris.km@parameters
    iris.km.cpy <- do.call("h2o.kmeans", parameters)

    wmse.org <- sort.int(iris.km@model$withinmse)
    wmse.cpy <- sort.int(iris.km.cpy@model$withinmse)

    Log.info("Verify outputs...")
    Log.info("centers")
    print(iris.km@model$centers)
    print(iris.km.cpy@model$centers)
    expect_equivalent(iris.km@model$centers, iris.km.cpy@model$centers)

    Log.info("wmse")
    print(wmse.org)
    print(wmse.cpy)
    expect_equal(wmse.org, wmse.cpy)

    testEnd()
}

doTest("Kmeans Test: Verify correct parameters passed into model", check.verify.parameters.slot)