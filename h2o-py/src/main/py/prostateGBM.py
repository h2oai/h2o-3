from h2o import H2OConnection
from h2o import H2OFrame

######################################################
#
# Sample Running GBM on prostate.csv

# Connect to a pre-existing cluster
cluster = H2OConnection()

df = H2OFrame(remote_fname="smalldata/logreg/prostate.csv")

# Remove ID from training frame
train = df.drop("ID")
print train.describe()

# For VOL, a zero realls means "missing"
vol = df['VOL']
print vol[vol==0].show()
vol[vol==0] = None
print vol.show()
print train.describe()

#train[df['VOL'] == 0] = None

#gbm = H2OGBM(dataset=train,ntrees=10,a)
