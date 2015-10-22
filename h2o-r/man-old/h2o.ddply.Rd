\name{h2o.ddply}
\alias{h2o.ddply}
\alias{h2o..}
%- Also NEED an '\alias' for EACH other topic documented here.
\title{
Split H2O dataset, apply function, and return results
}
\description{
For each subset of a H2O dataset, apply a user-specified function, then combine the results.
}
\usage{
h2o.ddply(.data, .variables, .fun = NULL, ..., .progress = "none")
}
%- maybe also 'usage' for other objects documented here.
\arguments{
  \item{.data}{
An \code{\linkS4class{H2OParsedData}} object to be processed.
}
  \item{.variables}{
Variables to split \code{.data} by, either the indices or names of a set of columns.
}
  \item{.fun}{
Function to apply to each subset grouping. Must have been pushed to H2O using \code{\link{h2o.addFunction}}.
}
  \item{\dots}{
Additional arguments passed on to \code{.fun}. (Currently unimplemented).
}
  \item{.progress}{
Name of the progress bar to use. (Currently unimplemented).
}
}
\details{
This is an extension of the plyr library's ddply function to datasets loaded into H2O.
}
\value{
%%  ~Describe the value returned
%%  If it is a LIST, use
%%  \item{comp1 }{Description of 'comp1'}
%%  \item{comp2 }{Description of 'comp2'}
%% ...
An \code{\linkS4class{H2OParsedData}} object containing the results from the split/apply operation, arranged row-by-row.
}
\references{
Hadley Wickham (2011). The Split-Apply-Combine Strategy for Data Analysis. Journal of Statistical Software, 40(1), 1-29. \url{http://www.jstatsoft.org/v40/i01/}.
}
\seealso{
\code{\link{h2o.addFunction}}
}
\examples{
library(h2o)
localH2O = h2o.init()

# Import iris dataset to H2O
irisPath = system.file("extdata", "iris_wheader.csv", package = "h2o")
iris.hex = h2o.importFile(localH2O, path = irisPath, key = "iris.hex")

# Add function taking mean of sepal_len column
fun = function(df) { sum(df[,1], na.rm = T)/nrow(df) }
h2o.addFunction(localH2O, fun)

# Apply function to groups by class of flower
# uses h2o's ddply, since iris.hex is an H2OParsedData object
res = h2o.ddply(iris.hex, "class", fun)
head(res)
}
