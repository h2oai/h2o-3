rest_api_version = 3

def update_param(name, param):
    if name == 'algorithm_params':
        param['default_value'] = None
        return param
    return None  # param untouched

extensions = dict(
    required_params=['x', 'y', 'training_frame'],  # empty to override defaults in gen_defaults
    set_required_params="""
    parms$training_frame <- training_frame
    args <- .verify_dataxy(training_frame, x, y)
    if (missing(protected_columns)) { # core infogram
      if (!missing(safety_index_threshold)) {
        warning("Should not set safety_index_threshold for core infogram runs.  Set net_information_threshold instead.
            Using default of 0.1 if not set")
      }
      if (!missing(relevance_index_threshold)) {
        warning("Should not set relevance_index_threshold for core infogram runs.  Set total_information_threshold 
        instead.   Using default of 0.1 if not set")
      }
    } else { # fair infogram
      if (!missing(net_information_threshold)) {
      warning("Should not set net_information_threshold for fair infogram runs, set safety_index_threshold instead.  
        Using default of 0.1 if not set")
      }
      if (!missing(total_information_threshold)) {
        warning("Should not set total_information_threshold for fair infogram runs, set relevance_index_threshold
         instead.    Using default of 0.1 if not set")
      }
    }
    
    if( !missing(offset_column) && !is.null(offset_column))  args$x_ignore <- args$x_ignore[!( offset_column == args$x_ignore )]
    if( !missing(weights_column) && !is.null(weights_column)) args$x_ignore <- args$x_ignore[!( weights_column == args$x_ignore )]
    if( !missing(fold_column) && !is.null(fold_column)) args$x_ignore <- args$x_ignore[!( fold_column == args$x_ignore )]
    parms$ignored_columns <- args$x_ignore
    parms$response_column <- args$y
    """,

    skip_default_set_params_for=['training_frame', 'ignored_columns', 'response_column', 'max_confusion_matrix_size',
                                 "algorithm_params"],
    set_params="""
if (!missing(algorithm_params))
    parms$algorithm_params <- as.character(toJSON(algorithm_params, pretty = TRUE))
h2o.show_progress() # enable progress bar explicitly
""",
    with_model="""
# Convert algorithm_params back to list if not NULL, added after obtaining model
if (!missing(algorithm_params)) {
    model@parameters$algorithm_params <- list(fromJSON(model@parameters$algorithm_params))[[1]] #Need the `[[ ]]` to avoid a nested list
}

infogram_model <- new("H2OInfogram", model_id=model@model_id)       
model <- infogram_model                
""",
    module="""
#' Extract the admissible attributes/predictors out of the H2O Infogram Model.
#'
#' @param model A fitted \linkS4class{H2OInfogram} object.
#' @export 
h2o.get_admissible_attributes <- function(model) {
  if (is(model, "H2OInfogram") && (model@algorithm == "infogram"))
    return(model@admissible_features)
}

#' Plot an H2O Infogram
#'
#' Plots the Infogram for an H2OInfogram object.
#'
#' @param x A fitted \linkS4class{H2OInfogram} object.
#' @param ... additional arguments to pass on.
#' @return A ggplot2 object.
#' @seealso \code{\link{h2o.infogram}}
#' @examples
#' \dontrun{
#' h2o.init()
#' 
#' # Convert iris dataset to an H2OFrame
#' train <- as.h2o(iris)
#' 
#' # Create and plot infogram
#' ig <- h2o.infogram(y = "Species", training_frame = train)
#' plot(ig)
#' 
#' }
#' @export
plot.H2OInfogram <- function(x, ...) {
  .check_for_ggplot2() # from explain.R
  varargs <- list(...)
  if ("title" %in% names(varargs)) {
    title <- varargs$title
  } else {
    title <- "Infogram"
  }
  if ("total_information" %in% names(x@admissible_score)) {
    # core infogram
    xlab <- "Total Information"
    ylab <- "Net Information"
    xthresh <- x@total_information_threshold
    ythresh <- x@net_information_threshold
  } else {
    # fair infogram
    xlab <- "Relevance Index"
    ylab <- "Safety Index"
    xthresh <- x@relevance_index_threshold
    ythresh <- x@safety_index_threshold
  }
  df <- as.data.frame(x@admissible_score)
  # use generic names for x, y for easier ggplot code
  names(df) <- c("column",
                 "admissible",
                 "admissible_index",
                 "ig_x",
                 "ig_y",
                 "raw")
  ggplot2::ggplot(data = df, ggplot2::aes_(~ig_x, ~ig_y)) +
    ggplot2::geom_point() +
    ggplot2::geom_polygon(ggplot2::aes(x, y), data = data.frame(
      x = c(xthresh, xthresh, -Inf, -Inf, Inf, Inf, xthresh),
      y = c(ythresh, Inf, Inf, -Inf, -Inf, ythresh, ythresh)
    ), alpha = 0.1, fill = "#CC663E") +
    ggplot2::geom_path(ggplot2::aes(x, y), data = data.frame(
      x = c(xthresh, xthresh, NA, xthresh, Inf),
      y = c(ythresh,     Inf, NA, ythresh, ythresh)
    ), color = "red", linetype = "dashed") +
    ggplot2::geom_text(ggplot2::aes_(~ig_x, ~ig_y, label = ~column),
                       data = df[as.logical(df$admissible),], nudge_y = -0.0325,
                       color = "blue", size = 2.5) +
    ggplot2::xlab(xlab) +
    ggplot2::ylab(ylab) +
    ggplot2::coord_fixed(xlim = c(0, 1.1), ylim = c(0, 1.1), expand = FALSE) +
    ggplot2::theme_bw() +
    ggplot2::ggtitle(title) +
    ggplot2::theme(plot.title = ggplot2::element_text(hjust = 0.5))
}
"""
)
# modify this for infogram.
doc = dict(
    preamble="""
H2O Infogram

The infogram is a graphical information-theoretic interpretability tool which allows the user to quickly spot the core, decision-making variables 
that uniquely and safely drive the response, in supervised learning problems. The infogram can significantly cut down the number of predictors needed to build 
a model by identifying only the most valuable, admissible features. When protected variables such as race or gender are present in the data, the admissibility 
of a variable is determined by a safety and relevancy index, and thus serves as a diagnostic tool for fairness. The safety of each feature can be quantified and 
variables that are unsafe will be considered inadmissible. Models built using only admissible features will naturally be more interpretable, given the reduced 
feature set.  Admissible models are also less susceptible to overfitting and train faster, while providing similar accuracy as models built using all available features.

The infogram allows the user to quickly spot the admissible decision-making variables that are driving the response.  
There are two types of infogram plots: Core and Fair Infogram.

The Core Infogram plots all the variables as points on two-dimensional grid of total vs net information.  The x-axis is total information, 
a measure of how much the variable drives the response (the more predictive, the higher the total information). 
The y-axis is net information, a measure of how unique the variable is.  The top right quadrant of the infogram plot is the admissible section; the variables
located in this quadrant are the admissible features.  In the Core Infogram, the admissible features are the strongest, unique drivers of 
the response.

If sensitive or protected variables are present in data, the user can specify which attributes should be protected while training using the \code{protected_columns} 
argument. All non-protected predictor variables will be checked to make sure that there's no information pathway to the response through a protected feature, and 
deemed inadmissible if they possess little or no informational value beyond their use as a dummy for protected attributes. The Fair Infogram plots all the features 
as points on two-dimensional grid of relevance vs safety.  The x-axis is relevance index, a measure of how much the variable drives the response (the more predictive, 
the higher the relevance). The y-axis is safety index, a measure of how much extra information the variable has that is not acquired through the protected variables.  
In the Fair Infogram, the admissible features are the strongest, safest drivers of the response.
    """,
    examples="""
    h2o.init()
    
    # Convert iris dataset to an H2OFrame    
    df <- as.h2o(iris)
    
    # Infogram
    ig <- h2o.infogram(y = "Species", training_frame = df)
    ig  
    plot(ig)
    """
)
