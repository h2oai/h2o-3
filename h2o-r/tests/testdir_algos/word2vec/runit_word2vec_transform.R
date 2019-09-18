setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.word2vec.transform <- function() {
    pretrained.frame <- as.h2o(data.frame(
        C1 = c("a", "b"), C2 = c(0, 1), C3 = c(1, 0), C4 = c(0.2, 0.8),
        stringsAsFactors = FALSE
    ))

    pretrained.w2v <- h2o.word2vec(pre_trained = pretrained.frame, vec_size = 3)

    words <- as.character(as.h2o(c("b", "a", "c", NA, "a")))
    vecs <- h2o.transform(pretrained.w2v, words = words)   
    vecs_specialized <- h2o.transform_word2vec(pretrained.w2v, words = words)
    

    expect_equal(vecs, vecs_specialized)
}

doTest("Newly introduced general random method should produce the same output as method specialized for W2Vec", test.word2vec.transform)
