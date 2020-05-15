#' Computes TF-IDF values for each word in given documents.
#'
#' @param frame      documents or words frame for which TF-IDF values should be computed.
#'                   (Default) Row format when `preprocess = True`: documentID, documentString
#'                   Row format when `preprocess = False`: documentID, word
#' @param preprocess whether input frame should be pre-processed. Defaults to `TRUE`.
#'
#' @return  resulting frame with TF-IDF values.
#'          Row format: documentID, word, TF, IDF, TF-IDF
h2o.tf_idf <- function(frame, preprocess=TRUE) {
    if( is(frame, 'H2OFrame') ) {
        .newExpr('tf-idf', frame, preprocess)
    } else {
        warning(paste0("TF-IDF cannot be computed for class ", class(frame), ". H2OFrame input is required."))
        return(NULL)
    }
}
