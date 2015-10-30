import h2o
h2o.init()
h2o_df = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/prostate/prostate.csv")
h2o_df['CAPSULE'] = h2o_df['CAPSULE'].asfactor()
binomial_fit = h2o.glm(y = "CAPSULE", x = ["AGE", "RACE", "PSA", "GLEASON"], training_frame = h2o_df, family = "binomial")
h2o.download_pojo(binomial_fit)
