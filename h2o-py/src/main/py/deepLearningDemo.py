import h2o

######################################################
#
# Sample Running GBM and DeepLearning on ecology_XXX.csv

# Connect to a pre-existing cluster
cluster = h2o.H2OConnection()

# Training data
train_data = h2o.H2OFrame(remote_fname="smalldata/gbm_test/ecology_model.csv")
del train_data['Site']
train_data['Angaus'] = train_data['Angaus'].asfactor()
print train_data.describe()

# Testing data
test_data = h2o.H2OFrame(remote_fname="smalldata/gbm_test/ecology_eval.csv")
test_data['Angaus'] = test_data['Angaus'].asfactor()
print test_data.describe()

# Run GBM
gbm = h2o.H2OGBM(dataset=train_data,validation_dataset=test_data,x="Angaus",ntrees=100,learn_rate=0.1)
#print gbm._model
print gbm.metrics()

# Run DeepLearning
dl = h2o.H2ODeepLearning(dataset=train_data,validation_dataset=test_data,x="Angaus",epochs=100,hidden=[20,20,20])
#print dl._model
print dl.metrics()

