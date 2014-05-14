\name{H2OGLMGridVA-class}
\Rdversion{1.1}
\docType{class}
\alias{H2OGLMGridVA-class}
\alias{show,H2OGLMGridVA-method}

\title{Class \code{"H2OGLMGridVA"}}
\description{
Object representing the models built by a H2O GLM grid search on ValueArray.
}
\section{Objects from the Class}{
Objects can be created by calls of the form \code{new("H2OGLMGridVA", ...)}.
%%  ~~ describe objects here ~~ 
}
\section{Slots}{
  \describe{
    \item{\code{key}:}{Object of class \code{"character"}, representing the unique hex key that identifies the model.}
    \item{\code{data}:}{Object of class \code{"H2OParsedDataVA"}, which is the input data used to build the model.}
    \item{\code{model}:}{Object of class \code{"list"} containing \code{"H2OGLMModelVA"} objects representing the models returned by the GLM (ValueArray) grid search.}
    \item{\code{sumtable}:}{Object of class \code{"list"} containing summary statistics of all the models returned by the GLM (ValueArray) grid search.}
  }
}
\section{Extends}{
Class \code{"\linkS4class{H2OGridVA}"}, directly.
}
\section{Methods}{
  \describe{
    \item{show}{\code{signature(object = "H2OGLMGridVA")}: ... }
	 }
}
\seealso{
%%  ~~objects to See Also as \code{\link{~~fun~~}}, ~~~
%%  ~~or \code{\linkS4class{CLASSNAME}} for links to other classes ~~~
\code{\linkS4class{H2OGLMModelVA}, \link{h2o.glm}}
}
\examples{
showClass("H2OGLMGridVA")
}
\keyword{classes}
