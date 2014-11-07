\name{H2OGBMModel-class}
\Rdversion{1.1}
\docType{class}
\alias{H2OGBMModel-class}
\alias{show,H2OGBMModel-method}

\title{Class \code{"H2OGBMModel"}}
\description{
A class for representing generalized boosted classification/regression models.
}
\section{Objects from the Class}{
Objects can be created by calls of the form \code{new("H2OGBMModel", ...)}.
%%  ~~ describe objects here ~~ 
}
\section{Slots}{
  \describe{
    \item{\code{key}:}{Object of class \code{"character"}, representing the unique hex key that identifies the model.}
    \item{\code{data}:}{Object of class \code{\linkS4class{H2OParsedData}}, which is the input data used to build the model.}
    \item{\code{model}:}{Object of class \code{"list"} containing the following elements:
      \itemize{
      \item{\code{err}: The mean-squared error in each tree.}
      \item{\code{cm}: (Only for classification). The confusion matrix of the response, with actual observations as rows and predicted values as columns.}
      }
  }
  \item{\code{valid}:}{Object of class \code{\linkS4class{H2OParsedData}}, which is the dataset used to validate the model.}
  \item{\code{xval}:}{List of objects of class \code{"H2OGBMModel"}, representing the n-fold cross-validation models.}
}}
\section{Extends}{
Class \code{"\linkS4class{H2OModel}"}, directly.
}
\section{Methods}{
  \describe{
    \item{show}{\code{signature(object = "H2OGBMModel")}: ... }
	 }
}
\seealso{
%%  ~~objects to See Also as \code{\link{~~fun~~}}, ~~~
%%  ~~or \code{\linkS4class{CLASSNAME}} for links to other classes ~~~
\code{\link{h2o.gbm}}
}
\examples{
showClass("H2OGBMModel")
}
\keyword{classes}
