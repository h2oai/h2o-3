import h2o
from h2o.estimators.gbm import H2OGradientBoostingEstimator
h2o.init() 
train = h2o.import_file("https://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")
splits = train.split_frame(ratios=[0.75], seed=1234)
gbm = H2OGradientBoostingEstimator(distribution="quantile", quantile_alpha=0.8)
gbm.train(x=range(0,2), y="petal_len", training_frame=splits[0])
print(gbm.predict(splits[1]))
