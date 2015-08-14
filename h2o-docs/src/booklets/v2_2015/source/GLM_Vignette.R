
h2o.init()

#---------------------------------------------------------------------
#---------------------------------------------------------------------

h2o.removeAll()
rm(list=ls())

library(h2o)
h2o.init()
path = system.file("extdata", "prostate.csv", package = "h2o")
h2o_df = h2o.importFile(path)
gaussian.fit = h2o.glm(y = "VOL", x = c("AGE", "RACE", "PSA", "GLEASON"), training_frame = h2o_df, family = "gaussian")

#---------------------------------------------------------------------
#---------------------------------------------------------------------

h2o.removeAll()
rm(list=ls())

library(h2o)
h2o.init()
path = system.file("extdata", "prostate.csv", package = "h2o")
h2o_df = h2o.importFile(path)
is.factor(h2o_df$CAPSULE)
h2o_df$CAPSULE = as.factor(h2o_df$CAPSULE)
is.factor(h2o_df$CAPSULE)
binomial.fit = h2o.glm(y = "CAPSULE", x = c("AGE", "RACE", "PSA", "GLEASON"), training_frame = h2o_df, family = "binomial")

library(h2o)
h2o.init()
library(MASS)
data(Insurance)

# Convert ordered factors into unordered factors.
# H2O only handles unordered factors today.
class(Insurance$Group) <- "factor"
class(Insurance$Age) <- "factor"

# Copy the R data.frame to an H2OFrame using as.h2o()
h2o_df = as.h2o(Insurance)
poisson.fit = h2o.glm(y = "Claims", x = c("District", "Group", "Age"), training_frame = h2o_df, family = "poisson")

#---------------------------------------------------------------------
#---------------------------------------------------------------------

h2o.removeAll()
rm(list=ls())

library(h2o)
h2o.init()
path = system.file("extdata", "prostate.csv", package = "h2o")
h2o_df = h2o.importFile(path)
gamma.inverse <- h2o.glm(y = "DPROS", x = c("AGE","RACE","CAPSULE","DCAPS","PSA","VOL"), training_frame = h2o_df, family = "gamma", link = "inverse")
gamma.log <- h2o.glm(y="DPROS", x = c("AGE","RACE","CAPSULE","DCAPS","PSA","VOL"), training_frame = h2o_df, family = "gamma", link = "log")

#---------------------------------------------------------------------
#---------------------------------------------------------------------

h2o.removeAll()
rm(list=ls())

library(h2o)
h2o.init()
path = system.file("extdata", "prostate.csv", package = "h2o")
h2o_df = h2o.importFile(path)
h2o_df$CAPSULE = as.factor(h2o_df$CAPSULE)
summary(h2o_df)

#---------------------------------------------------------------------
#---------------------------------------------------------------------

h2o.removeAll()
rm(list=ls())

library(h2o)
h2o.init()
h2o_df = h2o.importFile("http://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
model = h2o.glm(y = "IsDepDelayed", x = c("Year", "Origin"), training_frame = h2o_df, family = "binomial", lambda_search = TRUE, max_active_predictors = 10)
print(model)
v1 = model@model$coefficients
v2 = v1[v1 > 0]
print(v2)

#---------------------------------------------------------------------
#---------------------------------------------------------------------

h2o.removeAll()
rm(list=ls())

library(h2o)
h2o.init()
path = system.file("extdata", "prostate.csv", package = "h2o")
h2o_df = h2o.importFile(path)
h2o_df$CAPSULE = as.factor(h2o_df$CAPSULE)
rand_vec <- h2o.runif(h2o_df, seed = 1234)
train <- h2o_df[rand_vec <= 0.8,]
valid <- h2o_df[rand_vec > 0.8,]
binomial.fit = h2o.glm(y = "CAPSULE", x = c("AGE", "RACE", "PSA", "GLEASON"), training_frame = train, validation_frame = valid, family = "binomial")
print(binomial.fit)
# Optionally rename the train and valid H2OFrames on the server side.
# train <- h2o.assign(train, "train")
# valid <- h2o.assign(valid, "valid")

binomial.fit@model$coefficients
binomial.fit@model$coefficients_table
binomial.fit@model$standardized_coefficient_magnitudes
# binomial.fit@model$standardized_coefficients_magnitude

h2o.num_iterations(binomial.fit)
h2o.null_dof(binomial.fit, train = TRUE, valid = TRUE)
h2o.residual_dof(binomial.fit, train = TRUE, valid = TRUE)

h2o.mse(binomial.fit, train = TRUE, valid = TRUE)
h2o.r2(binomial.fit, train = TRUE, valid = TRUE)
h2o.logloss(binomial.fit, train = TRUE, valid = TRUE)
h2o.auc(binomial.fit, train = TRUE, valid = TRUE)
h2o.giniCoef(binomial.fit, train = TRUE, valid = TRUE)
h2o.null_deviance(binomial.fit, train = TRUE, valid = TRUE)
h2o.residual_deviance(binomial.fit, train = TRUE, valid = TRUE)
h2o.aic(binomial.fit, train = TRUE, valid = TRUE)

h2o.confusionMatrix(binomial.fit, valid = FALSE)
h2o.confusionMatrix(binomial.fit, valid = TRUE)

binomial.fit@model$scoring_history

#---------------------------------------------------------------------
#---------------------------------------------------------------------

h2o.removeAll()
rm(list=ls())

library(h2o)
h2o.init()
path = system.file("extdata", "prostate.csv", package = "h2o")
h2o_df = h2o.importFile(path)
h2o_df$CAPSULE = as.factor(h2o_df$CAPSULE)
binomial.fit = h2o.glm(y = "CAPSULE", x = c("AGE", "RACE", "PSA", "GLEASON"), training_frame = h2o_df, family = "binomial")
h2o.download_pojo(binomial.fit)

#---------------------------------------------------------------------
#---------------------------------------------------------------------

h2o.removeAll()
rm(list=ls())

library(h2o)
h2o.init()
path = system.file("extdata", "prostate.csv", package = "h2o")
h2o_df = h2o.importFile(path)
h2o_df$CAPSULE = as.factor(h2o_df$CAPSULE)
rand_vec <- h2o.runif(h2o_df, seed = 1234)
train <- h2o_df[rand_vec <= 0.8,]
valid <- h2o_df[(rand_vec > 0.8) & (rand_vec <= 0.9),]
test <- h2o_df[rand_vec > 0.9,]
binomial.fit = h2o.glm(y = "CAPSULE", x = c("AGE", "RACE", "PSA", "GLEASON"), training_frame = train, validation_frame = valid, family = "binomial")

# Make and export predictions.
pred = h2o.predict(binomial.fit, test)
head(pred)
h2o.exportFile(pred, "/tmp/pred.csv", force = TRUE)
# Or you can export the predictions to hdfs:  h2o.exportFile(pred, "hdfs://namenode/path/to/file.csv")

perf = h2o.performance(binomial.fit, test)
print(perf)

# Remove the response column to simulate new data points arriving without the answer being known.
newdata = test
newdata$CAPSULE <- NULL

newpred = h2o.predict(binomial.fit, newdata)
head(newpred)
newpred$predict = newpred$p1 > 0.3
head(newpred)
