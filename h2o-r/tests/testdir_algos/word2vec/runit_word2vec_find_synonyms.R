setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")


test.word2vec.findSynonyms <- function() {
    job_titles <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/craigslistJobTitles.csv",  col.names = c("category", "jobtitle"), col.types = c("String", "String"), header = TRUE)

    words <- h2o.tokenize(job_titles, " ")
    vec <- h2o.word2vec(training_frame = words)

    cnt <- 10
    syn <- h2o.findSynonyms(vec, "teacher", count = cnt)
    expect_equal(length(syn$score), cnt)

    # # GH-16192 h2o.findSynonyms returns empty dataset if there is no synonyms to find
    syn2 <- h2o.findSynonyms(vec, "Tteacher", count = cnt)
    expect_equal(length(syn2$score), 0)
}

doTest("Test findSynonyms function", test.word2vec.findSynonyms)
