prosPath <- system.file("extdata", "prostate.csv", package="h2o")
prostate.hex <- h2o.importFile(path = prosPath)
# Returns the percentiles at 0, 10, 20, ..., 100%
prostate.qs <- quantile(prostate.hex$PSA, probs = (1:10)/10)
prostate.qs

# Take the outliers or the bottom and top 10% of data
PSA.outliers <- prostate.hex[prostate.hex$PSA <= prostate.qs["10%"] | prostate.hex$PSA >=   prostate.qs["90%"],]
# Check that the number of rows return is about 20% of the original data
nrow(prostate.hex)

nrow(PSA.outliers)

nrow(PSA.outliers)/nrow(prostate.hex)
