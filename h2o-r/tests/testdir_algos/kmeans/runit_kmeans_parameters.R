setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

check.verify.parameters.slot <- function(conn) {
    Log.info("Getting data...")
    iris.hex <- h2o.uploadFile(conn, locate("smalldata/iris/iris.csv"))
    
    Log.info("Create and and duplicate...")
    iris.km <-  h2o.kmeans(x=1:4, training_frame=iris.hex, k = 3, seed = 1234, model_id = "f00b4r")
    parameters <- getParms(iris.km)
    parameters_unmunged <- getParms(iris.km)

    parameters$model_id <- NULL
    iris.km.cpy <- do.call("h2o.kmeans", parameters)

    wmse.org <- sort.int(getWithinMSE(iris.km))
    wmse.cpy <- sort.int(getWithinMSE(iris.km.cpy))

    Log.info("Verify outputs...")
    Log.info("centers")
    print(getCenters(iris.km))
    print(getCenters(iris.km.cpy))
    expect_equivalent(getCenters(iris.km), getCenters(iris.km.cpy))

    Log.info("wmse")
    print(wmse.org)
    print(wmse.cpy)
    expect_equal(wmse.org, wmse.cpy)

    Log.info("check that we can replace the old model, and the model_id parameter is mapped from Key<Model> to character properly")

    iris.km.cpy <- do.call("h2o.kmeans", parameters_unmunged)
    print(h2o.ls()[,1])
    expect_equal(length(h2o.ls()[,1]), 5)
    testEnd()
}

doTest("Kmeans Test: Verify correct parameters passed into model", check.verify.parameters.slot)
