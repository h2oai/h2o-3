\name{h2o.exec}
\alias{h2o.exec}
%- Also NEED an '\alias' for EACH other topic documented here.
\title{
Execute a Command on H2O
}
\description{
Directly send over and execute an R expression in the H2O console.
}
\usage{
h2o.exec(expr_to_execute, h2o, dest_key)
}
%- maybe also 'usage' for other objects documented here.
\arguments{
  \item{expr_to_execute}{An R expression that is supported by H2O. Currently, basic subsetting, arithmetic operations, logical filters, and simple expressions like \code{dim} are allowed.}
  \item{h2o}{(Optional)Point to an instance of H2O. If not given, h2o.exec will try to guess.}
  \item{dest_key}{(Optional)Give a destination key to the expression to be executed. If not given, h2o.exec will try to guess.}
}
\value{
A \code{\linkS4class{H2OParsedData}} object containing the result of the expression.
}
\examples{
library(h2o)
localH2O = h2o.init()
hex <- as.h2o(localH2O, iris)
res1 = h2o.exec(hex[,1] + hex[,2])
head(res1)
res2 = h2o.exec(hex[,1] + hex[, 2] + hex[, 3] * hex[,4] / hex[,1])
head(res2)
res3<- h2o.exec(hex$nc<- ifelse(hex[,1]<5,log(hex[,3]+1),hex[,"Petal.Width"]/hex$Sepal.Width))
head(res3)
head(hex)
}
