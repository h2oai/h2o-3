#' Computes TF-IDF values for each word in given documents.
#'
#' @param frame documents frame for which TF-IDF values should be computed.
#'              Row format: documentID, documentString
#'
#' @return  resulting frame with TF-IDF values.
#'          Row format: documentID, word, TF, IDF, TF-IDF
h2o.tf_idf <- function(frame) {
    if( is(frame, 'H2OFrame') ) {
        .newExpr('tf-idf', frame)
    } else {
        warning(paste0("TF-IDF cannot be computed for class ", class(frame), ". H2OFrame input is required."))
        return(NULL)
    }
}
