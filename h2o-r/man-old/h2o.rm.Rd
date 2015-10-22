\name{h2o.rm}
\alias{h2o.rm}
\title{Removes H2O objects from the server where H2O is running.}
\description{Allows users to remove H2O objects from the server where the instance of H2O is running. This call acts on the H2O server through the R console, and does NOT remove the associated named object from the R environment. 
}
\usage{
h2o.rm(object, keys)
}
\arguments{
  \item{object}{An \code{\linkS4class{H2OClient}} object containing the IP address and port of the server running H2O.}
  \item{keys}{the hex key associated with the object to be removed.}
}
\note{Users may wish to remove an H2O object on the server that is associated with an object in the R environment. Recommended behavior is to also remove the object in the R environment. See the second example at the end of this section.}

%% ~Make other sections like Warning with \section{Warning }{....} ~

\seealso{
%% ~~objects to See Also as \code{\link{help}}, ~~~
\code{\link{h2o.assign}}, \code{\link{h2o.ls}}
}
\examples{
# Remove an H2O object from the server where H2O is running.
library(h2o)
localH2O = h2o.init()
prosPath = system.file("extdata", "prostate.csv", package="h2o")
prostate.hex = h2o.importFile(localH2O, path = prosPath, key = "prostate.hex")

# Remove an H2O object from the server and from the R environment
h2o.ls(localH2O)
h2o.rm(object = localH2O, keys = "prostate.hex")
remove(prostate.hex)
h2o.ls(localH2O)
}

