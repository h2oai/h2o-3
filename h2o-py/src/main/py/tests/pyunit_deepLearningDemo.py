from h2o import H2OConnection
from h2o import H2OFrame
from h2o import H2ODeepLearning
from h2o import H2OGBM

######################################################
#
# Sample Running GBM and DeepLearning on ecology_XXX.csv

# Connect to a pre-existing cluster
cluster = H2OConnection()

# Training data
train_data = H2OFrame(remote_fname="smalldata/gbm_test/ecology_model.csv")
train_data = train_data.drop('Site')
train_data['Angaus'] = train_data['Angaus'].asfactor()
print train_data.describe()

# Testing data
test_data = H2OFrame(remote_fname="smalldata/gbm_test/ecology_eval.csv")
test_data['Angaus'] = test_data['Angaus'].asfactor()
print test_data.describe()

# Run GBM
gbm = H2OGBM(dataset=train_data,validation_dataset=test_data,x="Angaus",ntrees=100)
print gbm._model

# Run DeepLearning
dl = H2ODeepLearning(dataset=train_data,validation_dataset=test_data,x="Angaus",epochs=1000,hidden=[20,20,20])
print dl._model

