library(h2o)
h2o.init(nthreads = -1)
train.hex <- h2o.importFile("https://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")
splits <- h2o.splitFrame(train.hex, 0.75, seed=1234)
dl <- h2o.deeplearning(x=1:3, y="petal_len",
        training_frame=splits[[1]],
        distribution="quantile", quantile_alpha=0.8)
h2o.predict(dl, splits[[2]])
