\name{H2ODRFGrid-class}
\Rdversion{1.1}
\docType{class}
\alias{H2ODRFGrid-class}

\title{Class \code{"H2ODRFGrid"}}
\description{
Object representing the models built by a H2O distributed random forest grid search.
}
\section{Objects from the Class}{
Objects can be created by calls of the form \code{new("H2ODRFGrid", ...)}.
%%  ~~ describe objects here ~~ 
}
\section{Slots}{
  \describe{
    \item{\code{key}:}{Object of class \code{"character"}, representing the unique hex key that identifies the model.}
    \item{\code{data}:}{Object of class \code{"H2OParsedData"}, which is the input data used to build the model.}
    \item{\code{model}:}{Object of class \code{"list"} containing \code{"H2ODRFModel"} objects representing the models returned by the distributed random forest grid search.}
    \item{\code{sumtable}:}{Object of class \code{"list"} containing summary statistics of all the models returned by the distributed random forest grid search.}
  }
}
\section{Extends}{
Class \code{"\linkS4class{H2OGrid}"}, directly.
}
\section{Methods}{
No methods defined with class "H2ODRFGrid" in the signature.
}
\seealso{
%%  ~~objects to See Also as \code{\link{~~fun~~}}, ~~~
%%  ~~or \code{\linkS4class{CLASSNAME}} for links to other classes ~~~
\code{\linkS4class{H2ODRFModel}, \link{h2o.randomForest}}
}
\examples{
showClass("H2ODRFGrid")
}
\keyword{classes}
