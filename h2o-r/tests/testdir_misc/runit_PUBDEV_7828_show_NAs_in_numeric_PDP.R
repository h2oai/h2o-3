setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test <- function() {
    # 1D PDP
    prostate_hex = h2o.uploadFile(locate( "smalldata/prostate/prostate.csv"), "prostate.hex")
    prostate_hex[, "CAPSULE"] = as.factor(prostate_hex[, "CAPSULE"])
    seed=1234
    prostate_drf = h2o.randomForest(x = c("AGE", "RACE"), y = "CAPSULE", training_frame = prostate_hex, ntrees = 25, seed = seed)
    
    h2o.partialPlot(object = prostate_drf, data = prostate_hex, cols = "RACE", plot = TRUE, include_na = TRUE, plot_stddev = TRUE)
    h2o.partialPlot(object = prostate_drf, data = prostate_hex, cols = "RACE", plot = TRUE, include_na = FALSE, plot_stddev = TRUE)
    h2o.partialPlot(object = prostate_drf, data = prostate_hex, cols = "RACE", plot = TRUE, include_na = FALSE, plot_stddev = FALSE)
    h2o.partialPlot(object = prostate_drf, data = prostate_hex, cols = "RACE", plot = TRUE, include_na = TRUE, plot_stddev = FALSE)

    # 1D multiple cols
    h2o.partialPlot(object = prostate_drf, data = prostate_hex, cols = c("AGE", "RACE", "DCAPS"), plot = TRUE, include_na = TRUE, plot_stddev = TRUE)
    h2o.partialPlot(object = prostate_drf, data = prostate_hex, cols = c("AGE", "RACE", "DCAPS"), plot = TRUE,  include_na = FALSE, plot_stddev = TRUE)
    h2o.partialPlot(object = prostate_drf, data = prostate_hex, cols = c("AGE", "RACE", "DCAPS"), plot = TRUE,  include_na = FALSE, plot_stddev = FALSE)
    h2o.partialPlot(object = prostate_drf, data = prostate_hex, cols = c("AGE", "RACE", "DCAPS"), plot = TRUE,  include_na = TRUE, plot_stddev = FALSE)

    # 1D multiple cols && targets
    iris[,'random'] <- as.factor(as.data.frame(unlist(sample(x = 1:4, size = length(iris[[1]]), replace=TRUE)))[[1]])
    iris_hex <- as.h2o(iris)
    iris_gbm <- h2o.gbm(x = c(1:4,6), y = 5, training_frame = iris_hex)

    # one column  
    h2o.partialPlot(object = iris_gbm, data = iris_hex, cols = "Petal.Length", targets = c("setosa"), plot = TRUE, include_na = TRUE, plot_stddev = TRUE )
    h2o.partialPlot(object = iris_gbm, data = iris_hex, cols = "Petal.Length", targets = c("setosa", "virginica", "versicolor"), plot = TRUE, include_na = TRUE, plot_stddev = TRUE)
    
    # two colums  
    h2o.partialPlot(object = iris_gbm, data = iris_hex, cols=c("Petal.Length", "Sepal.Length"), targets=c("setosa"))
    h2o.partialPlot(object = iris_gbm, data = iris_hex, cols=c("Petal.Length", "Sepal.Length"), targets=c("setosa"), include_na = FALSE, plot_stddev = TRUE)

}

doTest("Test 2D Partial Dependence Plots in H2O: ", test)

