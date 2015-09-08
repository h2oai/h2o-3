\name{h2o-package}
\alias{h2o-package}
\alias{h2o}
\docType{package}
\title{
H2O R Interface
}
\description{
This is a package for running H2O via its REST API from within R. To communicate with a H2O instance, the version of the R package must match the version of H2O. When connecting to a new H2O cluster, it is necessary to re-run the initializer.
}
\details{
\tabular{ll}{
Package: \tab h2o\cr
Type: \tab Package\cr
Version: \tab 2.9.0.99999\cr
Date: \tab 2014-05-15\cr
License: \tab Apache License (== 2.0)\cr
Depends: \tab R (>= 2.13.0), RCurl, jsonlite, statmod, tools, methods, utils\cr
}

This package allows the user to run basic H2O commands using R commands. In order to use it, you must first have H2O running (See \href{http://docs.0xdata.com/newuser/quickstart_jar.html}{How to Start H2O}). To run H2O on your local machine, call \code{h2o.init} without any arguments, and H2O will be automatically launched on \url{http://127.0.0.1:54321}, where the IP is "127.0.0.1" and the port is 54321. If H2O is running on a cluster, you must provide the IP and port of the remote machine as arguments to the h2o.init() call. 

H2O supports a number of standard statistical models, such as GLM, K-means, and Random Forest classification. For example, to run GLM, call \code{\link{h2o.glm}} with the H2O parsed data and parameters (response variable, error distribution, etc...) as arguments. (The operation will be done on the server associated with the data object where H2O is running, not within the R environment). 

Note that no actual data is stored in the R workspace; and no actual work is carried out by R. R only saves the named objects, which uniquely identify the data set, model, etc on the server. When the user makes a request, R queries the server via the REST API, which returns a JSON file with the relevant information that R then displays in the console.
}
\author{
Anqi Fu, Tom Kraljevic and Petr Maj, with contributions from the 0xdata team

Maintainer: Ariel Rao <ariel@0xdata.com>
}
\references{
\itemize{
  \item \href{http://www.0xdata.com}{0xdata Homepage}
  \item \href{http://docs.0xdata.com}{H2O Documentation}
  \item \href{https://github.com/0xdata/h2o}{H2O on Github}
  }
}
\keyword{ package }
\examples{
# Check connection with H2O and ensure local H2O R package matches server version.
# Optionally, ask for startH2O to start H2O if it's not already running.
# Note that for startH2O to work, the IP must be 127.0.0.1 or localhost with port 54321.
library(h2o)
localH2O = h2o.init(ip = "127.0.0.1", port = 54321, startH2O = TRUE)

# Import iris dataset into H2O and print summary
irisPath = system.file("extdata", "iris.csv", package = "h2o")
iris.hex = h2o.importFile(localH2O, path = irisPath, key = "iris.hex")
summary(iris.hex)

# Attach H2O R package and run GLM demo
??h2o
demo(package = "h2o")
demo(h2o.prcomp)
}
