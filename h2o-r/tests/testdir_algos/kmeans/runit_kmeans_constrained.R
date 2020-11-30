setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")


test.km.constrained <- function() {
    # import the iris dataset:
    # this dataset is used to classify the type of iris plant
    # the original dataset can be found at https://archive.ics.uci.edu/ml/datasets/Iris
    iris <-h2o.importFile(locate("smalldata/iris/iris_wheader.csv"))

    # convert response column to a factor
    iris['class'] <-as.factor(iris['class'])

    # set the predictor names 
    predictors <-colnames(iris)[-length(iris)]

    # try using the `cluster_size_constraints` parameter:
    iris_kmeans <- h2o.kmeans(x = predictors, k=3, standardize=T, cluster_size_constraints=c(2, 5, 8),
    training_frame=iris, score_each_iteration=T, seed=1234)

    # print the model summary to see the number of datapoints are in each cluster
    summary(iris_kmeans)
}

doTest("KMeans Test: Constrained Kmeans parameter test", test.km.constrained)
