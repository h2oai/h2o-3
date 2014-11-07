\name{h2o.shutdown}
\alias{h2o.shutdown}
%- Also NEED an '\alias' for EACH other topic documented here.
\title{
Shutdown H2O server
}
\description{
Shuts down the specified H2O instance. All data on the server will be lost!
}
\usage{
h2o.shutdown(client, prompt = TRUE)
}
%- maybe also 'usage' for other objects documented here.
\arguments{
  \item{client}{
    An \code{\linkS4class{H2OClient}} client containing the IP address and port of the server running H2O.
  }
  \item{prompt}{
    (Optional) A logical value indicating whether to prompt the user before shutting down the H2O server.
  }
}
\details{
This method checks if H2O is running at the specified IP address and port, and if it is, shuts down that H2O instance. WARNING: All data, models, and other values stored on the server will be lost! Only call this function if you and all other clients connected to the H2O server are finished and have saved your work.
}
\note{
Users must call h2o.shutdown explicitly in order to shut down the local H2O instance started by R. If R is closed before H2O, then an attempt will be made to automatically shut down H2O. This only applies to local instances started with h2o.init, not remote H2O servers.
}

%% ~Make other sections like Warning with \section{Warning }{....} ~

\seealso{
\code{\link{h2o.init}}
}

\examples{
# Don't run automatically to prevent accidentally shutting down a cloud
\dontrun{
library(h2o)
localH2O = h2o.init()
h2o.shutdown(localH2O)
}
}
