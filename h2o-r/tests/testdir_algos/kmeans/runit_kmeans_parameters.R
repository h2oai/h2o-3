setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



check.verify.parameters.slot <- function() {
    h2oTest.logInfo("Getting data...")
    iris.hex <- h2o.uploadFile( h2oTest.locate("smalldata/iris/iris.csv"))
    
    h2oTest.logInfo("Create and and duplicate...")
    iris.km <-  h2o.kmeans(x=1:4, training_frame=iris.hex, k = 3, seed = 1234, model_id = "f00b4r")
    parameters <- getParms(iris.km)
    parameters_unmunged <- getParms(iris.km)

    parameters$model_id <- NULL
    iris.km.cpy <- do.call("h2o.kmeans", parameters)

    wss.org <- sort.int(getWithinSS(iris.km))
    wss.cpy <- sort.int(getWithinSS(iris.km.cpy))

    h2oTest.logInfo("Verify outputs...")
    h2oTest.logInfo("centers")
    print(getCenters(iris.km))
    print(getCenters(iris.km.cpy))
    expect_equivalent(getCenters(iris.km), getCenters(iris.km.cpy))

    h2oTest.logInfo("Within Cluster SS")
    print(wss.org)
    print(wss.cpy)
    expect_equal(wss.org, wss.cpy)

    h2oTest.logInfo("check that we can replace the old model, and the model_id parameter is mapped from Key<Model> to character properly")

    iris.km.cpy <- do.call("h2o.kmeans", parameters_unmunged)
    print(h2o.ls()[,1])
    expect_equal(length(h2o.ls()[,1]), 5)
    
}

h2oTest.doTest("Kmeans Test: Verify correct parameters passed into model", check.verify.parameters.slot)
