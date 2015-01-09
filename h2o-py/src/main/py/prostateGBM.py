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
del df['ID']

# For VOL & GLEASON, a zero really means "missing"
vol = df['VOL']
vol[vol==0] = None
gle = df['GLEASON']
gle[gle==0] = None

# Convert CAPSULE to a logical factor
df['CAPSULE'] = df['CAPSULE'].asfactor()

r = vol.runif()
train = df[r< 0.8]
test  = df[r>=0.8]

# See that the data is ready
print train.describe()
print test .describe()

# Run GBM
gbm = H2OGBM(dataset=train,x="CAPSULE",validation_dataset=test,ntrees=50,max_depth=5,learn_rate=0.1)
print gbm._model
print gbm.metrics()
