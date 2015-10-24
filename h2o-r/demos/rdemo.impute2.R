library(h2o)
h2o.init()

# a useful function to make a quick copy of a data frame in H2O
cp <- function(this) this[1:nrow(this), 1:ncol(this)]

# a useful function to count number of NAs in a column
numNAs <- function(col) sum(is.na(col))

prostate.hex <- h2o.uploadFile(h2o:::.h2o.locate("smalldata/logreg/prostate_missing.csv"), "prostate.hex")
dim(prostate.hex)

print("Summary of the data in iris_missing.csv")
print("Each column has 50 missing observations (at random)")
summary(prostate.hex)


print("Make a copy of the original dataset to play with.")
hex <- cp(prostate.hex)
print(prostate.hex)
print(hex)


print("Impute a numeric column with the mean")
nas <- numNAs(hex[,"DPROS"])
print(paste("NAs before imputation:", nas))
h2o.impute(hex, "DPROS", method = "mean")

nas <- numNAs(hex[,"DPROS"])
print(paste("NAs after imputation: ", nas))



# OTHER POSSIBLE SYNTAXES ALLOWED:
hex <- cp(prostate.hex)
h2o.impute(hex, 8, method = "mean")

hex <- cp(prostate.hex)
h2o.impute(hex, c("VOL"), method = "mean")

hex <- cp(prostate.hex)
h2o.impute(hex, "VOL", method = "mean")

# USING  MEDIAN
print("Impute a numeric column with the median")

hex <- cp(prostate.hex)
h2o.impute(hex, "VOL", method = "median")

hex <- cp(prostate.hex)
h2o.impute(hex, 8, method = "median")

hex <- cp(prostate.hex)
h2o.impute(hex, c("VOL"), method = "median")

hex <- cp(prostate.hex)
h2o.impute(hex, "VOL", method = "median")