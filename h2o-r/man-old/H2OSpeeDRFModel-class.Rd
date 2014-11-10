\name{H2OSpeeDRFModel-class}
\Rdversion{1.1}
\docType{class}
\alias{H2OSpeeDRFModel-class}
\alias{show,H2OSpeeDRFModel-method}

\title{Class \code{"H2OSpeeDRFModel"}}
\description{
A class for representing single-node random forest ensembles.
}
\section{Objects from the Class}{
Objects can be created by calls of the form \code{new("H2OSpeeDRFModel", ...)}.
%%  ~~ describe objects here ~~ 
}
\section{Slots}{
  \describe{
    \item{\code{key}:}{Object of class \code{"character"}, representing the unique hex key that identifies the model.}
    \item{\code{data}:}{Object of class \code{"H2OParsedData"}, which is the input data used to build the model.}
    \item{\code{model}:}{Object of class \code{"list"} containing the following elements:
      \itemize{
        \item{\code{ntree}: Number of trees grown.}
        \item{\code{mse}: Mean squared error for each tree.}
        \item{\code{confusion}: Confusion matrix of the prediction.}
        }
      }
    \item{\code{valid}:}{Object of class \code{"H2OParsedData"}, which is the data used for validating the model.}
    \item{\code{xval}:}{List of objects of class \code{"H2OSpeeDRFModel"}, representing the n-fold cross-validation models.}
  }
}
\section{Extends}{
Class \code{"\linkS4class{H2OModel}"}, directly.
}
\section{Methods}{
  \describe{
    \item{show}{\code{signature(object = "H2OSpeeDRFModel")}: ... }
	 }
}
\seealso{
%%  ~~objects to See Also as \code{\link{~~fun~~}}, ~~~
%%  ~~or \code{\linkS4class{CLASSNAME}} for links to other classes ~~~
\code{\link{h2o.SpeeDRF}}
}
\examples{
showClass("H2OSpeeDRFModel")
}
\keyword{classes}
