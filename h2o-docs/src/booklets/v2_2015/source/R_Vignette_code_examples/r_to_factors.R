# Import prostate data
prosPath <- system.file("extdata", "prostate.csv", package="h2o")
prostate.hex <- h2o.importFile(path = prosPath)

# Converts column 4 (RACE) to an enum
is.factor(prostate.hex[,4])

prostate.hex[,4] <- as.factor(prostate.hex[,4])
is.factor(prostate.hex[,4])

# Summary will return a count of the factors
summary(prostate.hex[,4])