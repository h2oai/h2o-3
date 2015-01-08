from h2o import H2OConnection
from h2o import H2OFrame
from h2o import H2OGBM

######################################################
#
# Sample Running GBM on prostate.csv

# Connect to a pre-existing cluster
cluster = H2OConnection()

# Training data
train_data = H2OFrame(remote_fname="smalldata/airlines/AirlinesTrain.csv.zip")
train_data = train_data.drop('IsDepDelayed_REC')
print train_data.describe()

# Testing data
test_data = H2OFrame(remote_fname="smalldata/airlines/AirlinesTest.csv.zip")
test_data = test_data.drop('IsDepDelayed_REC')
print test_data.describe()

# Run GBM
gbm = H2OGBM(dataset=train_data,validation_dataset=test_data,x="IsDepDelayed",ntrees=50,shrinkage=0.1,interaction_depth=5)
print gbm._model

