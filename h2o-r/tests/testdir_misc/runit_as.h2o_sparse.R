setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.as.h2o.sparse <- function() {
    data <- rep(0, 100)
    data[(1:10)^2] <- 1:10 * pi
    m <- matrix(data, ncol = 20, byrow = TRUE)
    m <- Matrix::Matrix(m, sparse = TRUE)
    
    Log.info("Loading a sparse matrix into H2O")
    h2o.matrix <- as.h2o(m, "sparse_matrix")

    h2o.df <- as.data.frame(h2o.matrix)
    colnames(h2o.df) <- sprintf("V%s", 1:20)

    Log.info("Expect that number of rows in as.data.frame is same as the original file")
    expect_equal(h2o.df, as.data.frame(as.matrix(m)))
}

doTest("Test as.h2o for sparse matrix", test.as.h2o.sparse)

