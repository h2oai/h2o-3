# Predict

Used to apply an H2O model to a holdout set to obtain predictions
based on model results.
In the examples below models are first generated, and then the
predictions for that model are obtained.

```r
prostate.hex = h2o.importURL.VA(localH2O,
path =
"https://raw.github.com/0xdata/h2o/master/smalldata/logreg/prostate.csv",
key = "prostate.hex")
prostate.glm = h2o.glm(y = "CAPSULE", x =
c("AGE","RACE","PSA","DCAPS"), data = prostate.hex,
family = "binomial", nfolds = 10, alpha = 0.5)
prostate.fit = h2o.predict(object = prostate.glm, newdata = prostate.hex)
summary(prostate.fit)

covPath = system.file("extdata", "covtype.csv", package="h2o")
covtype.hex = h2o.importFile(localH2O, path = covPath)
covtype.km = h2o.kmeans(data = covtype.hex, centers = 5, cols = c(1, 2, 3))
covtype.clusters = h2o.predict(object = covtype.km, newdata = covtype.hex)
```



