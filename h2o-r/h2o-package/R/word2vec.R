# This file is auto-generated by h2o-3/h2o-bindings/bin/gen_R.py
# Copyright 2016 H2O.ai;  Apache License Version 2.0 (see LICENSE for details) 
#
# -------------------------- word2vec -------------------------- #
#'
#' Trains a word2vec model on a String column of an H2O data frame
#'
#' @param training_frame Id of the training data frame.
#' @param model_id Destination id for this model; auto-generated if not specified.
#' @param min_word_freq This will discard words that appear less than <int> times Defaults to 5.
#' @param word_model The word model to use (SkipGram or CBOW) Must be one of: "SkipGram", "CBOW". Defaults to SkipGram.
#' @param norm_model Use Hierarchical Softmax Must be one of: "HSM". Defaults to HSM.
#' @param vec_size Set size of word vectors Defaults to 100.
#' @param window_size Set max skip length between words Defaults to 5.
#' @param sent_sample_rate Set threshold for occurrence of words. Those that appear with higher frequency in the training data
#'                        will be randomly down-sampled; useful range is (0, 1e-5) Defaults to 0.001.
#' @param init_learning_rate Set the starting learning rate Defaults to 0.025.
#' @param epochs Number of training iterations to run Defaults to 5.
#' @param pre_trained Id of a data frame that contains a pre-trained (external) word2vec model
#' @param max_runtime_secs Maximum allowed runtime in seconds for model training. Use 0 to disable. Defaults to 0.
#' @param export_checkpoints_dir Automatically export generated models to this directory.
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' 
#' # Import the CraigslistJobTitles dataset
#' job_titles <- h2o.importFile(
#'     "https://s3.amazonaws.com/h2o-public-test-data/smalldata/craigslistJobTitles.csv",
#'     col.names = c("category", "jobtitle"), col.types = c("String", "String"), header = TRUE
#' )
#' 
#' # Build and train the Word2Vec model
#' words <- h2o.tokenize(job_titles, " ")
#' vec <- h2o.word2vec(training_frame = words)
#' h2o.findSynonyms(vec, "teacher", count = 20)
#' }
#' @export
h2o.word2vec <- function(training_frame = NULL,
                         model_id = NULL,
                         min_word_freq = 5,
                         word_model = c("SkipGram", "CBOW"),
                         norm_model = c("HSM"),
                         vec_size = 100,
                         window_size = 5,
                         sent_sample_rate = 0.001,
                         init_learning_rate = 0.025,
                         epochs = 5,
                         pre_trained = NULL,
                         max_runtime_secs = 0,
                         export_checkpoints_dir = NULL)
{
  # Validate required training_frame first and other frame args: should be a valid key or an H2OFrame object
  # training_frame is required if pre_trained frame is not specified
  if (missing(pre_trained) && missing(training_frame)) stop("argument 'training_frame' is missing, with no default")
  training_frame <- .validate.H2OFrame(training_frame)
  pre_trained <- .validate.H2OFrame(pre_trained)

  # Build parameter list to send to model builder
  parms <- list()
  parms$training_frame <- training_frame

  if (!missing(model_id))
    parms$model_id <- model_id
  if (!missing(min_word_freq))
    parms$min_word_freq <- min_word_freq
  if (!missing(word_model))
    parms$word_model <- word_model
  if (!missing(norm_model))
    parms$norm_model <- norm_model
  if (!missing(vec_size))
    parms$vec_size <- vec_size
  if (!missing(window_size))
    parms$window_size <- window_size
  if (!missing(sent_sample_rate))
    parms$sent_sample_rate <- sent_sample_rate
  if (!missing(init_learning_rate))
    parms$init_learning_rate <- init_learning_rate
  if (!missing(epochs))
    parms$epochs <- epochs
  if (!missing(pre_trained))
    parms$pre_trained <- pre_trained
  if (!missing(max_runtime_secs))
    parms$max_runtime_secs <- max_runtime_secs
  if (!missing(export_checkpoints_dir))
    parms$export_checkpoints_dir <- export_checkpoints_dir

  # Error check and build model
  model <- .h2o.modelJob('word2vec', parms, h2oRestApiVersion=3, verbose=FALSE)
  return(model)
}
.h2o.train_segments_word2vec <- function(training_frame = NULL,
                                         min_word_freq = 5,
                                         word_model = c("SkipGram", "CBOW"),
                                         norm_model = c("HSM"),
                                         vec_size = 100,
                                         window_size = 5,
                                         sent_sample_rate = 0.001,
                                         init_learning_rate = 0.025,
                                         epochs = 5,
                                         pre_trained = NULL,
                                         max_runtime_secs = 0,
                                         export_checkpoints_dir = NULL,
                                         segment_columns = NULL,
                                         segment_models_id = NULL,
                                         parallelism = 1)
{
  # formally define variables that were excluded from function parameters
  model_id <- NULL
  verbose <- NULL
  destination_key <- NULL
  # Validate required training_frame first and other frame args: should be a valid key or an H2OFrame object
  # training_frame is required if pre_trained frame is not specified
  if (missing(pre_trained) && missing(training_frame)) stop("argument 'training_frame' is missing, with no default")
  training_frame <- .validate.H2OFrame(training_frame)
  pre_trained <- .validate.H2OFrame(pre_trained)

  # Build parameter list to send to model builder
  parms <- list()
  parms$training_frame <- training_frame

  if (!missing(min_word_freq))
    parms$min_word_freq <- min_word_freq
  if (!missing(word_model))
    parms$word_model <- word_model
  if (!missing(norm_model))
    parms$norm_model <- norm_model
  if (!missing(vec_size))
    parms$vec_size <- vec_size
  if (!missing(window_size))
    parms$window_size <- window_size
  if (!missing(sent_sample_rate))
    parms$sent_sample_rate <- sent_sample_rate
  if (!missing(init_learning_rate))
    parms$init_learning_rate <- init_learning_rate
  if (!missing(epochs))
    parms$epochs <- epochs
  if (!missing(pre_trained))
    parms$pre_trained <- pre_trained
  if (!missing(max_runtime_secs))
    parms$max_runtime_secs <- max_runtime_secs
  if (!missing(export_checkpoints_dir))
    parms$export_checkpoints_dir <- export_checkpoints_dir

  # Build segment-models specific parameters
  segment_parms <- list()
  if (!missing(segment_columns))
    segment_parms$segment_columns <- segment_columns
  if (!missing(segment_models_id))
    segment_parms$segment_models_id <- segment_models_id
  segment_parms$parallelism <- parallelism

  # Error check and build segment models
  segment_models <- .h2o.segmentModelsJob('word2vec', segment_parms, parms, h2oRestApiVersion=3)
  return(segment_models)
}
