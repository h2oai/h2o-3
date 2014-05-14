\name{H2OPCAModel-class}
\Rdversion{1.1}
\docType{class}
\alias{H2OPCAModel-class}
\alias{show,H2OPCAModel-method}

\title{Class \code{"H2OPCAModel"}}
\description{
A class for representing principal components analysis results.
}
\section{Objects from the Class}{
Objects can be created by calls of the form \code{new("H2OPCAModel", ...)}.
%%  ~~ describe objects here ~~ 
}
\section{Slots}{
  \describe{
    \item{\code{key}:}{Object of class \code{"character"}, representing the unique hex key that identifies the model.}
    \item{\code{data}:}{Object of class \code{\linkS4class{H2OParsedData}}, which is the input data used to build the model.}
    \item{\code{model}:}{Object of class \code{"list"} containing the following elements:
    \itemize{
    \item{\code{standardized}: }{A logical value indicating whether the data was centered and scaled.}
    \item{\code{sdev}: }{The standard deviations of the principal components (i.e., the square roots of the eigenvalues of the covariance/correlation matrix).}
    \item{\code{rotation}: }{The matrix of variable loadings (i.e., a matrix whose columns contain the eigenvectors).}}
    }
  }
}
\section{Extends}{
Class \code{"\linkS4class{H2OModel}"}, directly.
}
\section{Methods}{
  \describe{
    \item{show}{\code{signature(object = "H2OPCAModel")}: ... }
    \item{plot}{\code{signature(x = "H2OPCAModel", y, ...)}: ... }
    \item{summary}{\code{signature(object = "H2OPCAModel")}: ... }
	 }
}
\seealso{
%%  ~~objects to See Also as \code{\link{~~fun~~}}, ~~~
%%  ~~or \code{\linkS4class{CLASSNAME}} for links to other classes ~~~
\code{\link{h2o.prcomp}}
}
\examples{
showClass("H2OPCAModel")
}
\keyword{classes}
