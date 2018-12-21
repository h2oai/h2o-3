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
#' @param data An H2OFrame object with which to create the target encoding map.
#' @param x A list containing the names or indices of the variables to encode.  A target encoding map will be created for each element in the list.  Items in the list can be multiple columns.  For example, if `x = list(c("A"), c("B", "C"))`, then there will be one mapping frame for A and one mapping frame for B & C (in this case, we group by two columns).
#' @param y The name or column index of the response variable in the data. The response variable can be either numeric or binary.
#' @param fold_column (Optional) The name or column index of the fold column in the data. Defaults to NULL (no `fold_column`).
#' @return Returns an object containing the target encoding mapping for each column in `x`.
#' @seealso \code{\link{h2o.target_encode_transform}} for applying the target encoding mapping to a frame.
#' @export
h2o.target_encode_fit <- function(data, x, y, fold_column = NULL)
{

    # Handling `x` parameter
    if (is.numeric(unlist(x))) {
        x <- lapply(x, function(i) colnames(data)[i])
    }

    # Handling `y` parameter
    if (is.numeric(y)) {
        y <- colnames(data)[y]
    }

    # Handling `fold_column` parameter
    if (missing(fold_column)) {
        fold_column_name <- ""
    } else {
        if (is.numeric(fold_column)) {
            fold_column <- colnames(data)[fold_column]
        }
        fold_column_name <- fold_column
    }


    encoding_map <- .eval.driver(.newExprMap("target.encoder.fit", data, .arrayArgumentHelper(x), .quote(y), .quote(fold_column_name)))
    return(encoding_map)
}

#'
#' Transform Frame by Target Encoding Map
#' This is an API for a new target encoding implemented in JAVA.
#'
#' Applies a target encoding map to an H2OFrame object.  Computing target encoding for high cardinality
#' categorical columns can improve performance of supervised learning models.
#'
#' @param data An H2OFrame object with which to apply the target encoding map.
#' @param x A list containing the names or indices of the variables to encode. Case when item in the list is a list of multiple columns itself is not supported for now.
#' @param y The name or column index of the response variable in the data. The response variable can be either numeric or binary.
#' @param target_encode_map An object that is a result of the calling \code{\link{h2o.target_encode_fit}} function.
#' @param holdout_type The holdout type used. Must be one of: "LeaveOneOut", "KFold", "None".
#' @param fold_column (Optional) The name or column index of the fold column in the data. Defaults to NULL (no `fold_column`).
#' @param blended_avg \code{Logical}. (Optional) Whether to perform blended average. Defaults to TRUE
#' @param inflection_point  Parameter for blending. Used to calculate `lambda`. Parameter determines half of the minimal sample size for which we completely trust the estimate based on the sample in the particular level of categorical variable.
#' @param smoothing  Parameter for blending. Used to calculate `lambda`. The parameter f controls the rate of transition between the particular level's posterior probability and the prior probability. For smoothing values approaching infinity it becomes a hard threshold between the posterior and the prior probability.
#' @param noise (Optional) The amount of random noise added to the target encoding.  This helps prevent overfitting. Defaults to 0.01 * range of y.
#' @param seed (Optional) A random seed used to generate draws from the uniform distribution for random noise. Defaults to -1.
#' @param is_train_or_valid \code{Logical}
#' @return Returns an H2OFrame object containing the target encoding per record.
#' @seealso \code{\link{h2o.target_encode_fit}} for creating the target encoding map
#' @export
h2o.target_encode_transform <- function(data, x, y, target_encode_map, holdout_type,
                                        fold_column = NULL, blended_avg = TRUE, inflection_point = NULL, smoothing = NULL,
                                        noise = -1, seed = -1, is_train_or_valid)
{
    # Handling `x` parameter
    if (is.numeric(unlist(x))) {
        x <- lapply(x, function(i) colnames(data)[i])
    }

    # Handling `y` parameter
    if (is.numeric(y)) {
        y <- colnames(data)[y]
    }

    # Handling `fold_column` parameter
    if (missing(fold_column)) {
        fold_column_name <- ""
    } else {
        if (is.numeric(fold_column)) {
            fold_column <- colnames(data)[fold_column]
        }
        fold_column_name <- fold_column
    }

    # Handling `holdout_type` parameter
    if (!(holdout_type %in% c("KFold", "LeaveOneOut", "None"))){
        stop(paste0("`holdout_type` must be one of the following: KFold, LeaveOneOut, None but got "
        , holdout_type))
    }
    if (holdout_type == "LeaveOneOut") holdout_type <- "loo"

    mapKeys <- attr(target_encode_map, "map_keys")
    emKeys <- mapKeys$string

    frameKeys <- attr(target_encode_map, "frames")
    emFrameKeys <- lapply(frameKeys, function(x) x$key$name )

    transformed <- .eval.driver(.newExpr("target.encoder.transform", .arrayArgumentHelper(emKeys), .arrayArgumentHelper(emFrameKeys),
                                data, .arrayArgumentHelper(x), .quote(tolower(holdout_type)), .quote(y), .quote(fold_column_name),
                                blended_avg, inflection_point, smoothing, noise, seed, is_train_or_valid)
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
