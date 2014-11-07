\name{h2o.createFrame}
\alias{h2o.createFrame}

\title{
Create an H2O Frame
}
\description{
Create an H2O data frame from scratch, with optional randomization. Supports categoricals, integers, reals and missing values.
}
\usage{
h2o.createFrame(object, key, rows, cols, seed, randomize, value, real_range, 
  categorical_fraction, factors, integer_fraction, integer_range, 
  missing_fraction, response_factors)
}
%- maybe also 'usage' for other objects documented here.
\arguments{
  \item{object}{An \code{\linkS4class{H2OClient}} object containing the IP address and port of the server running H2O.}
  \item{key}{
  The unique hex key assigned to the created frame.}
  \item{rows}{Number of rows}
  \item{cols}{Number of columns}
  \item{seed}{Random number seed}
  \item{randomize}{Whether frame should be randomized}
  \item{value}{Constant value (for randomize=false)}
  \item{real_range}{Range for real variables (-range ... range)}
  \item{categorical_fraction}{Fraction of categorical columns (for randomize=true)}
  \item{factors}{Factor levels for categorical variables}
  \item{integer_fraction}{Fraction of integer columns (for randomize=true)}
  \item{integer_range}{Range for integer variables (-range ... range)}
  \item{missing_fraction}{Fraction of missing values}
  \item{response_factors}{Number of factor levels of the first column (1=real, 2=binomial, N=multinomial)}
}
\value{
Returns an H2O data frame.
}
\examples{
library(h2o)
localH2O = h2o.init(beta = TRUE)
myframe = h2o.createFrame(localH2O, 'myframekey', rows = 1000, cols = 10, 
                          seed = -12301283, randomize = TRUE, value = 0, real_range = 2.0,
                          categorical_fraction = 0.2, factors = 100, 
                          integer_fraction = 0.2, integer_range = 100, missing_fraction = 0.1, 
                          response_factors = 2)
head(myframe)
summary(myframe)
h2o.shutdown(localH2O)
}
