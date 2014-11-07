\name{h2o.gapStatistic}
\alias{h2o.gapStatistic}
\title{
Compute Gap Statistic from H2O Dataset 
}
\description{
Compute the gap statistic of a H2O dataset. The gap statistic is a measure of the goodness of fit of a clustering algorithm. For each number of clusters \code{k}, it compares \eqn{\log(W(k))} with \eqn{E^*[\log(W(k))]} where the latter is defined via bootstrapping.
}
\usage{
h2o.gapStatistic(data, cols = "", K.max = 10, B = 100, boot_frac = 0.33, seed = 0)
}
%- maybe also 'usage' for other objects documented here.
\arguments{
  \item{data}{An \code{\linkS4class{H2OParsedData}} object.}
  \item{cols}{(Optional) A vector of column names or indices indicating the features to analyze. By default, all columns in the dataset are analyzed.}
  \item{K.max}{The maximum number of clusters to consider. Must be at least 2.}
  \item{B}{A positive integer indicating the number of Monte Carlo (bootstrap) samples for simulating the reference distribution.}
  \item{boot_frac}{Fraction of data size to replicate in each Monte Carlo simulation.}
  \item{seed}{(Optional) Random number seed for breaking ties between equal probabilities.}
}

\details{
IMPORTANT: Currently, you must initialize H2O with the flag \code{beta = TRUE} in \code{h2o.init} in order to use this method!
}

\value{
A list containing the following components:
  \item{log_within_ss }{Log of the pooled cluster within sum of squares per value of \code{k}.}
  \item{boot_within_ss }{Monte Carlo bootstrap replicate averages of \code{log_within_ss} per value of \code{k}.}
  \item{se_boot_within_ss }{Standard error from the Monte Carlo simulated data for each iteration.}
  \item{gap_stats }{Gap statistics per value of \code{k}.}
  \item{k_opt }{Optimal number of clusters.}
}

\references{
Tibshirani, R., Walther, G. and Hastie, T. (2001). Estimating the number of data clusters via the Gap statistic. \emph{Journal of the Royal Statistical Society B}, \strong{63}, 411-423.

Tibshirani, R., Walther, G. and Hastie, T. (2000). Estimating the number of clusters in a dataset via the Gap statistic. Technical Report. Stanford.
}

\seealso{
\code{\linkS4class{H2OParsedData}}, \code{h2o.kmeans}
}
\examples{
# Currently still in beta, so don't automatically run example
\dontrun{
library(h2o)
localH2O = h2o.init(beta = TRUE)
irisPath = system.file("extdata", "iris.csv", package = "h2o")
iris.hex = h2o.importFile(localH2O, path = irisPath)
h2o.gapStatistic(iris.hex, K.max = 10, B = 100)
}
}
