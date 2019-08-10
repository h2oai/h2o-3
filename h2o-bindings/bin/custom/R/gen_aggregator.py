rest_api_version = 99

extensions = dict(
    required_params=dict(training_frame=None, x=None),
    validate_required_params="",
    set_required_params="""
parms$training_frame <- training_frame
if(!missing(x))
  parms$ignored_columns <- .verify_datacols(training_frame, x)$cols_ignore
""",
    skip_default_set_params_for=['training_frame', 'response_column'],
    with_model="""
model@model$aggregated_frame_id <- model@model$output_frame$name
""",
    module="""
#' Retrieve an aggregated frame from an Aggregator model
#'
#' Retrieve an aggregated frame from the Aggregator model and use it to create a new frame.
#'
#' @param model an \linkS4class{H2OClusteringModel} corresponding from a \code{h2o.aggregator} call.
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' df <- h2o.createFrame(rows=100, cols=5, categorical_fraction=0.6, integer_fraction=0,
#'                       binary_fraction=0, real_range=100, integer_range=100, missing_fraction=0)
#' target_num_exemplars=1000
#' rel_tol_num_exemplars=0.5
#' encoding="Eigen"
#' agg <- h2o.aggregator(training_frame=df,
#'                      target_num_exemplars=target_num_exemplars,
#'                      rel_tol_num_exemplars=rel_tol_num_exemplars,
#'                      categorical_encoding=encoding)
#' # Use the aggregated frame to create a new dataframe
#' new_df <- h2o.aggregated_frame(agg)
#' }
#' @export
h2o.aggregated_frame <- function(model) {
  key <- model@model$aggregated_frame_id
  h2o.getFrame(key)
}
""",
)

doc = dict(
    preamble="""
Build an Aggregated Frame

Builds an Aggregated Frame of an H2OFrame.
""",
    params=dict(
        x="""A vector containing the \code{character} names of the predictors in the model."""
    ),
    examples="""
library(h2o)
h2o.init()
df <- h2o.createFrame(rows=100, cols=5, categorical_fraction=0.6, integer_fraction=0,
                      binary_fraction=0, real_range=100, integer_range=100, missing_fraction=0)
target_num_exemplars=1000
rel_tol_num_exemplars=0.5
encoding="Eigen"
agg <- h2o.aggregator(training_frame=df,
                     target_num_exemplars=target_num_exemplars,
                     rel_tol_num_exemplars=rel_tol_num_exemplars,
                     categorical_encoding=encoding)
"""
)
