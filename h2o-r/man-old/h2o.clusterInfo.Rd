\name{h2o.clusterInfo}
\alias{h2o.clusterInfo}
%- Also NEED an '\alias' for EACH other topic documented here.
\title{
Get Information on H2O Cluster
}
\description{
Display the name, version, uptime, total nodes, total memory, total cores and health of a cluster running H2O.
}
\usage{
h2o.clusterInfo(client)
}
%- maybe also 'usage' for other objects documented here.
\arguments{
  \item{client}{An \code{\linkS4class{H2OClient}} object containing the IP address and port of the server running H2O.
}
}
\seealso{
\code{\linkS4class{H2OClient}}
}
\examples{
library(h2o)
localH2O = h2o.init()
h2o.clusterInfo(localH2O)
}
