import sys
import os
sys.path.insert(1, os.path.join('..','..','h2o-py'))
#sys.path.insert(0, os.path.join('..','..','h2o-py'))
import h2o

######################################################
# Parse command-line args.
#
# usage:  python test_name.py --usecloud ipaddr:port
#

ip_port = sys.argv[2].split(":")
print ip_port
ip = ip_port[0]
port = int(ip_port[1])

######################################################
#
# Sample Running GBM on prostate.csv

# Connect to a pre-existing cluster
h2o.init(ip=ip, port=port, strict_version_check=False)

df = h2o.import_file(path="../../../smalldata/logreg/prostate.csv")
df.describe()

# Remove ID from training frame
df.pop('ID')  # WARNING: this is NOT python negative indexing!!!

# For VOL & GLEASON, a zero really means "missing"
vol = df['VOL']
vol[vol == 0] = float("nan")
gle = df['GLEASON']
gle[gle == 0] = float("nan")

# Convert CAPSULE to a logical factor
df['CAPSULE'] = df['CAPSULE'].asfactor()

# Test/train split
r = vol.runif()
train = df[r < 0.8]
test  = df[r >= 0.8]

# See that the data is ready
train.describe()
test.describe()


# Run GBM
from h2o.estimators.gbm import H2OGradientBoostingEstimator
gbm =H2OGradientBoostingEstimator(ntree=5, max_depth=3, distribution="bernoulli")
gbm.train(x=range(1,train.ncol), y="CAPSULE", training_Frame=train, validation_frame=valid)

mm = gbm.model_performance(test)
mm.show()
