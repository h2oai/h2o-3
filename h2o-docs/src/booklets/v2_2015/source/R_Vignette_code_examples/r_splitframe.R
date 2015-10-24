# Splits data in prostate data frame with a ratio of 0.75
prostate.split <- h2o.splitFrame(data = prostate.hex , ratios = 0.75)
# Creates training set from 1st data set in split
prostate.train <- prostate.split[[1]]
# Creates testing set from 2st data set in split
prostate.test <- prostate.split[[2]]