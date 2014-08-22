# Importing Data

## Import File

Use this call when importing a data set that exists in a single file.

```r
irisPath = system.file("extdata", "iris.csv", package="h2o")
iris.hex = h2o.importFile(localH2O, path = irisPath, key = "iris.hex")
summary(iris.hex)
```

## Import Folder

Use this call when importing a data set that exists in multiple files.

```r
myPath = system.file("extdata", "prostate_folder", package = "h2o")
prostate_all.hex = h2o.importFolder(localH2O, path = myPath)
class(prostate_all.hex)
summary(prostate_all.hex)
prostate_all.fv = h2o.importFolder(localH2O, path = myPath, version = 2)
```

## Import URL

Use this call when data are stored at a website accessible from the machine on which the
instance of H2O is running.

```r
prostate.hex = h2o.importURL(localH2O, path = paste("https://raw.github.com",
"0xdata/h2o/master/smalldata/logreg/prostate.csv", sep = "/"), key = "prostate.hex")
class(prostate.hex)
summary(prostate.hex)
```


