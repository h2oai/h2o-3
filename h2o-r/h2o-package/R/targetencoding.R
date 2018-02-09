#'
#' Target Encoding Map
#' 
#' Creates a target encoding map based on a group-by column and a target column (numeric or binary).
#' 
#' Calculates the metrics of the target column per group.  Used to create the target encoding frame.
#' 
#' @param groupby_frame An H2OFrame object with categorical columns in which to group by.
#' @param y An H2OFrame object with a single numeric or binary column.
#' @param noise_level (Optional) The amount of random noise added to the target encoding.  This helps prevent overfitting. Defaults to 0.01 * range of y.
#' @param seed (Optional) A random seed used to generate draws from the uniform distribution for random noise. Defaults to -1.
#' @return Returns an H2OFrame object containing the leave one out target encoding.
#' @seealso \code{\link{h2o.targetencoding_frame}} for creating the target encoding frame from the mapping.
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#' 
#' # Get Target Encoding Map on bank-additional-full data with numeric `y`
#' data.hex = h2o.importFile(
#' path = "https://s3.amazonaws.com/h2o-public-test-data/smalldata/demos/bank-additional-full.csv",
#' destination_frame = "data.hex")
#' h2o.targetencoding_map(data.hex[c("job", "marital")], data.hex$age)
#' 
#' # Get Target Encoding Map on bank-additional-full data with binary `y`
#' h2o.targetencoding_map(data.hex[c("job", "marital")], data.hex$y)
#' 
#' }
#' @export

h2o.targetencoding_map <- function(groupby_frame, y){
  
  if (missing(groupby_frame)) 
    stop("argument 'groupby_frame' is missing, with no default")
  if (missing(y)) 
    stop("argument 'y' is missing, with no default")
  
  if (!is.h2o(groupby_frame)) 
    stop("argument `groupby_frame` must be a valid H2OFrame")
  if (!is.h2o(y)) 
    stop("argument `y` ust be a valid H2OFrame")
  
  if (nrow(groupby_frame) != nrow(y))
    stop("`groupby_frame` and `y` must have the same number of rows")
  
  if (ncol(y) > 1)
    stop("`y` must have one column")
  if (is.numeric(groupby_frame) || length(groupby_frame) == 0L) 
    stop("`groupby_frame` must be a frame of categorical columns")
  
  if (is.factor(y)) {
    y_levels <- h2o.levels(y)
    if (length(y_levels) == 2)
      y <- h2o.ifelse(is.na(y), NA, h2o.ifelse(y == y_levels[[1]], 0, 1))
    else stop(paste0("`y` must be a numeric or binary vector - has ", length(y_levels), " levels"))
  }
  
  # Remove records where y is NA
  y_name <- colnames(y)
  encoding_data <- h2o.cbind(groupby_frame, y)
  encoding_data <- encoding_data[!is.na(encoding_data[[y_name]]), ]
  
  # Calculate sum of y and number of rows per level of groupby_frame
  te_mapping <- h2o.group_by(encoding_data, colnames(groupby_frame), sum(y_name), nrow(y_name))
  colnames(te_mapping)[which(colnames(te_mapping) == paste0("sum_", y_name))] <- "numerator"
  colnames(te_mapping)[which(colnames(te_mapping) == "nrow")] <- "denominator"
  
  return(te_mapping)
}

# Target Encoding Frame
#' 
#' Creates a target encoding frame based on a target encoding map.
#' 
#' For training data, calculates the mean of the target encoding map per group removing the value of the existing row.
#' For valiation data, calculates the mean of the target encoding map per grouop.
#' This can help predictive performance of high cardinality categorical columns in supervised learning problems.
#' 
#' @param groupby_frame An H2OFrame object with categorical columns in which to group by.
#' @param y An H2OFrame object with a single numeric or binary column.
#' @param train \code{Logical}. Whether to apply the target encoding to train data.
#' @param noise_level (Optional) The amount of random noise added to the target encoding.  This helps prevent overfitting. Defaults to 0.01 * range of y.
#' @param seed (Optional) A random seed used to generate draws from the uniform distribution for random noise. Defaults to -1.
#' @return Returns an H2OFrame object containing the leave one out target encoding.
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#' 
#' # Get Target Encoding Frame on bank-additional-full data with numeric `y`
#' data.hex = h2o.importFile(
#' path = "https://s3.amazonaws.com/h2o-public-test-data/smalldata/demos/bank-additional-full.csv",
#' destination_frame = "data.hex")
#' splits <- h2o.splitFrame(data.hex, seed = 1234)
#' train <- splits[[1]]
#' test <- splits[[2]]
#' mapping <- h2o.targetencoding_map(train[c("job", "marital")], train$age)
#' h2o.targetencoding_frame(test[c("job", "marital")], test$age, mapping, train = FALSE)
#' 
#' # Run Target Encoding on bank-additional-full data with binary `y`
#' h2o.target_encoding(data.hex$job, data.hex$y)
#' }
#' @export
h2o.targetencoding_frame <- function(groupby_frame, y, targetencoding_map, train, noise_level = NULL, seed = -1){
  
  if (missing(groupby_frame)) 
    stop("argument 'groupby_frame' is missing, with no default")
  if (missing(targetencoding_map)) 
    stop("argument 'targetencoding_map' is missing, with no default")
  
  if (!is.h2o(groupby_frame)) 
    stop("argument `groupby_frame` must be a valid H2OFrame")
  if (!is.h2o(targetencoding_map)) 
    stop("argument `targetencoding_map` ust be a valid H2OFrame")
  
  if (nrow(groupby_frame) != nrow(y))
    stop("`groupby_frame` and `y` must have the same number of rows")
  
  if (!Reduce('&', c("numerator", "denominator") %in% colnames(targetencoding_map)))
    stop("`targetencoding_map` must have columns: numerator, denominator")
  
  if (length(intersect(colnames(groupby_frame), colnames(targetencoding_map))) == 0L) 
    stop("`groupby_frame` and `targetencoding_map` must have intersecting column names to merge on")
  
  if (!is.null(noise_level))
    if (!is.numeric(noise_level) || length(noise_level) > 1L)
      stop("`noise_level` must be a numeric vector of length 1")
  else if (noise_level < 0)
    stop("`noise_level` must be non-negative")
  
  # Merge Target Encoding Mapping to groupby_frame
  y_name <- colnames(y)
  te_frame <- h2o.cbind(groupby_frame, y)
  te_frame <- h2o.merge(te_frame, targetencoding_map, all.x = TRUE, all.y = FALSE)
  
  # Calculate Mean Per Group
  if (train){
    
    # Calculate Mean Target per Group - removing value of existing row
    target_encoding <- h2o.ifelse(is.na(te_frame[[y_name]]), 
                                  te_frame$numerator/te_frame$denominator,
                                  (te_frame$numerator - te_frame[[y_name]])/(te_frame$denominator - 1))
    
  } else{
    
    # Calculate Mean Target per Group
    target_encoding <- te_frame$numerator/te_frame$denominator
    
  }
  
  # Add Random Noise
  
  if(is.null(noise_level)){
    # If `noise_level` is NULL, value chosen based on `y` distribution
    noise_level <- (max(y) - min(y))*0.01
  }
  
  if(noise_level > 0){
    # Generate random floats sampled from a uniform distribution  
    random_noise <- h2o.runif(target_encoding, seed = seed)
    # Scale within noise_level
    random_noise <- random_noise * 2 * noise_level - noise_level
    # Add noise to target_encoding
    target_encoding <- target_encoding + random_noise
  }
  
  colnames(target_encoding) <- "C1"
  
  return(target_encoding)
}

