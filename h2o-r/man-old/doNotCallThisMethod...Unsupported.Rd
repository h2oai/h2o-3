\name{doNotCallThisMethod...Unsupported}
\alias{doNotCallThisMethod...Unsupported}
%- Also NEED an '\alias' for EACH other topic documented here.
\title{
Internal method used for testing at the API level.
}
\description{
Fetches all of the model JSON for the model key passed in.
}
\usage{
doNotCallThisMethod...Unsupported(h2o, key)
}
\arguments{
  \item{h2o}{An h2o object returned from h2o.init() that represents the connection to the h2o cloud. 
  }
  \item{key}{Any valid model key that exists in the h2o cluster.
  }
}

\value{ A blob of JSON.
}

\examples{
library(h2o)
localH2O = h2o.init()
hex <- as.h2o(localH2O, iris)
m <- h2o.randomForest(x = 1:4, y = 5, data = hex)
doNotCallThisMethod...Unsupported(localH2O, m@key)
}
