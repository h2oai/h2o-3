setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")




test.word2vec.sg_hs <- function() {
  text8.path = locate("bigdata/laptop/text8.gz")
  epochs <- nrow(h2o.clusterStatus())

  words <- h2o.importFile(text8.path, destination_frame = "words", col.names = "word", col.types = "String")
  w2v <- h2o.word2vec(words, min_word_freq = 5, vec_size = 50, sent_sample_rate = 0.001,
                      init_learning_rate = 0.025, epochs = epochs, window_size = 4)
  synonyms <- h2o.findSynonyms(w2v, "dog", 20)
  print(synonyms)
  known.synonyms <- c("dogs", "cat", "hound", "wolf")
  matched.synonyms <- which(synonyms$synonym %in% known.synonyms)
  print(matched.synonyms)

  expect_true(length(matched.synonyms) > 0)

  vectors <- h2o.transform(w2v, words = words[1:1000,])
  expect_equal(nrow(vectors), 1000)
  expect_equal(ncol(vectors), 50)

  aggregated <- h2o.transform(w2v, words = words[1:1000,], aggregate_method = "AVERAGE")
  expect_equal(nrow(aggregated), 1)
  expect_equal(ncol(aggregated), 50)

  aggregated.local <- as.vector(as.matrix(aggregated))
  aggregated.expected <- colMeans(as.data.frame(vectors), na.rm = TRUE)
  names(aggregated.local) <- names(aggregated.expected)
  expect_equal(aggregated.local, aggregated.expected, tolerance = 1e-5, scale = 1)
}

doTest("Test word2vec (Skip Gram, Hierarchical Softmax) on text8 dataset", test.word2vec.sg_hs)
