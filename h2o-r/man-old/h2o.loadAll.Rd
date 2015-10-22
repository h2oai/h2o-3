\name{h2o.loadAll}
\alias{h2o.loadAll}
\title{Load all H2O Model in a directory.}
\description{Load all H2OModel object in a directory from disk that was saved using h2o.saveModel or h2o.saveAll.}
\usage{h2o.loadAll(object, dir = "")}

\arguments{
  \item{object}{An \code{\linkS4class{H2OClient}} object containing the IP address and port of the server running H2O.}
  \item{dir}{The directory multiple H2O model files to be imported from.}
  }
 
\value{
Returns \linkS4class{H2OModel} objects of the class corresponding to the type of model built. Ex: A saved model built using GLM will return a \linkS4class{H2OGLMModel} object.
}
\seealso{
\code{\link{h2o.saveModel},\link{h2o.saveAll}, \link{h2o.loadModel}, \linkS4class{H2OModel}}
}
\examples{
\dontrun{
library(h2o)
localH2O = h2o.init()
prosPath = system.file("extdata", "prostate.csv", package = "h2o")
prostate.hex = h2o.importFile(localH2O, path = prosPath, key = "prostate.hex")
prostate.glm = h2o.glm(y = "CAPSULE", x = c("AGE","RACE","PSA","DCAPS"), 
  data = prostate.hex, family = "binomial", nfolds = 10, alpha = 0.5)
prostate.gbm = h2o.gbm(y = "CAPSULE", x = c("AGE","RACE","PSA","DCAPS"), n.trees=3,
  interaction.depth=1, distribution="multinomial", data = prostate.hex)
h2o.saveAll(object = localH2O, dir = "/Users/UserName/Desktop", save_cv = TRUE, force = TRUE)  
h2o.removeAll(object = conn)
model.load = h2o.loadModel(localH2O, dir = "/Users/UserName/Desktop")
prostate.glm = model.load[[1]]
prostate.gbm = model.load[[2]]

}
}
