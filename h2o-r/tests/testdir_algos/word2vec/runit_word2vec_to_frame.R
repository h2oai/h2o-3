setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")




test.word2vec.toFrame <- function() {
    pretrained.frame <- as.h2o(data.frame(
        Word = c("a", "b"), V1 = c(0, 1), V2 = c(1, 0), V3 = c(0.2, 0.8),
        stringsAsFactors = FALSE
    ))

    # convert to an H2O word2vec model
    pretrained.w2v <- h2o.word2vec(pre_trained = pretrained.frame, vec_size = 3)

    # conver back to a Frame
    result <- h2o.toFrame(pretrained.w2v)
    result.local <- as.data.frame(result)

    expect_equal(result, expected = pretrained.frame)
}

doTest("Test converting a word2vec model to a frame", test.word2vec.toFrame)
