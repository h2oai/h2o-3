extensions = dict(
    set_required_params="""
parms$training_frame <- training_frame
args <- .verify_dataxy(training_frame, x, y)
if( !missing(weights_column) && !is.null(weights_column)) args$x_ignore <- args$x_ignore[!( weights_column == args$x_ignore )]
parms$ignored_columns <- args$x_ignore
parms$response_column <- args$y
""",
)


doc = dict(
    preamble="""
Build an Isotonic Regression model

Builds an Isotonic Regression model on an H2OFrame with a single feature (univariate regression).
""",
    returns="""
Creates a \linkS4class{H2OModel} object of the right type.
""",
    seealso="""
\code{\link{predict.H2OModel}} for prediction
""",
    examples="""
library(h2o)
h2o.init()

N <- 100
x <- seq(N)
y <- sample(-50:50, N, replace=TRUE) + 50 * log1p(x)

train <- as.h2o(data.frame(x = x, y = y))
isotonic <- h2o.isotonicregression(x = "x", y = "y", training_frame = train)
"""
)
