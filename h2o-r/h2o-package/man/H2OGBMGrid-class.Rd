\name{H2OGBMGrid-class}
\Rdversion{1.1}
\docType{class}
\alias{H2OGBMGrid-class}

\title{Class \code{"H2OGBMGrid"}}
\description{
Object representing the models built by a H2O GBM grid search.
}
\section{Objects from the Class}{
Objects can be created by calls of the form \code{new("H2OGBMGrid", ...)}.
%%  ~~ describe objects here ~~ 
}
\section{Slots}{
  \describe{
    \item{\code{key}:}{Object of class \code{"character"}, representing the unique hex key that identifies the model.}
    \item{\code{data}:}{Object of class \code{"H2OParsedData"}, which is the input data used to build the model.}
    \item{\code{model}:}{Object of class \code{"list"} containing \code{"H2OGBMModel"} objects representing the models returned by the GBM grid search.}
    \item{\code{sumtable}:}{Object of class \code{"list"} containing summary statistics of all the models returned by the GBM grid search.}
  }
}
\section{Extends}{
Class \code{"\linkS4class{H2OGrid}"}, directly.
}
\section{Methods}{
No methods defined with class "H2OGBMGrid" in the signature.
}
\seealso{
%%  ~~objects to See Also as \code{\link{~~fun~~}}, ~~~
%%  ~~or \code{\linkS4class{CLASSNAME}} for links to other classes ~~~
\code{\linkS4class{H2OGBMModel}, \link{h2o.gbm}}
}
\examples{
showClass("H2OGBMGrid")
}
\keyword{classes}
