\name{H2OGLMModelList-class}
\Rdversion{1.1}
\docType{class}
\alias{H2OGLMModelList-class}
\alias{show,H2OGLMModelList-method}
\alias{summary,H2OGLMModelList-method}

\title{Class \code{"H2OGLMModelList"}}
\description{
Object representing the models built by a H2O GLM search over lambda values.
}
\section{Objects from the Class}{
Objects can be created by calls of the form \code{new("H2OGLMModelList", ...)}.
%%  ~~ describe objects here ~~ 
}
\section{Slots}{
  \describe{
    \item{\code{models}:}{Object of class \code{"list"} containing \code{"H2OGLMModel"} objects representing the models returned from the lambda search. }
    \item{\code{best_model}:}{Object of class \code{"numeric"} indicating the index of the model with the optimal lambda value in the above list. }
    \item{\code{lambdas}:}{Object of class \code{"numeric"} indicating the optimal lambda value from the lambda search. }
  }
}
\section{Methods}{
  \describe{
    \item{show}{\code{signature(object = "H2OGLMModelList")}: ... }
    \item{summary}{\code{signature(object = "H2OGLMModelList")}: ... }
	 }
}
\seealso{
%%  ~~objects to See Also as \code{\link{~~fun~~}}, ~~~
%%  ~~or \code{\linkS4class{CLASSNAME}} for links to other classes ~~~
\code{\linkS4class{H2OGLMModel}, \link{h2o.glm}}
}
\examples{
showClass("H2OGLMModelList")
}
\keyword{classes}
