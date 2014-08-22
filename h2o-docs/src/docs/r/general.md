# Other Useful Functions

## List all H2O Objects

Used to generate a list of all H2O objects that have been generated
during a work session, along with each objects byte size.

```r
prostate.hex = h2o.importFile(localH2O, path = prosPath, key = "prostate.hex")
s = runif(nrow(prostate.hex))
prostate.train = prostate.hex[s <= 0.8,]
prostate.train = h2o.assign(prostate.train, "prostate.train")
h2o.ls(localH2O)
```

## Remove an H2O object from the server where H2O is running

Users may wish to remove an H2O object on the server that is
associated with an object in the R environment. Recommended behavior
is to also remove the object in the R environment.

```r
localH2O = h2o.init()
prosPath = system.file("extdata", "prostate.csv", package="h2o")
prostate.hex = h2o.importFile(localH2O, path = prosPath, key = "prostate.hex")
s = runif(nrow(prostate.hex))
prostate.train = prostate.hex[s <= 0.8,]
prostate.train = h2o.assign(prostate.train, "prostate.train")
h2o.ls(localH2O)
h2o.rm(object= localH2O, keys= "Last.value.0")
h2o.ls(localH2O)
```




