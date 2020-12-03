extensions = dict(
    required_params=['x', 'event_column', 'training_frame'],
    validate_required_params="""
# If x is missing, then assume user wants to use all columns as features.
if (missing(x)) {
   if (is.numeric(event_column)) {
       x <- setdiff(col(training_frame), event_column)
   } else {
       x <- setdiff(colnames(training_frame), event_column)
   }
}
""",
    validate_params="""
if (is.null(interactions_only) && (! is.null(interactions) || ! is.null(interaction_pairs))) {
  used <- unique(c(interactions, unlist(sapply(interaction_pairs, function(x) {x[1]})), unlist(sapply(interaction_pairs, function(x) {x[2]}))))
  interactions_only <- setdiff(used, x)
  x <- c(x, interactions_only)
}
if (! is.null(stratify_by)) {
  stratify_by_only <- setdiff(stratify_by, x)
  x <- c(x, stratify_by_only)
}
if(!is.character(stop_column) && !is.numeric(stop_column)) {
  stop('argument "stop_column" must be a column name or an index')
}
""",
    set_required_params="""
parms$training_frame <- training_frame
args <- .verify_dataxy(training_frame, x, event_column)
if( !missing(offset_column) && !is.null(offset_column))  args$x_ignore <- args$x_ignore[!( offset_column == args$x_ignore )]
if( !missing(weights_column) && !is.null(weights_column)) args$x_ignore <- args$x_ignore[!( weights_column == args$x_ignore )]
parms$ignored_columns <- args$x_ignore
parms$response_column <- args$y
""",
)

doc = dict(
    preamble="""
Trains a Cox Proportional Hazards Model (CoxPH) on an H2O dataset
""",
    params=dict(
        x="""
(Optional) A vector containing the names or indices of the predictor variables to use in building the model.
If x is missing, then all columns except event_column, start_column and stop_column are used.
""",
        event_column="""
The name of binary data column in the training frame indicating the occurrence of an event.
"""
    ),
    examples="""
library(h2o)
h2o.init()

# Import the heart dataset
f <- "https://s3.amazonaws.com/h2o-public-test-data/smalldata/coxph_test/heart.csv"
heart <- h2o.importFile(f)

# Set the predictor and response
predictor <- "age"
response <- "event"

# Train a Cox Proportional Hazards model 
heart_coxph <- h2o.coxph(x = predictor, training_frame = heart,
                         event_column = "event",
                         start_column = "start", 
                         stop_column = "stop")
"""
)
