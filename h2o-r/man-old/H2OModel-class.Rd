\name{H2OModel-class}
\Rdversion{1.1}
\docType{class}
\alias{H2OModel-class}

\title{Class \code{"H2OModel"}}
\description{
Object representing the model built by an H2O algorithm.
}
\section{Objects from the Class}{A virtual Class: No objects may be created from it.}
\section{Slots}{
  \describe{
    \item{\code{key}:}{Object of class \code{"character"}, representing the unique hex key that identifies the model.}
    \item{\code{data}:}{Object of class \code{"H2OParsedData"}, which is the input data used to build the model.}
    \item{\code{model}:}{Object of class \code{"list"} containing the characteristics of the model returned by the algorithm.}
  }
}
\section{Methods}{
No methods defined with class "H2OModel" in the signature.
}
\seealso{
%%  ~~objects to See Also as \code{\link{~~fun~~}}, ~~~
%%  ~~or \code{\linkS4class{CLASSNAME}} for links to other classes ~~~
\code{\linkS4class{H2OGLMModel}, \linkS4class{H2OKMeansModel}, \linkS4class{H2ODRFModel}, \linkS4class{H2OGBMModel}, \linkS4class{H2OPCAModel}, \linkS4class{H2ODeepLearningModel}}
}
\examples{
showClass("H2OModel")
}
\keyword{classes}
