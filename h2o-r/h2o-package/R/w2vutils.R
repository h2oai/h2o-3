#'
#' Find synonyms using a word2vec model.
#'
#' @param word2vec A word2vec model.
#' @param word A single word to find synonyms for.
#' @param count The top `count` synonyms will be returned.
#' @export
h2o.findSynonyms <- function(word2vec, word, count = 20) {
    if (!is(word2vec, "H2OModel")) stop("`word2vec` must be a word2vec model")
    if (missing(word)) stop("`word` must be specified")
    if (!is.character(word)) stop("`word` must be character")
    if (!is.numeric(count)) stop("`count` must be numeric")

    res <- .h2o.__remoteSend(method="GET", "Word2VecSynonyms", model = word2vec@model_id,
                             word = word, count = count)
    fr <- data.frame(synonym = res$synonyms, score = res$scores)
    fr[with(fr, order(score, decreasing = TRUE)),]
}

#'
#' Transform words to vectors using a word2vec model.
#'
#' @param word2vec A word2vec model.
#' @param words An H2OFrame made of a single column containing source words.
#' @export
h2o.transform <- function(word2vec, words) {
    if (!is(word2vec, "H2OModel")) stop("`word2vec` must be a word2vec model")
    if (missing(words)) stop("`words` must be specified")
    if (!is.H2OFrame(words)) stop("`words` must be an H2OFrame")
    if (ncol(words) != 1) stop("`words` frame must contain a single string column")

    res <- .h2o.__remoteSend(method="GET", "Word2VecTransform", model = word2vec@model_id,
                             words_frame = h2o.getId(words))
    key <- res$vectors_frame$name
    h2o.getFrame(key)
}
