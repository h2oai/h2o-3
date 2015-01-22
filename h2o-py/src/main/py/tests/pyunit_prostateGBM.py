import h2o

######################################################
#
# Sample Running GBM on prostate.csv

# Connect to a pre-existing cluster
h2o.init()  # connect to localhost:54321

df = h2o.import_frame(path="smalldata/logreg/prostate.csv")
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
gbm = H2OGBM(dataset=train,x="CAPSULE",ntrees=50,shrinkage=0.1)
print gbm._model

