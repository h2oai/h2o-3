setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.as.h2o.sparse <- function() {
    # 1. small matrix
    data <- rep(0, 100)
    data[(1:10)^2] <- 1:10 * pi
    m.dense.small <- matrix(data, ncol = 20, byrow = TRUE)
    m.small <- Matrix::Matrix(m.dense.small, sparse = TRUE)

    Log.info("Loading a sparse matrix into H2O")
    h2o.small <- as.h2o(m.small, "small_matrix")

    h2o.small.df <- as.data.frame(h2o.small)
    colnames(h2o.small.df) <- sprintf("V%s", 1:20)

    Log.info("Check that data.frame in H2O has the same content as the local matrix")
    expect_equal(h2o.small.df, as.data.frame(as.matrix(m.small)))

    # 2. large sparse matrix (46341 x 46343): too big for as.matrix in R (Cholmod error 'problem too large')
    i <- c(1, 3:8, 46341)
    j <- c(2, 9, 6:10, 46343)
    x <- pi * (1:8)
    m.large <- Matrix::sparseMatrix(i, j, x = x)
    # When we have enough memory R 4.4 can create the matrix without failing
    # expect_error(as.matrix(m.large), "Cholmod error 'problem too large'|vector memory limit of .* reached")

    Log.info("Loading a large sparse matrix into H2O")
    h2o.large <- as.h2o(m.large, "large_matrix")

    Log.info("Check that the large matrix has correct size in H2O")
    expect_equal(c(46341, 46343), dim(h2o.large))

    # 3. show index numbers are correctly formatted (scientific notation, eg. 1e5 is written as 100000)
    m.simple <- Matrix::sparseMatrix(1, 1e5 + 1, x = pi) # +1 because indices are 0-based in SVMLight
    h2o.simple <- as.h2o(m.simple, "simple_matrix")
    expect_equal(c(1, 100001), dim(h2o.simple))
}

doTest("Test as.h2o for sparse matrix", test.as.h2o.sparse)

