setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

check.verify.parameters.slot <- function() {
    Log.info("Getting data...")
    iris.hex <- h2o.uploadFile( locate("smalldata/iris/iris.csv"))
    
    Log.info("Create and and duplicate...")
    iris.km <-  h2o.kmeans(x=1:4, training_frame=iris.hex, k = 3, seed = 1234, model_id = "f00b4r")
    parameters <- getParms(iris.km)
    parameters_unmunged <- getParms(iris.km)

    parameters$model_id <- NULL
    iris.km.cpy <- do.call("h2o.kmeans", parameters)

    wss.org <- sort.int(getWithinSS(iris.km))
    wss.cpy <- sort.int(getWithinSS(iris.km.cpy))

    Log.info("Verify outputs...")
    Log.info("centers")
    print(getCenters(iris.km))
    print(getCenters(iris.km.cpy))
    expect_equivalent(getCenters(iris.km), getCenters(iris.km.cpy))

    Log.info("Within Cluster SS")
    print(wss.org)
    print(wss.cpy)
    expect_equal(wss.org, wss.cpy)

    Log.info("check that we can replace the old model, and the model_id parameter is mapped from Key<Model> to character properly")

    iris.km.cpy <- do.call("h2o.kmeans", parameters_unmunged)
    print(h2o.ls()[,1])
    expect_equal(length(h2o.ls()[,1]), 5)
    testEnd()
}

doTest("Kmeans Test: Verify correct parameters passed into model", check.verify.parameters.slot)
