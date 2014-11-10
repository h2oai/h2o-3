\name{H2OKMeansModel-class}
\Rdversion{1.1}
\docType{class}
\alias{H2OKMeansModel-class}
\alias{show,H2OKMeansModel-method}

\title{Class \code{"H2OKMeansModel"}}
\description{
A class for representing k-means models.
}
\section{Objects from the Class}{
Objects can be created by calls of the form \code{new("H2OKMeansModel", ...)}.
%%  ~~ describe objects here ~~ 
}
\section{Slots}{
  \describe{
    \item{\code{key}:}{Object of class \code{"character"}, representing the unique hex key that identifies the model.}
    \item{\code{data}:}{Object of class \code{\linkS4class{H2OParsedData}}, which is the input data used to build the model.}
    \item{\code{model}:}{Object of class \code{"list"} containing the following elements:
    \itemize{
    \item{\code{centers}: A matrix of cluster centers.}
    \item{\code{cluster}: A \code{\linkS4class{H2OParsedData}} object containing the vector of integers (from 1:k), which indicate the cluster to which each point is allocated.}
    \item{\code{size}: The number of points in each cluster.}
    \item{\code{withinss}: Vector of within-cluster sum of squares, with one component per cluster.}
    \item{\code{tot.withinss}: Total within-cluster sum of squares, i.e., \code{sum(withinss)}.}
    }}
  }
}
\section{Methods}{
  \describe{
    \item{show}{\code{signature(object = "H2OKMeansModel")}: ... }
	 }
}
\seealso{
%%  ~~objects to See Also as \code{\link{~~fun~~}}, ~~~
%%  ~~or \code{\linkS4class{CLASSNAME}} for links to other classes ~~~
\code{\link{h2o.kmeans}}
}
\examples{
showClass("H2OKMeansModel")
}
\keyword{classes}
