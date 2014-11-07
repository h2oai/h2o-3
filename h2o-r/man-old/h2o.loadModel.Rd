\name{h2o.loadModel}
\alias{h2o.loadModel}
\title{Load a H2O Model.}
\description{Load a H2OModel object from disk that was saved using h2o.saveModel.}
\usage{h2o.loadModel(object, path = "")}

\arguments{
  \item{object}{An \code{\linkS4class{H2OClient}} object containing the IP address and port of the server running H2O.}
  \item{path}{The path of the H2O model file to be imported.}
  }
 
\value{
Returns a \linkS4class{H2OModel} object of the class corresponding to the type of model built. Ex: A saved model built using GLM will return a \linkS4class{H2OGLMModel} object.
}
\seealso{
\code{\link{h2o.saveModel},\link{h2o.saveAll}, \link{h2o.loadAll}, \linkS4class{H2OModel}}
}
\examples{
\dontrun{
library(h2o)
localH2O = h2o.init()
prosPath = system.file("extdata", "prostate.csv", package = "h2o")
prostate.hex = h2o.importFile(localH2O, path = prosPath, key = "prostate.hex")
prostate.glm = h2o.glm(y = "CAPSULE", x = c("AGE","RACE","PSA","DCAPS"), 
  data = prostate.hex, family = "binomial", nfolds = 10, alpha = 0.5)
glmmodel.path = h2o.saveModel(object = prostate.glm, dir = "/Users/UserName/Desktop")
glmmodel.load = h2o.loadModel(localH2O, glmmodel.path)
}
}
