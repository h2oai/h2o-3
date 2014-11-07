\name{h2o.interaction}
\alias{h2o.interaction}

\title{
Create interaction terms between categorical features of an H2O Frame
}
\description{
Create N-th order interaction terms between categorical features of an H2O Frame, N=0,1,2,3,...
}
\usage{
h2o.interaction(data, key=NULL, factors, pairwise, max_factors, min_occurrence)
}
%- maybe also 'usage' for other objects documented here.
\arguments{
  \item{data}{ An \code{\linkS4class{H2OParsedData}} object containing the variables in the model. }
  \item{key}{The unique hex key assigned to the created frame which has one extra column appended.}
  \item{factors}{Factor columns for which interactions are to be computed. If only one is specified, this can be used to reduce the factor levels.}
  \item{pairwise}{Whether to create pairwise quadratic interactions between factors (otherwise create one higher-order interaction). Only applicable if there are 3 or more factors.}
  \item{max_factors}{Max. number of factor levels in pair-wise interaction terms (if enforced, one extra catch-all factor will be made).}
  \item{min_occurrence}{Min. occurrence threshold for factor levels in pair-wise interaction terms.}
}
\value{
Returns an H2O data frame.
}
\examples{
library(h2o)
localH2O = h2o.init()

# Create some random data
myframe = h2o.createFrame(localH2O, 'framekey', rows = 20, cols = 5,
                          seed = -12301283, randomize = TRUE, value = 0,
                          categorical_fraction = 0.8, factors = 10, real_range = 1,
                          integer_fraction = 0.2, integer_range = 10, missing_fraction = 0.2,
                          response_factors = 1)[,-1]
myframe[,3] <- as.factor(myframe[,3])
summary(myframe)
head(myframe, 20)

# Create pairwise interactions
pairwise <- h2o.interaction(myframe, key = 'pairwise', factors = list(c(1,2),c(2,3,4)),
                            pairwise=TRUE, max_factors = 10, min_occurrence = 1)
head(pairwise, 20)
levels(pairwise[,2])

# Create 5-th order interaction
higherorder <- h2o.interaction(myframe, key = 'higherorder', factors = c(1,2,3,4,5),
                               pairwise=FALSE, max_factors = 10000, min_occurrence = 1)
head(higherorder, 20)

# Create a categorical variable out of integer column via self-interaction,
# and keep at most 3 factors, and only if they occur at least twice
summary(myframe[,3])
head(myframe[,3], 20)
trim_integer_levels <- h2o.interaction(myframe, key = 'trim_integers', factors = c(3),
                                       pairwise = FALSE, max_factors = 3, min_occurrence = 2)
head(trim_integer_levels, 20)

# Put all together and clean up temporaries
myframe <- cbind(myframe, pairwise, higherorder, trim_integer_levels)
myframe <- h2o.assign(myframe, 'final.key')
h2o.rm(localH2O, grep(pattern = "Last.value", x = h2o.ls(localH2O)$Key, value = TRUE))
myframe
head(myframe,20)
summary(myframe)
h2o.shutdown(localH2O)
}
