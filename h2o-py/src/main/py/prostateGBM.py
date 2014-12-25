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

# For VOL, a zero really means "missing"
vol = df['VOL']
# (= ([ %vec (== %vec #0) "null") #NaN
vol[vol==0] = None
print vol.show()
print train.describe()

#gbm = H2OGBM(dataset=train,ntrees=10,a)
