#
# -------------------------- Target Encoding  -------------------------- #
#
#' Create Target Encoding Map
#'
#' This is an API for a new target encoding implemented in JAVA.
#'
#' Creates a target encoding map based on group-by columns (`x`) and binary target column (`y`).
#' Computing target encoding for high cardinality categorical columns can improve performance of supervised
#' learning models.
#'
#' @param frame An H2OFrame object with which to create the target encoding map.
#' @param x List of categorical column names or indices that we want apply target encoding to. 
#           Case when item in the list is a list of multiple columns itself is not supported for now.
#' @param y The name or column index of the response variable in the frame.
#' @param fold_column (Optional) The name or column index of the fold column in the frame.
#' @return Returns an object containing the target encoding mapping for each column in `x`.
#' @seealso \code{\link{h2o.target_encode_transform}} for applying the target encoding mapping to a frame.
#' @export
h2o.target_encode_fit <- function(frame, x, y, fold_column = NULL)
{

    # Handling `x` parameter
    if (is.numeric(unlist(x))) {
        x <- lapply(x, function(i) colnames(frame)[i])
    }

    # Handling `y` parameter
    if (is.numeric(y)) {
        y <- colnames(frame)[y]
    }

    # Handling `fold_column` parameter
    if (missing(fold_column)) {
        fold_column_name <- ""
    } else {
        if (is.numeric(fold_column)) {
            fold_column <- colnames(frame)[fold_column]
        }
        fold_column_name <- fold_column
    }


    encoding_map <- .eval.driver(.newExprMap("target.encoder.fit", frame, .arrayArgumentHelper(x), .quote(y), .quote(fold_column_name)))
    return(encoding_map)
}

#'
#' Transform Frame by Target Encoding Map
#' This is an API for a new target encoding implemented in JAVA.
#'
#' Applies a target encoding map to an H2OFrame object.  Computing target encoding for high cardinality
#' categorical columns can improve performance of supervised learning models.
#'
#' @param frame An H2OFrame object with which to apply the target encoding map.
#' @param x List of categorical column names or indices that we want apply target encoding to. 
#           Case when item in the list is a list of multiple columns itself is not supported for now.
#' @param y The name or column index of the response variable in the frame.
#' @param target_encode_map An object that is a result of the calling \code{\link{h2o.target_encode_fit}} function.
#' @param holdout_type Supported options:
#'
#'                  1) "kfold" - encodings for a fold are generated based on out-of-fold data.
#'                  2) "loo" - leave one out. Current row's response value is subtracted from the pre-calculated per-level frequencies.
#'                  3) "none" - we do not holdout anything. Using whole frame for training
#' @param fold_column (Optional) The name or column index of the fold column in the frame.
#' @param blended_avg \code{Logical}. (Optional) Whether to perform blended average. Defaults to TRUE
#' @param inflection_point  Parameter for blending. Used to calculate `lambda`. Determines half of the minimal sample
#                           size for which we completely trust the estimate based on the sample in the particular level of categorical variable.
#' @param smoothing  Parameter for blending. Used to calculate `lambda`. Controls the rate of transition between 
#                   the particular level's posterior probability and the prior probability. For smoothing values approaching infinity
#                   it becomes a hard threshold between the posterior and the prior probability.
#' @param noise (Optional) The amount of random noise added to the target encoding.  This helps prevent overfitting. Defaults to 0.01 * range of y.
#' @param seed (Optional) A random seed used to generate draws from the uniform distribution for random noise. Defaults to -1.
#' @return Returns an H2OFrame object containing the target encoding per record.
#' @seealso \code{\link{h2o.target_encode_fit}} for creating the target encoding map
#' @export
h2o.target_encode_transform <- function(frame, x, y, target_encode_map, holdout_type,
                                        fold_column = NULL, blended_avg = TRUE, inflection_point = NULL, smoothing = NULL,
                                        noise = -1, seed = -1)
{
    # Handling `x` parameter
    if (is.numeric(unlist(x))) {
        x <- lapply(x, function(i) colnames(frame)[i])
    }

    # Handling `y` parameter
    if (is.numeric(y)) {
        y <- colnames(frame)[y]
    }

    # Handling `fold_column` parameter
    if (missing(fold_column)) {
        fold_column_name <- ""
    } else {
        if (is.numeric(fold_column)) {
            fold_column <- colnames(frame)[fold_column]
        }
        fold_column_name <- fold_column
    }

    # Handling `holdout_type` parameter
    if (!(holdout_type %in% c("kfold", "loo", "none"))){
        stop(paste0("`holdout_type` must be one of the following: kfold, loo, none but got "
        , holdout_type))
    }

    mapKeys <- attr(target_encode_map, "map_keys")
    emKeys <- mapKeys$string

    frameKeys <- attr(target_encode_map, "frames")
    emFrameKeys <- lapply(frameKeys, function(x) x$key$name )

    transformed <- .eval.driver(.newExpr("target.encoder.transform", .arrayArgumentHelper(emKeys), .arrayArgumentHelper(emFrameKeys),
                                frame, .arrayArgumentHelper(x), .quote(tolower(holdout_type)), .quote(y), .quote(fold_column_name),
                                blended_avg, inflection_point, smoothing, noise, seed)
    )
    return(transformed)
}

.newExprMap <- function(op,...) .newExprListMap(op,list(...))

.newExprListMap <- function(op,li) {
    node <- structure(new.env(parent = emptyenv()))
    .set(node,"op",op)
    .set(node,"eval",li)
    node
}

.arrayArgumentHelper <- function(arr) {
    if(length(arr) == 1) {
        arr <- .quote(arr)
    } else {
        arr <- unlist(arr)
    }
    return(arr)
}
