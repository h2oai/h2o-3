library(h2o)
h2o.init()
library(HDtweedie)
data(auto) # 2812 policy samples with 56 predictors

dim(auto$x)
hist(auto$y)

# Copy the R data.frame to an H2OFrame using as.h2o()
h2o_df = as.h2o(auto)
vars= paste("x.",colnames(auto$x),sep="")
tweedie.fit = h2o.glm(y = "y", x = vars, training_frame = h2o_df, family = "tweedie")