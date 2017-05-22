setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.predict_json <- function() {
    iris.hex <- h2o.uploadFile(locate("smalldata/iris/iris_wheader.csv"), "iris.hex")
    hh <- h2o.gbm(x=c(1,2,3,4),y=5,training_frame=iris.hex)
    file <- h2o.download_mojo(hh, get_genmodel_jar=TRUE) #check mojo
    print(file)

    res <- h2o.predict_json(file, '{}')
    print(res)
    expect_equal(res$labelIndex, 1)
    expect_equal(res$label, "Iris-versicolor")
    expect_equal(res$classProbabilities, c(0.009345740, 0.981322293, 0.009331966))

    res <- h2o.predict_json(file, '{"sepal_len": 5.1, "sepal_wid": 3.5, "petal_len": 1.4, "petal_wid": 0.2}')
    print(res)
    expect_equal(res$labelIndex, 0)
    expect_equal(res$label, "Iris-setosa")
    expect_equal(res$classProbabilities, c(0.9989300037, 0.0005656894, 0.0005043069))

    res <- h2o.predict_json(file, '{"sepal_len": 6.3, "sepal_wid": 3.3, "petal_len": 6.0, "petal_wid": 2.5}')
    print(res)
    expect_equal(res$labelIndex, 2)
    expect_equal(res$label, "Iris-virginica")
    expect_equal(res$classProbabilities, c(0.001489174, 0.003726691, 0.994784135))

    file.remove(file)
}

doTest("Test predict_json", test.predict_json)
