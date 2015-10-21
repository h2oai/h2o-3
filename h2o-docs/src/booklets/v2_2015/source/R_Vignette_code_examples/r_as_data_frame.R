# Creates object that defines path
prosPath <- system.file("extdata", "prostate.csv", package="h2o")
# Imports data set
prostate.hex = h2o.importFile(path = prosPath, destination_frame="prostate.hex")

# Converts current data frame (prostate data set) to an R data frame
prostate.R <- as.data.frame(prostate.hex)
# Displays a summary of data frame where the summary was executed in R
summary(prostate.R)
