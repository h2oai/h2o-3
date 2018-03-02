library(h2o)
h2o.init()
h2o_df = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/bigdata/laptop/glm_ordinal_logit/ordinal_multinomial_training_set.csv")
Dtrain$C11 <- h2o.asfactor(Dtrain$C11)
X <- c(1:10)  
Y <-"C11"
ordinal.fit <- h2o.glm(y = Y, x = X, training_frame = Dtrain, lambda=c(0.000000001), alpha=c(0.7), family = "ordinal", beta_epsilon=1e-8, objective_epsilon=1e-10, obj_reg=0.00001,max_iterations=1000 )
