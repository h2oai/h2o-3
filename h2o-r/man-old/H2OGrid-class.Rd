\name{H2OGrid-class}
\Rdversion{1.1}
\docType{class}
\alias{H2OGrid-class}
\alias{show,H2OGrid-method}

\title{Class \code{"H2OGrid"}}
\description{
Object representing the models built by a H2O grid search algorithm.
}
\section{Objects from the Class}{A virtual Class: No objects may be created from it.}
\section{Slots}{
  \describe{
    \item{\code{key}:}{Object of class \code{"character"}, representing the unique hex key that identifies the model.}
    \item{\code{data}:}{Object of class \code{"H2OParsedData"}, which is the input data used to build the model.}
    \item{\code{model}:}{Object of class \code{"list"} containing \code{"H2OModel"} objects representing the models returned by the grid search algorithm.}
    \item{\code{sumtable}:}{Object of class \code{"list"} containing summary statistics of all the models returned by the grid search algorithm.}
  }
}
\section{Methods}{
  \describe{
    \item{show}{\code{signature(object = "H2OGrid")}: ... }
	 }
}
\seealso{
%%  ~~objects to See Also as \code{\link{~~fun~~}}, ~~~
%%  ~~or \code{\linkS4class{CLASSNAME}} for links to other classes ~~~
\code{\linkS4class{H2OGLMGrid}, \linkS4class{H2OKMeansGrid}, \linkS4class{H2ODRFGrid}, \linkS4class{H2OGBMGrid}, \linkS4class{H2ODeepLearningGrid}}
}
\examples{
showClass("H2OGrid")
}
\keyword{classes}
