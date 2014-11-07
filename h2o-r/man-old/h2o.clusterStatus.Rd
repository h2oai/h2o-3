\name{h2o.clusterStatus}
\alias{h2o.clusterStatus}
%- Also NEED an '\alias' for EACH other topic documented here.
\title{
Retrieve Status of H2O Cluster
}
\description{
Retrieve information on the status of the cluster running H2O.
}
\usage{
h2o.clusterStatus(client)
}
%- maybe also 'usage' for other objects documented here.
\arguments{
  \item{client}{
    An \code{\linkS4class{H2OClient}} object containing the IP address and port of the server running H2O.
  }
}
\details{
This method prints the status of the H2O cluster represented by \code{client}, consisting of the following information:
  \itemize{
    \item{\code{Version}: }{The version of H2O running on the cluster.}
    \item{\code{Cloud Name}: }{Name of the cluster.}
    \item{\code{Node Name}: }{Name of the node. (Defaults to the HTTP address).}
    \item{\code{Cloud Size}: }{Number of nodes in the cluster.}
    }
Furthermore, for each node, this function displays:
  \itemize{
    \item{\code{name}: }{Name of the node.}
    \item{\code{value_size_bytes}: }{Amount of data stored on the node.}
    \item{\code{free_mem_bytes}: }{Amount of free memory on the JVM.}
    \item{\code{max_mem_bytes}: }{Maximum amount of memory that the JVM will attempt to use.}
    \item{\code{free_disk_bytes}: }{Amount of free space on the disk that launched H2O.}
    \item{\code{max_disk_bytes}: }{Size of disk that launched H2O.}
    \item{\code{num_cpus}: }{Number of CPUs reported by JVM.}
    \item{\code{system_load}: }{Average system load.}
    \item{\code{rpcs}: }{Number of remote procedure calls.}
    \item{\code{last_contact}: }{Number of seconds since last heartbeat.}
    }
}

%% ~Make other sections like Warning with \section{Warning }{....} ~

\seealso{
\code{\linkS4class{H2OClient}}, \code{\link{h2o.init}}
}

\examples{
library(h2o)
localH2O = h2o.init()
h2o.clusterStatus(localH2O)
}
