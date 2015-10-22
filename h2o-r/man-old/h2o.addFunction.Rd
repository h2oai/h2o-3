\name{h2o.addFunction}
\alias{h2o.addFunction}
%- Also NEED an '\alias' for EACH other topic documented here.
\title{
Adds an R function to H2O
}
\description{
Add a function defined in R to the H2O server, so it is recognized for future operations on H2O. This method is necessary because R functions are not automatically pushed across via the REST API to H2O.
}
\usage{
h2o.addFunction(object, fun, name)
}
%- maybe also 'usage' for other objects documented here.
\arguments{
  \item{object}{
An \code{\linkS4class{H2OClient}} object containing the IP address and port of the server running H2O.
}
  \item{fun}{
A function in R. Currently, only a subset of the R syntax is recognizable by H2O, and functions that fall outside this set will be rejected. Values referred to by \code{fun} must be defined within H2O, e.g. a H2O dataset must be referred to by its key name, not its H2OParsedData R variable name.
}
  \item{name}{
(Optional) A character string giving the name that the function should be saved under in H2O. If missing, defaults to the name that the function is saved under in R.
}
}
\details{
%%  ~~ If necessary, more details than the description above ~~
This method is intended to be used in conjunction with \code{\link{h2o.ddply}}. The user must explicitly add the function he or she wishes to apply to H2O. Otherwise, the server will not recognize a function reference that only exists in R.
}
\seealso{
%% ~~objects to See Also as \code{\link{help}}, ~~~
\code{\link{h2o.ddply}}
}
\examples{
library(h2o)
localH2O = h2o.init()
h2o.addFunction(localH2O, function(x) { 2*x + 5 }, "simpleFun")
}
