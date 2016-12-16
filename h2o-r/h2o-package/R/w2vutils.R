#'
#' Find synonyms using a word2vec model.
#'
#' @param word2vec A word2vec model.
#' @param target Single word to find synonyms for.
#' @param count The top `count` synonyms will be returned.
#' @export
h2o.findSynonyms <- function(word2vec, target, count = 20) {
    if (!is(word2vec, "H2OModel")) stop("`word2vec` must be a word2vec model")
    if (missing(target)) stop("`target` must be specified")
    if (!is.character(target)) stop("`target` must be character")
    if (!is.numeric(count)) stop("`count` must be numeric")

    res <- .h2o.__remoteSend(method="GET", "Word2VecSynonyms", model = word2vec@model_id,
                             target = target, count = count)
    fr <- data.frame(synonyms = res$words, scores = res$scores)
    fr[with(fr, order(scores, decreasing = TRUE)),]
}