setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")




test.word2vec.pretrained <- function() {
    pretrained.frame <- as.h2o(data.frame(
        C1 = c("a", "b"), C2 = c(0, 1), C3 = c(1, 0), C4 = c(0.2, 0.8),
        stringsAsFactors = FALSE
    ))

    # convert to an H2O word2vec model
    pretrained.w2v <- h2o.word2vec(pre_trained = pretrained.frame, vec_size = 3)

    words <- as.character(as.h2o(c("b", "a", "c", NA, "a")))
    # use a method defined for w2v model
    vecs <- h2o.transform(pretrained.w2v, words = words)

    expect_equal(as.data.frame(vecs), expected = data.frame(
        C1 = c(1, 0, NA, NA, 0), C2 = c(0, 1, NA, NA, 1), C3 = c(0.8, 0.2, NA, NA, 0.2)
    ))
}

doTest("Test converting pre-trained word2vec model from a frame to a word2vec model", test.word2vec.pretrained)
