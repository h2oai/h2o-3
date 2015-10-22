\name{H2ODRFModel-class}
\Rdversion{1.1}
\docType{class}
\alias{H2ODRFModel-class}
\alias{show,H2ODRFModel-method}

\title{Class \code{"H2ODRFModel"}}
\description{
A class for representing random forest ensembles.
}
\section{Objects from the Class}{
Objects can be created by calls of the form \code{new("H2ODRFModel", ...)}.
%%  ~~ describe objects here ~~ 
}
\section{Slots}{
  \describe{
    \item{\code{key}:}{Object of class \code{"character"}, representing the unique hex key that identifies the model.}
    \item{\code{data}:}{Object of class \code{"H2OParsedData"}, which is the input data used to build the model.}
    \item{\code{model}:}{Object of class \code{"list"} containing the following elements:
      \itemize{
        \item{\code{type}: The type of the tree, which at this point must be classification.}
        \item{\code{ntree}: Number of trees grown.}
        \item{\code{oob_err}: Out of bag error rate.}
        \item{\code{forest}: A matrix giving the minimum, mean, and maximum of the tree depth and number of leaves.}
        \item{\code{confusion}: Confusion matrix of the prediction.}
        }
      }
    \item{\code{valid}:}{Object of class \code{"H2OParsedData"}, which is the data used for validating the model.}
    \item{\code{xval}:}{List of objects of class \code{"H2ODRFModel"}, representing the n-fold cross-validation models.}
  }
}
\section{Extends}{
Class \code{"\linkS4class{H2OModel}"}, directly.
}
\section{Methods}{
  \describe{
    \item{show}{\code{signature(object = "H2ODRFModel")}: ... }
	 }
}
\seealso{
%%  ~~objects to See Also as \code{\link{~~fun~~}}, ~~~
%%  ~~or \code{\linkS4class{CLASSNAME}} for links to other classes ~~~
\code{\link{h2o.randomForest}}
}
\examples{
showClass("H2ODRFModel")
}
\keyword{classes}
