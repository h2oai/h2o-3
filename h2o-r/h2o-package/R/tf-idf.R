#' Computes TF-IDF values for each word in given documents.
#'
#' @param frame             documents or words frame for which TF-IDF values should be computed.
#' @param document_id_col   index or name of a column containing document IDs.
#' @param text_col          index or name of a column containing documents if `preprocess = TRUE`
#'                          or words if `preprocess = FALSE`.
#' @param preprocess        whether input text data should be pre-processed. Defaults to `TRUE`.
#' @param case_sensitive    whether input data should be treated as case sensitive. Defaults to `TRUE`.
#'
#' @return  resulting frame with TF-IDF values.
#'          Row format: documentID, word, TF, IDF, TF-IDF
#' @export
h2o.tf_idf <- function(frame, document_id_col, text_col, preprocess=TRUE, case_sensitive=TRUE) {
    col_indices <- c()
    for (col in c(document_id_col, text_col))
        if(is.numeric(col) && all.equal(col, as.integer(col)))
            col_indices <- c(col_indices, col)
        else if (is.character(col))
            col_indices <- c(col_indices, match(col, colnames(frame)))
        else {
            warning(paste0("Invalid type to specify a column ('", class(col), "'). Name or index of a column is required."))
            return(NULL)
        }
    
    if(is(frame, 'H2OFrame')) {
        .newExpr('tf-idf', frame, col_indices[1], col_indices[2], preprocess, case_sensitive)
    } else {
        warning(paste0("TF-IDF cannot be computed for class ", class(frame), ". H2OFrame input is required."))
        return(NULL)
    }
}
