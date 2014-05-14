\name{H2OGridVA-class}
\Rdversion{1.1}
\docType{class}
\alias{H2OGridVA-class}
\alias{show,H2OGridVA-method}

\title{Class \code{"H2OGridVA"}}
\description{
Object representing the models built by a H2O grid search algorithm on a ValueArray dataset.
}
\section{Objects from the Class}{A virtual Class: No objects may be created from it.}
\section{Slots}{
  \describe{
    \item{\code{key}:}{Object of class \code{"character"}, representing the unique hex key that identifies the model.}
    \item{\code{data}:}{Object of class \code{"H2OParsedDataVA"}, which is the input data used to build the model.}
    \item{\code{model}:}{Object of class \code{"list"} containing \code{"H2OModelVA"} objects representing the models returned by the grid search algorithm.}
    \item{\code{sumtable}:}{Object of class \code{"list"} containing summary statistics of all the models returned by the grid search algorithm. }
  }
}
\section{Methods}{
  \describe{
    \item{show}{\code{signature(object = "H2OGridVA")}: ... }
	 }
}
\seealso{
%%  ~~objects to See Also as \code{\link{~~fun~~}}, ~~~
%%  ~~or \code{\linkS4class{CLASSNAME}} for links to other classes ~~~
\code{\linkS4class{H2OGLMGridVA}}
}
\examples{
showClass("H2OGridVA")
}
\keyword{classes}
