\name{plot.H2OPerfModel}
\alias{plot.H2OPerfModel}
%- Also NEED an '\alias' for EACH other topic documented here.
\title{
Scatterplot of H2O Performance Measures
}
\description{
Draw scatter plot of a particular performance measure vs. thresholds for a H2O model, or the ROC curve.
}
\usage{
\method{plot}{H2OPerfModel}(x, type = "cutoffs", ...)
}
%- maybe also 'usage' for other objects documented here.
\arguments{
  \item{x}{An \code{\linkS4class{H2OPerfModel}} object.
}
  \item{type}{Either \code{"cutoffs"} to plot the performance measure \code{x@perf} versus thresholds \code{x@cutoffs}, or \code{"roc"} to plot the corresponding ROC curve (true positive rate vs. false positive rate).
}
  \item{\dots}{
Arguments to be passed to methods, such as graphical parameters (see \code{\link{par}} for details).
}
}

\seealso{
%% ~~objects to See Also as \code{\link{help}}, ~~~
\code{\linkS4class{H2OPerfModel}}, \code{\link{h2o.performance}}
}
\examples{
library(h2o)
localH2O = h2o.init()

# Run GBM classification on prostate.csv
prosPath = system.file("extdata", "prostate.csv", package = "h2o")
prostate.hex = h2o.importFile(localH2O, path = prosPath, key = "prostate.hex")
prostate.gbm = h2o.gbm(y = 2, x = 3:9, data = prostate.hex)

# Calculate performance measures at threshold that maximizes precision
prostate.pred = h2o.predict(prostate.gbm)
prostate.perf = h2o.performance(prostate.pred[,3], prostate.hex$CAPSULE, measure = "precision")

plot(prostate.perf, type = "cutoffs")     # Plot precision vs. thresholds
plot(prostate.perf, type = "roc")         # Plot ROC curve
}
