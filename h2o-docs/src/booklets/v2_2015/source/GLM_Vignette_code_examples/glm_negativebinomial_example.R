library(h2o)
h2o.init()
h2o_df = h2o.importFile("http://h2o-public-test-data.s3.amazonaws.com/smalldata/glm_test/Motor_insurance_sweden.txt")
predictors <- c["Payment", "Insured", "Kilometres", "Zone", "Bonus", "Make"]
response <- "Claims"
negativebinomial.fit <- h2o.glm(x=predictors, y=response, training_frame=h2o_df, family="negativebinomial", link="identity", theta=0.5)