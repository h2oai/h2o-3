\name{H2OKMeansModelVA-class}
\Rdversion{1.1}
\docType{class}
\alias{H2OKMeansModelVA-class}
\alias{show,H2OKMeansModelVA-method}

\title{Class \code{"H2OKMeansModelVA"}}
\description{
A class for representing k-means clustering models built on ValueArray data.
}
\section{Objects from the Class}{
Objects can be created by calls of the form \code{new("H2OKMeansModelVA", ...)}.
%%  ~~ describe objects here ~~ 
}
\section{Slots}{
  \describe{
    \item{\code{key}:}{Object of class \code{"character"}, representing the unique hex key that identifies the model.}
    \item{\code{data}:}{Object of class \code{\linkS4class{H2OParsedDataVA}}, which is the input data used to build the model.}
    \item{\code{model}:}{Object of class \code{"list"} containing the following elements:
      \itemize{
      \item{\code{cluster}: A \code{\linkS4class{H2OParsedDataVA}} object, which contains the cluster assignment for each observation in the input data.}
      \item{\code{centers}: A matrix of cluster centers.}
      \item{\code{withinss}: Within-cluster sum of squared errors for each cluster.}
      \item{\code{tot.withinss}: Sum total within-cluster sum of squared errors.}
      \item{\code{size}: Number of observations in each cluster.}
      }
    }
  }
}
\section{Extends}{
Class \code{"\linkS4class{H2OModelVA}"}, directly.
}
\section{Methods}{
  \describe{
    \item{show}{\code{signature(object = "H2OKMeansModelVA")}: ... }
	 }
}
\seealso{
%%  ~~objects to See Also as \code{\link{~~fun~~}}, ~~~
%%  ~~or \code{\linkS4class{CLASSNAME}} for links to other classes ~~~
\code{\link{h2o.kmeans}}
}
\examples{
showClass("H2OKMeansModelVA")
}
\keyword{classes}
