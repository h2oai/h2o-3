##
## Word2Vec
##
## Create a word2vec object.
##
## Two cases below: 1. Negative Sampling; 2. Hierarchical Softmax
##
## * Constructor used for specifying the number of negative sampling cases.
##  @param wordModel - SkipGram or CBOW
##  @param normModel - Hierarchical softmax or Negative sampling
##  @param numNegEx - Number of negative samples used per word
##  @param vocabKey - Key pointing to frame of [Word, Cnt] vectors
##  @param vecSize - Size of word vectors
##  @param winSize - Size of word window
##  @param sentSampleRate - Sampling rate in sentences to generate new n-grams
##  @param initLearningRate - Starting alpha value.  This tempers the effect of progressive information as learning progresses.
##  @param epochs - Number of iterations data is run through.
##
## * Constructor used for hierarchical softmax cases.
##  @param wordModel - SkipGram or CBOW
##  @param vocabKey - Key pointing to frame of [Word, Cnt] vectors
##  @param vecSize - Size of word vectors
##  @param winSize - Size of word window
##  @param sentSampleRate - Sampling rate in sentences to generate new n-grams
##  @param initLearningRate - Starting alpha value.  This tempers the effect of progressive information as learning progresses.
##  @param epochs - Number of iterations data is run through.
h2o.word2vec <- function(trainingFrame, minWordFreq, wordModel, normModel, negExCnt = NULL,
         vecSize, windowSize, sentSampleRate, initLearningRate, epochs) {

  # param checking
  if (!is.Frame(trainingFrame)) stop("`data` must be an H2O Frame object")
  if (missing(wordModel) || !(wordModel %in% c("SkipGram", "CBOW"))) stop("`wordModel` must be one of \"SkipGram\" or \"CBOW\"")
  if (missing(normModel) || !(normModel %in% c("HSM", "NegSampling"))) stop("`normModel` must be onf of \"HSM\" or \"NegSampling\"")
  if (!is.null(negExCnt)) {
    if (negExCnt < 0L) stop("`negExCnt` must be >= 0")
    if (negExCnt != 0L && normModel == "HSM") stop("Both hierarchical softmax and negative samples != 0 is not allowed for Word2Vec.  Expected value = 0, received ", negExCnt)
  }
  if (missing(vecSize) || !is.numeric(vecSize)) stop("`vecSize` must be numeric")
  if (missing(windowSize) || !is.numeric(windowSize)) stop("`windowSize` must be numeric")
  if (missing(sentSampleRate) || !is.numeric(sentSampleRate)) stop("`sentSampleRate` must be numeric")
  if (missing(initLearningRate) || !is.numeric(initLearningRate)) stop("`initLearningRate` must be numeric")
  if (missing(epochs) || !is.numeric(epochs)) stop("`epochs` must be numeric")
  if (!is.Frame(trainingFrame)) invisible(nrow(trainingFrame))  # try to force the eval of the frame
  if (!is.Frame(trainingFrame)) stop("Could not evaluate `trainingFrame` as an H2O Frame object")

  params <- list(training_frame = h2o.getId(trainingFrame),
                 wordModel = wordModel,
                 normModel = normModel,
                 minWordFreq = minWordFreq,
                 negSampleCnt = negExCnt,
                 vecSize = vecSize,
                 windowSize = windowSize,
                 sentSampleRate = sentSampleRate,
                 initLearningRate = initLearningRate,
                 epochs = epochs)

  res <- .h2o.__remoteSend(.h2o.__W2V, .params = params)
  .h2o.__waitOnJob(res$job)
  dest_key <- .h2o.__remoteSend(paste0(.h2o.__JOBS, "/", res$job))$jobs[[1L]]$dest$name
  w2vmodel <- h2o.getModel(dest_key)
  new("H2OW2V", h2o = trainingFrame@conn, key = dest_key, train.data=trainingFrame)
}

##
## Find Synonyms Using an H2OW2V object
##
##  @param word2vec: An H2OW2V model.
##  @param target: A single word, or a vector of words.
##  @param count: The top `count` synonyms will be returned.
##
h2o.synonym<-
function(word2vec, target, count) {
  if (!is(word2vec, "H2OW2V")) stop("`word2vec` must be an H2O word2vec object. See h2o.word2vec")
  if (missing(target)) stop("`target` must be specified")
  if (!is.character(target)) stop("`target` must be character")
  if (missing(count)) stop("`count` must be specified")
  if (!is.numeric(count)) stop("`count` must be numeric")

  params <- list(key = h2o.getId(word2vec), target=target, cnt=count)
  if (length(target) == 1L) {
    res <- .h2o.__remoteSend(word2vec@conn, .h2o.__SYNONYMS, .params = params)
    fr <- data.frame(synonyms = res$synonyms, cosine.similarity = res$cos_sim)
    fr[with(fr, order(-cosine.similarity)),]
  } else {
    .NotYetImplemented()
#    vecs <- lapply(target, h2o.transform, word2vec)
#    vec <- colSums(as.data.frame(vecs))
#    params$vec <- vec
#    res <- .h2o.__remoteSend(data@conn, .h2o.__SYNONYMS, params)
#    return(h2o.getFrame(res$key))
  }
}

#'
#' Transform A Word to A Vec Using Word2Vec
#'
#' Use a pre-existing word2vec object to transform a target word
#' into a numeric vector.
#setMethod("h2o.transform", "H2OW2V", function(word2vec, target) {
#  if (!is(word2vec, "H2OW2V")) stop("`word2vec` must be an H2O word2vec object. See h2o.word2vecs")
#  if (missing(target)) stop("`target` must be specified")
#  if (!is.character(target)) stop("`target` must be character")
#  if (length(target) > 1) stop("`target` must be a single word")
#
#  params <- params <- c(data = word2vec@word2vec:"id", target = target, word2vec@params)
#  res <- .h2o.__remoteSend(data@conn, .h2o.__TRANSFORM, params)
#  res$vec
#})
