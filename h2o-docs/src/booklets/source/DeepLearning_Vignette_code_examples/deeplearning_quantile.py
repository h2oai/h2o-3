import h2o
from h2o.estimators.deeplearning import H2ODeepLearningEstimator
h2o.init() 
train = h2o.import_file("https://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")
splits = train.split_frame(ratios=[0.75], seed=1234)
dl = H2ODeepLearningEstimator(distribution="quantile", quantile_alpha=0.8)
dl.train(x=range(0,2), y="petal_len", training_frame=splits[0])
print(dl.predict(splits[1]))
