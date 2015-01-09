from h2o import H2OConnection
from h2o import H2OFrame
from h2o import H2OGBM

######################################################
#
# Sample Running GBM on prostate.csv

# Connect to a pre-existing cluster
cluster = H2OConnection()

df = H2OFrame(remote_fname="smalldata/logreg/prostate.csv")
print df.describe()

# Remove ID from training frame
train = df.drop("ID")

# For VOL & GLEASON, a zero really means "missing"
vol = train['VOL']
vol[vol==0] = None
gle = train['GLEASON']
gle[gle==0] = None

# Convert CAPSULE to a logical factor
train['CAPSULE'] = train['CAPSULE'].asfactor()

# See that the data is ready
print train.describe()

# Run GBM
gbm = H2OGBM(dataset=train,x="CAPSULE",ntrees=50,learn_rate=0.1)
print gbm._model
print gbm.metrics()
