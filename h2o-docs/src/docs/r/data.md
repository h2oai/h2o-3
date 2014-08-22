# Data Manipulation and Description
## Any Factor

Used to determine if any column in a data set is a factor.

```r
irisPath = system.file("extdata", "iris_wheader.csv", package="h2o")
iris.hex = h2o.importFile(localH2O, path = irisPath)
h2o.anyFactor(iris.hex)
```


## As Data Frame

Used to convert an H2O parsed data object into an R data frame
(which can subsequently be manipulated using R calls). While this is
frequently useful, as.data.frame should be used with care when
converting H2O Parsed Data objects. Data sets that are easily and
quickly handled by H2O are often too large to be treated
equivalently well in R.

```r
 prosPath <- system.file("extdata", "prostate.csv", package="h2o")
 prostate.hex = h2o.importFile(localH2O, path = prosPath)
 prostate.data.frame<- as.data.frame(prostate.hex)
 summary(prostate.data.frame)
 head(prostate.data.frame)
```



## As Factor

Used to convert an integer into a non-ordered factor (alternatively
called an enum or categorical).

```r
prosPath = system.file("extdata", "prostate.csv", package="h2o")
prostate.hex = h2o.importFile(localH2O, path = prosPath)
prostate.hex[,4] = as.factor(prostate.hex[,4])
summary(prostate.hex)
```

## As H2O

Used to pass a data frame from inside of the R environment to the H2O instance.

```r
data(iris)
summary(iris)
iris.r <- iris
iris.h2o <- as.h2o(localH2O, iris.r, key="iris.h2o")
class(iris.h2o)
```



## Assign H2O

Used to create an hex key on the server where H2O is running for data sets manipulated   in R.
For instance, in the example below, the prostate data set was
uploaded to the H2O instance, and was manipulated to remove
outliers. Saving the new data set on the H2O server so that it can
be subsequently be analyzed with H2O without overwriting the original
data set relies on h2o.assign.

```r
prosPath = system.file("extdata", "prostate.csv", package="h2o")
prostate.hex = h2o.importFile(localH2O, path = prosPath)
prostate.qs = quantile(prostate.hex$PSA)
PSA.outliers = prostate.hex[prostate.hex$PSA <= prostate.qs[2] | prostate.hex$PSA >=   prostate.qs[10],]
PSA.outliers = h2o.assign(PSA.outliers, "PSA.outliers")
nrow(prostate.hex)
nrow(PSA.outliers)
```

## Colnames

Used to obtain a list of the column names in a data set.

```r
irisPath = system.file("extdata", "iris.csv", package="h2o")
iris.hex = h2o.importFile(localH2O, path = irisPath, key = "iris.hex")
summary(iris.hex)
colnames(iris.hex)
```


## Extremes

Used to obtain the maximum and minimum values in real valued columns.

```r
ausPath = system.file("extdata", "australia.csv", package="h2o")
australia.hex = h2o.importFile(localH2O, path = ausPath, key = "australia.hex")
min(australia.hex)
min(c(-1, 0.5, 0.2), FALSE, australia.hex[,1:4])
```

## Quantiles

Used to request quantiles for an H2O parsed data set. When requested
for a full parsed data set quantiles() returns a matrix displaying
quantile information for all numeric columns in the data set.

```r
prosPath = system.file("extdata", "prostate.csv", package="h2o")
prostate.hex = h2o.importFile(localH2O, path = prosPath)
quantile(prostate.hex)
```


## Summary

Used to generate an R like summary for each of the columns of a data
set. For continuous reals this produces a summary that includes
information on quartiles, min, max and mean. For factors this
produces information on counts of elements within each factor
level. For information on the Summary algorithm see [Summary](../data/summary)

```r
 prosPath = system.file("extdata", "prostate.csv", package="h2o")
 prostate.hex = h2o.importFile(localH2O, path = prosPath)
 summary(prostate.hex)
 summary(prostate.hex$GLEASON)
 summary(prostate.hex[,4:6])
```

## H2O Table

Used to summarize information in data. Note that because H2O handles such large data sets,
it is possible for users to generate tables that are larger that R's
capacity. To minimize this risk and allow users to work uninterrupted,
h2o.table is called inside of a call for head() or tail(). Within
head() and tail() users can explicitly specify the number of rows in
the table to return.

```r
head(h2o.table(prostate.hex[,3]))
head(h2o.table(prostate.hex[,c(3,4)]))
```

## Test Train Split and generating Random Numbers

Runif is used to append a column of random numbers to an H2O data
frame and facilitate creating test/ train splits of data for
analysis and validation in H2O.

```r
prosPath = system.file("extdata", "prostate.csv", package="h2o")
prostate.hex = h2o.importFile(localH2O, path = prosPath, key = "prostate.hex")
s = h2o.runif(prostate.hex)
summary(s)

prostate.train = prostate.hex[s <= 0.8,]
prostate.train = h2o.assign(prostate.train, "prostate.train")
prostate.test = prostate.hex[s > 0.8,]
prostate.test = h2o.assign(prostate.test, "prostate.test")
nrow(prostate.train) + nrow(prostate.test)
```



