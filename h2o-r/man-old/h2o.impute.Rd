\name{h2o.impute}
\alias{h2o.impute}
\title{Impute A Column of Data}
\description{ Impute a column of data using the mean, median, or mode. Optionally impute based on groupings of additional columns.}
\usage{h2o.impute(data, column, method, groupBy)}
\arguments{
  \item{data}{An \code{\linkS4class{H2OParsedData} object}}
  \item{column}{The column to be imputed. Must be a single column, but may be an index, the column name, or a quoted column.}
  \item{method}{The method describing how to impute the column, one of "mean", "median", or "mode". If the column is a factor,
                then "mode" is forced by H2O.}
  \item{groupBy}{ If `groupBy` is not NULL, then the missing values are imputed using the mean/median/mode of
                  `column` within the groups formed by the groupBy columns.}
  }
\value{No return value, but the \code{\linkS4class{H2OParsedData} object is imputed in place.}
}

\examples{

library(h2o)
localH2O = h2o.init()

# randomly repalce 50 rows in each column of the iris dataset with NA
ds <- iris
ds[sample(nrow(ds), 50),1] <- NA
ds[sample(nrow(ds), 50),2] <- NA
ds[sample(nrow(ds), 50),3] <- NA
ds[sample(nrow(ds), 50),4] <- NA
ds[sample(nrow(ds), 50),5] <- NA

# upload the NA'ed dataset to H2O
hex <- as.h2o(localH2O, ds)
head(hex)

# impute the numeric column in place with "median"
h2o.impute(hex, .(Sepal.Length), method = "median")

# impute with the mean based on the groupBy columns Sepal.Length and Petal.Width and Species
h2o.impute(hex, 2, method = "mean", groupBy = .(Sepal.Length, Petal.Width, Species))

# impute the Species column with the "mode" based on the columns 1 and 4
h2o.impute(hex, 5, method = "mode", groupBy = c(1,4))
}
