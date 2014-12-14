#'
#' Word2Vec
#'
#' Create a word2vec object.
#'
#' Two cases below: 1. Negative Sampling; 2. Hierarchical Softmax
#'
#' * Constructor used for specifying the number of negative sampling cases.
#'  @param wordModel - SkipGram or CBOW
#'  @param normModel - Hierarchical softmax or Negative sampling
#'  @param numNegEx - Number of negative samples used per word
#'  @param vocabKey - Key pointing to frame of [Word, Cnt] vectors
#'  @param vecSize - Size of word vectors
#'  @param winSize - Size of word window
#'  @param sentSampleRate - Sampling rate in sentences to generate new n-grams
#'  @param initLearningRate - Starting alpha value.  This tempers the effect of progressive information as learning progresses.
#'  @param epochs - Number of iterations data is run through.
#'
#' * Constructor used for hierarchical softmax cases.
#'  @param wordModel - SkipGram or CBOW
#'  @param vocabKey - Key pointing to frame of [Word, Cnt] vectors
#'  @param vecSize - Size of word vectors
#'  @param winSize - Size of word window
#'  @param sentSampleRate - Sampling rate in sentences to generate new n-grams
#'  @param initLearningRate - Starting alpha value.  This tempers the effect of progressive information as learning progresses.
#'  @param epochs - Number of iterations data is run through.
h2o.word2vec<-
function(trainingFrame, minWordFreq, wordModel, normModel, negExCnt = NULL,
         vecSize, windowSize, sentSampleRate, initLearningRate, epochs) {

  # param checking
  if (!(trainingFrame %i% "h2o.frame")) stop("`data` must be an h2o.frame")
  if (missing(wordModel) || !(wordModel %in% c("SkipGram", "CBOW"))) stop("`wordModel` must be one of \"SkipGram\" or \"CBOW\"")
  if (missing(normModel) || !(normModel %in% c("HSM", "NegSampling"))) stop("`normModel` must be onf of \"HSM\" or \"NegSampling\"")
  if (!is.null(negExCnt)) {
    if (negExCnt < 0) stop("`negExCnt` must be >= 0")
    if (negExCnt != 0 && normModel == "HSM") stop("Both hierarchical softmax and negative samples != 0 is not allowed for Word2Vec.  Expected value = 0, received" %p% negExCnt)
  }
  if (missing(vecSize) || !is.numeric(vecSize)) stop("`vecSize` must be numeric")
  if (missing(windowSize) || !is.numeric(windowSize)) stop("`windowSize` must be numeric")
  if (missing(sentSampleRate) || !is.numeric(sentSampleRate)) stop("`sentSampleRate` must be numeric")
  if (missing(initLearningRate) || !is.numeric(initLearningRate)) stop("`initLearningRate` must be numeric")
  if (missing(epochs) || !is.numeric(epochs)) stop("`epochs` must be numeric")
  if (!(trainingFrame %i% "h2o.frame")) invisible(nrow(trainingFrame))  # try to force the eval of the frame
  if (!(trainingFrame %i% "h2o.frame")) stop("Could not evaluate `trainingFrame` as an H2O data frame.")

  params <- list(training_frame = trainingFrame@key,
                 wordModel = wordModel,
                 normModel = normModel,
                 minWordFreq = minWordFreq,
                 negSampleCnt = negExCnt,
                 vecSize = vecSize,
                 windowSize = windowSize,
                 sentSampleRate = sentSampleRate,
                 initLearningRate = initLearningRate,
                 epochs = epochs)

  res <- .h2o.__remoteSend(trainingFrame@h2o, .h2o.__W2V, .params = params)
  .h2o.__waitOnJob(trainingFrame@h2o, res$job)
  dest_key <- .h2o.__remoteSend(trainingFrame@h2o, paste(.h2o.__JOBS, "/", res$job, sep = ""))$jobs[[1]]$dest$name
  w2vmodel <- .h2o.__remoteSend(trainingFrame@h2o, .h2o.__INSPECT, key = dest_key)
  new("H2OW2V", h2o = trainingFrame@h2o, key = dest_key, train.data=trainingFrame)
}

#'
#' Find Synonyms Using an H2OW2V object
#'
#'  @param word2vec: An H2OW2V model.
#'  @param target: A single word, or a vector of words.
#'  @param count: The top `count` synonyms will be returned.
#'
h2o.synonym<-
function(word2vec, target, count) {
  if (!(word2vec %i% "H2OW2V")) stop("`word2vec` must be an H2O word2vec object. See h2o.word2vec")
  if (missing(target)) stop("`target` must be specified")
  if (!is.character(target)) stop("`target` must be character")
  if (missing(count)) stop("`count` must be specified")
  if (!is.numeric(count)) stop("`count` must be numeric")

  params <- list(key = word2vec@key, target=target, cnt=count)
  if (length(target) == 1) {
    res <- .h2o.__remoteSend(word2vec@h2o, .h2o.__SYNONYMS, .params = params)
    fr <- data.frame(synonyms = res$synonyms, cosine.similarity = res$cos_sim)
    fr <- fr[with(fr, order(-cosine.similarity)),]
    return(fr)
  } else {
    stop("unimplemented")
#    vecs <- lapply(target, h2o.transform, word2vec)
#    vec <- colSums(as.data.frame(vecs))
#    params$vec <- vec
#    res <- .h2o.__remoteSend(data@h2o, .h2o.__SYNONYMS, params)
#    return(h2o.getFrame(res$key))
  }
}

#'
#' Transform A Word to A Vec Using Word2Vec
#'
#' Use a pre-existing word2vec object to transform a target word
#' into a numeric vector.
#setMethod("h2o.transform", "H2OW2V", function(word2vec, target) {
#  if (!(word2vec %i% "H2OW2V")) stop("`word2vec` must be an H2O word2vec object. See h2o.word2vecs")
#  if (missing(target)) stop("`target` must be specified")
#  if (!is.character(target)) stop("`target` must be character")
#  if (length(target) > 1) stop("`target` must be a single word")
#
#  params <- params <- c(data = word2vec@word2vec@key, target = target, word2vec@params)
#  res <- .h2o.__remoteSend(data@h2o, .h2o.__TRANSFORM, params)
#  res$vec
#})
