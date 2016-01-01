setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")




test <- function() {
    fPath = tryCatch({
      h2oTest.locate("bigdata/laptop/text8.gz")
    }, warning= function(w) {
      print("File bigdata/laptop/text8.gz could not be found.  Please run ./gradlew syncBigdataLaptop (or gradlew.bat syncBigdataLaptop for Windows) to retrieve the file.")
    }, error= function(e) {
      print("File bigdata/laptop/text8.gz could not be found.  Please run ./gradlew syncBigdataLaptop (or gradlew.bat syncBigdataLaptop for Windows) to retrieve the file.")
    }, finally = {
      
    })

    text.hex = h2o.importFile(path = fPath, key = "text.hex",header = FALSE)
    w2v = h2o.word2vec(text.hex, wordModel = "CBOW", normModel = "HSM", windowSize = 4, vecSize = 100, minWordFreq = 20, sentSampleRate = 0.001, initLearningRate = 0.05, epochs = 25, negExCnt = 0)
    h2o.synonym(word2vec = w2v, target = "dog", count = 10)
    w2v = h2o.word2vec(text.hex, wordModel = "CBOW", normModel = "NegSampling", windowSize = 4, vecSize = 100, minWordFreq = 20, sentSampleRate = 0.001, initLearningRate = 0.05, epochs = 15, negExCnt = 15)
    h2o.synonym(word2vec = w2v, target = "dog", count = 10)
    w2v = h2o.word2vec(text.hex, wordModel = "SkipGram", normModel = "HSM", windowSize = 4, vecSize = 100, minWordFreq = 20, sentSampleRate = 0.001, initLearningRate = 0.05, epochs = 2, negExCnt = 0)
    h2o.synonym(word2vec = w2v, target = "dog", count = 10)
    w2v = h2o.word2vec(text.hex, wordModel = "SkipGram", normModel = "NegSampling", windowSize = 4, vecSize = 100, minWordFreq = 20, sentSampleRate = 0.001, initLearningRate = 0.025, epochs = 2, negExCnt = 5)
    h2o.synonym(word2vec = w2v, target = "dog", count = 10)

    
}

h2oTest.doTest("Run all four word2vec algos on 17M word training set.", test)
