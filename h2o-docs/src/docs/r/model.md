# Running Models

## GBM

Gradient Boosted Models. For information on the GBM algorithm see [GBM](../model/gbm).

```r
ausPath = system.file("extdata", "australia.csv", package="h2o")
australia.hex = h2o.importFile(localH2O, path = ausPath)
independent <- c("premax", "salmax","minairtemp", "maxairtemp",
"maxsst", "maxsoilmoist", "Max_czcs")
dependent <- "runoffnew"
h2o.gbm(y = dependent, x = independent, data = australia.hex,
n.trees = 10, interaction.depth = 3,
   n.minobsinnode = 2, shrinkage = 0.2, distribution= "gaussian")
```

*Run multinomial classification GBM on abalone data*

```r
h2o.gbm(y = dependent, x = independent, data = australia.hex, n.trees
= 15, interaction.depth = 5,
 n.minobsinnode = 2, shrinkage = 0.01, distribution= "multinomial")
```


## GLM

Generalized linear models, which are used to develop linear models
for exponential distributions. Regularization can be applied. For
information on the GBM algorithm see [GLM](../model/glm).


```r
prostate.hex = h2o.importURL.VA(localH2O, path =
"https://raw.github.com/0xdata/h2o/master/smalldata/logreg/prostate.csv",
key = "prostate.hex")
h2o.glm(y = "CAPSULE", x = c("AGE","RACE","PSA","DCAPS"), data =
prostate.hex, family = "binomial", nfolds = 10, alpha = 0.5)

myX = setdiff(colnames(prostate.hex), c("ID", "DPROS", "DCAPS", "VOL"))
h2o.glm(y = "VOL", x = myX, data = prostate.hex, family = "gaussian", nfolds = 5, alpha = 0.1)
```


## K-Means

K means is a clustering algorithm that allows users to characterize
data. This algorithm does not rely on a dependent variable. For
information on the K-Means algorithm see [K-Means](../model/kmeans)


```r
prosPath = system.file("extdata", "prostate.csv", package="h2o")
prostate.hex = h2o.importFile(localH2O, path = prosPath)
h2o.kmeans(data = prostate.hex, centers = 10, cols = c("AGE", "RACE", "VOL", "GLEASON"))
covPath = system.file("extdata", "covtype.csv", package="h2o")
covtype.hex = h2o.importFile(localH2O, path = covPath)
covtype.km = h2o.kmeans(data = covtype.hex, centers = 5, cols = c(1, 2, 3))
print(covtype.km)
```


## Principal Components Analysis

Principal Components Analysis maps a set of variables onto a
subspace via linear transformations. PCA is the first step in
Principal Components Regression. For more information on PCA
see [PCA](../model/pca).

```r
ausPath = system.file("extdata", "australia.csv", package="h2o")
australia.hex = h2o.importFile(localH2O, path = ausPath)
australia.pca = h2o.prcomp(data = australia.hex, standardize = TRUE)
print(australia.pca)
summary(australia.pca)
```


## Principal Components Regression

PCR is an algorithm that allows users to map a set of variables to a
new set of linearly independent variables. The new set of variables
are linearly independent linear combinations of the original
variables and exist in a subspace of lower dimension. This
transformation is then prepended to a regression model, often
improving results. For more information on PCA, see [PCA](../model/pca).

```r
prostate.hex = h2o.importFile(localH2O,
path =
"https://raw.github.com/0xdata/h2o/master/smalldata/logreg/prostate.csv",
key = "prostate.hex")
h2o.pcr(x = c("AGE","RACE","PSA","DCAPS"), y = "CAPSULE", data =
prostate.hex, family = "binomial",
nfolds = 10, alpha = 0.5, ncomp = 3)
```



