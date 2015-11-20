prosPath <- system.file("extdata", "prostate.csv", package="h2o")
prostate.hex<-h2o.importFile(path = prosPath)

## Assign a new name to prostate dataset in the KV store
h2o.ls()

prostate.hex <- h2o.assign(data = prostate.hex, key = "myNewName")
h2o.ls()
