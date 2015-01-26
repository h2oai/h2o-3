import sys
sys.path.insert(1, "..")  # inserts before index "1"

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
vol[vol == 0] = None
gle = train['GLEASON']
gle[gle == 0] = None

# Convert CAPSULE to a logical factor
train['CAPSULE'] = train['CAPSULE'].asfactor()

# See that the data is ready
print train.describe()

# Run GBM
from h2o import H2OGBM

my_gbm = H2OGBM()
my_gbm.training_frame = train
my_gbm.y = "CAPSULE"  # 0th index
my_gbm.x = range(1, train.ncol(), 1)  # train on the remaining columns
my_gbm.ntrees = 50
my_gbm.learn_rate = 0.1
my_gbm.fit()

my_gbm.show()


# Alternative Look:
from h2o.model.h2o_gbm_builder import H2OGBMBuilder

my_gbm2 = H2OGBMBuilder(training_frame=train,
                        y="CAPSULE",
                        x=range(1, train.ncol(), 1),
                        ntrees=50,
                        learn_rate=0.1).fit()
my_gbm2.show()
