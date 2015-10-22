\name{h2o.kmeans}
\alias{h2o.kmeans}
\title{
H2O: K-Means Clustering
}
\description{Performs k-means clustering on a data set.}
\usage{
h2o.kmeans(data, centers, cols = "", key = "", iter.max = 10, 
  normalize = FALSE, init = "none", seed = 0, dropNACols = FALSE)
}
%- maybe also 'usage' for other objects documented here.
\arguments{
  \item{data}{
An \code{\linkS4class{H2OParsedData}} object containing the variables in the model.
}
  \item{centers}{
The number of clusters k.
}
  \item{cols}{
(Optional) A vector containing the names of the data columns on which k-means runs. If blank, k-means clustering will be run on the entire data set.
}
  \item{key}{
(Optional) The unique hex key assigned to the resulting model. If none is given, a key will automatically be generated.
}
  \item{iter.max}{
(Optional) The maximum number of iterations allowed.
}
  \item{normalize}{
(Optional) A logical value indicating whether the data should be normalized before running k-means.
}
  \item{init}{
(Optional) Method by which to select the k initial cluster centroids. Possible values are \code{"none"} for random initialization, \code{"plusplus"} for k-means++ initialization, and \code{"furthest"} for initialization at the furthest point from each successive centroid. See the \href{http://docs.0xdata.com/datascience/kmeans.html}{H2O K-means documentation} for more details.
  }
  \item{seed}{
  (Optional) Random seed used to initialize the cluster centroids.
  }
  \item{dropNACols}{
  (Optional) A logical value indicating whether to drop columns with more than 10\% entries that are NA.
  }
}
\value{
An object of class \code{\linkS4class{H2OKMeansModel}} with slots key, data, and model, where the last is a list of the following components:
  \item{centers }{A matrix of cluster centers.}
  \item{cluster }{A \code{\linkS4class{H2OParsedData}} object containing the vector of integers (from 1 to k), which indicate the cluster to which each point is allocated.}
  \item{size }{The number of points in each cluster.}
  \item{withinss }{Vector of within-cluster sum of squares, with one component per cluster.}
  \item{tot.withinss }{Total within-cluster sum of squares, i.e., sum(withinss).}
}

\seealso{
%% ~~objects to See Also as \code{\link{help}}, ~~~
\code{\link{h2o.importFile}, \link{h2o.importFolder}, \link{h2o.importHDFS}, \link{h2o.importURL}, \link{h2o.uploadFile}}
}

\examples{
library(h2o)
localH2O = h2o.init()
prosPath = system.file("extdata", "prostate.csv", package = "h2o")
prostate.hex = h2o.importFile(localH2O, path = prosPath)
h2o.kmeans(data = prostate.hex, centers = 10, cols = c("AGE", "RACE", "VOL", "GLEASON"))
}
