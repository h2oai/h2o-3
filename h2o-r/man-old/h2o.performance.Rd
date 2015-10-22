\name{h2o.performance}
\alias{h2o.performance}
\title{
Performance Measures
}
\description{
Evaluate the predictive performance of a model via various measures.
}
\usage{
h2o.performance(data, reference, measure = "accuracy", thresholds, gains = TRUE, ...)
}
\arguments{
  \item{data}{
  An \code{\linkS4class{H2OParsedData}} object containing the predicted outcome scores. Must be a single column with the same number of rows as \code{reference}.
  }
  \item{reference}{
  An \code{\linkS4class{H2OParsedData}} object containing the actual outcomes for comparison. Must be a single binary column with all entries in \{0,1\}.
  }
  \item{measure}{
  A character string indicating the performance measure to optimize. Must be one of the following: 
  \itemize{
      \item{\code{F1}: F1 score, equal to \eqn{2*(Precision*Recall)/{Precision + Recall}}}
      \item{\code{accuracy}: Accuracy of model, estimated as \eqn{(TP+TN)/(P+N)}.}
      \item{\code{precision}: Precision of model, estimated as \eqn{TP/(TP+FP)}.}
      \item{\code{recall}: Recall of model, i.e. the true positive rate \eqn{TP/P}.}
      \item{\code{specificity}: Specificity of model, i.e. the true negative rate \eqn{TN/N}.}
      \item{\code{max_per_class_error}: Maximum per class error in model.}
  }
  }
  \item{thresholds}{
  (Optional) A numeric vector from 0 to 1 indicating the threshold values at which to compute the performance measure. If missing, the range will be automatically generated.
  TODO: Still not sure I understand what exactly these thresholds are, is it the FPR or something else?
  }
  \item{gains}{If TRUE, then `h2o.performance` will additionally compute the gains and lift charts. These can be accessed via @gains}
  \item{\dots}{Additional arguments to pass to the `h2o.gains` method. Accepts "percents" and "groups".}
}
\value{
An object of class \code{\linkS4class{H2OPerfModel}} with slots cutoffs, measure, perf (the performance measure selected), roc (data frame used to plot ROC) and model, where the last is a list of the following components:
\item{auc }{Area under the curve.}
\item{gini }{Gini coefficient.}
\item{best_cutoff }{Threshold value that optimizes the performance measure.}
\item{F1 }{F1 score at best cutoff.}
\item{accuracy }{Accuracy value at best cutoff.}
\item{precision }{Precision value at best cutoff.}
\item{recall }{Recall value at best cutoff.}
\item{specificity }{Specificity value at best cutoff.}
\item{max_per_class_err }{Maximum per class error at best cutoff.}
\item{confusion }{Confusion matrix at best cutoff.}
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
head(prostate.pred)
h2o.performance(prostate.pred[,3], prostate.hex$CAPSULE, measure = "precision")
}
