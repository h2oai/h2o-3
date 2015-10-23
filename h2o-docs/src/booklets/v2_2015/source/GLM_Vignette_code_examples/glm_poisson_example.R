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
