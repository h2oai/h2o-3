\name{h2o.gains}
\alias{h2o.gains}
\title{
Gains and Lift Charts
}
\description{
 Construct the gains table and lift charts for binary outcome algorithms.
 Lift charts and gains tables are commonly applied to marketing.
}
\usage{
h2o.gains(actual, predicted, groups=10, percents=FALSE)
}
\arguments{
  \item{actual}{
  An \code{\linkS4class{H2OParsedData}} object containing the predicted outcome scores. Must be a single column with the same number of rows as \code{reference}.
  }
  \item{predicted}{
  An \code{\linkS4class{H2OParsedData}} object containing the actual outcomes for comparison. Must be a single binary column with all entries in \{0,1\}.
  }
  \item{groups}{
  an integer containing the number of rows in the gains table. The default value is
  10.
  }
  \item{percents}{
  (Optional) a logical that indicates whether to return results as percentage values for the cumulative lift,
  }
}
\value{
  An R data.frame with columns Quantile, Response.Rate, Lift, Cumulative.Lift
  If percents is TRUE, then Quantile, Response.Rate, and Cumulative.Lift will be in percent form.
}
\examples{
  library(h2o)
  localH2O = h2o.init()

  # Run GBM classification on prostate.csv
  prosPath = system.file("extdata", "prostate.csv", package = "h2o")
  prostate.hex = h2o.importFile(localH2O, path = prosPath, key = "prostate.hex")
  prostate.gbm = h2o.gbm(y = 2, x = 3:9, data = prostate.hex)

  # Calculate performance measures at threshold that maximizes precision
  prostate.pred = h2o.predict(prostate.gbm)
  head(prostate.pred)
  h2o.gains(prostate.hex$CAPSULE, prostate.pred[,3], percents = TRUE)
}
