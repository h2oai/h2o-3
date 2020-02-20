setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.grid.summary <- function() {
    iris_hex <- h2o.importFile(path = locate("smalldata/iris/iris.csv"))

    hyper_parameters = list(ntrees = c(10, 50), min_rows = c(10, 1000))
    grid <- expect_warning(h2o.grid(
    "gbm", x=1:4, y=5, training_frame=iris_hex, hyper_params = hyper_parameters,
    search_criteria = list(strategy = "RandomDiscrete")
    ), "Some models were not built due to a failure")
    summary.out <- paste0(capture.output(summary(grid, show_stack_traces = TRUE)), collapse="\n")
    cat(summary.out)
    expect_true(grepl("H2OModelBuilderIllegalArgumentException", summary.out, fixed = TRUE))
}

doTest("Summary of Grid object shows stack traces", test.grid.summary)
