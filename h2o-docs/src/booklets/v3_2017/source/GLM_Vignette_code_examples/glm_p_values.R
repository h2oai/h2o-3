library(h2o)
h2o.init()
a = runif(100)
b = runif(100)
c = -3*a + 10 + 0.01*runif(100)
df = data.frame(a,b,c)
h2o_df = as.h2o(df)
h2o.fit = h2o.glm(y = "c", x = c("a", "b"), training_frame = h2o_df, lambda=0,remove_collinear_columns=TRUE,compute_p_values=TRUE)
h2o.fit