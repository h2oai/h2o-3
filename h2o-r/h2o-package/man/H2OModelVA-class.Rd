\name{H2OModelVA-class}
\Rdversion{1.1}
\docType{class}
\alias{H2OModelVA-class}

\title{Class \code{"H2OModelVA"}}
\description{
Object representing the model built by an H2O algorithm on a ValueArray dataset.
}
\section{Objects from the Class}{A virtual Class: No objects may be created from it.}
\section{Slots}{
  \describe{
    \item{\code{key}:}{Object of class \code{"character"}, representing the unique hex key that identifies the model.}
    \item{\code{data}:}{Object of class \code{"H2OParsedDataVA"}, which is the input data used to build the model.}
    \item{\code{model}:}{Object of class \code{"list"} containing the characteristics of the model returned by the algorithm.}
  }
}
\section{Methods}{
No methods defined with class "H2OModelVA" in the signature.
}
\seealso{
%%  ~~objects to See Also as \code{\link{~~fun~~}}, ~~~
%%  ~~or \code{\linkS4class{CLASSNAME}} for links to other classes ~~~
\code{\linkS4class{H2OGLMModelVA}, \linkS4class{H2OKMeansModelVA}, \linkS4class{H2ORFModelVA}}
}
\examples{
showClass("H2OModelVA")
}
\keyword{classes}
