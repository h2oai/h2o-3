\name{H2OPerfModel-class}
\Rdversion{1.1}
\docType{class}
\alias{H2OPerfModel-class}
\alias{show,H2OPerfModel-method}

\title{Class \code{"H2OPerfModel"}}
\description{
A class for constructing performance measures of H2O models.
}
\section{Objects from the Class}{
Objects can be created by calls of the form \code{new("H2OPerfModel", ...)}.
%%  ~~ describe objects here ~~ 
}
\section{Slots}{
  \describe{
    \item{\code{cutoffs}:}{A numeric vector of threshold values.}
    \item{\code{measure}:}{A numeric vector of performance values corresponding to the threshold values. The specific performance measure is given in \code{perf}.}
    \item{\code{perf}:}{A character string indicating the performance measure used to evaluate the model. One of either "F1", "accuracy", "precision", "recall", "specificity", or "max_per_class_error". See \code{\link{h2o.performance}} for a detailed description of each.}
    \item{\code{model}:}{Object of class \code{"list"} containing the following elements:
      \itemize{
      \item{\code{auc}: Area under the curve.}
      \item{\code{gini}: Gini coefficient.}
      \item{\code{best_cutoff}: Threshold value that optimizes the performance measure \code{perf}. If \code{perf} is "max_per_class_error", it is minimized at this threshold, otherwise, it is maximized.}
      \item{\code{F1}: F1 score at best cutoff.}
      \item{\code{accuracy}: Accuracy value at best cutoff. Estimated as \eqn{(TP+TN)/(P+N)}.}
      \item{\code{precision}: Precision value at best cutoff. Estimated as \eqn{TP/(TP+FP)}.}
      \item{\code{recall}: Recall value at best cutoff, i.e. the true positive rate \eqn{TP/P}.}
      \item{\code{specificity}: Specificity value at best cutoff, i.e. the true negative rate \eqn{TN/N}.}
      \item{\code{max_per_class_err}: Maximum per class error at best cutoff.}
      \item{\code{confusion}: Confusion matrix at best cutoff.}
      }
    }
    \item{\code{roc}:}{A data frame with two columns: TPR = true positive rate and FPR = false positive rate, calculated at the listed cutoffs.}
    \item{\code{gains}:}{A gains table and lift chart.}
  }
}
\section{Methods}{
  \describe{
    \item{show}{\code{signature(object = "H2OPerfModel")}: ... }
    \item{plot}{\code{signature(x = "H2OPerfModel", type, ...)}: ... }
	 }
}
\seealso{
%%  ~~objects to See Also as \code{\link{~~fun~~}}, ~~~
%%  ~~or \code{\linkS4class{CLASSNAME}} for links to other classes ~~~
\code{\link{h2o.performance}}, \code{\link{plot.H2OPerfModel}}
}
\examples{
showClass("H2OPerfModel")
}
\keyword{classes}
