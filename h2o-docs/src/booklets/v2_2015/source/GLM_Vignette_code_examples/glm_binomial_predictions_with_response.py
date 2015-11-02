h2o.init()
h2o_df = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/prostate/prostate.csv")
h2o_df['CAPSULE'] = h2o_df['CAPSULE'].asfactor()

rand_vec = h2o_df.runif(1234)

train = h2o_df[rand_vec <= 0.8]
valid = h2o_df[(rand_vec > 0.8) & (rand_vec <= 0.9)]
test = h2o_df[rand_vec > 0.9]
binomial_fit = H2OGeneralizedLinearEstimator(family = "binomial")
binomial_fit.train(y = "CAPSULE", x = ["AGE", "RACE", "PSA", "GLEASON"], training_frame = train, validation_frame = valid)

# Make and export predictions.
pred = binomial_fit.predict(test)
h2o.export_file(pred, "/tmp/pred.csv", force = True)
# Or you can export the predictions to hdfs:
#   h2o.exportFile(pred, "hdfs://namenode/path/to/file.csv")

# Calculate metrics.
binomial_fit.model_performance(test)
