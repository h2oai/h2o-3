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
ip = ip_port[0]
port = int(ip_port[1])

######################################################
#
# Sample Running GBM on prostate.csv

# Connect to a pre-existing cluster
h2o.init(ip=ip, port=port, strict_version_check=False)

df = h2o.import_file(path=os.path.realpath("../../smalldata/logreg/prostate.csv"))
df.describe()

# Remove ID from training frame
df.pop('ID')  # WARNING: this is NOT python negative indexing!!!

# For VOL & GLEASON, a zero really means "missing"
df[df['VOL'],'VOL'] = None
df[df['GLEASON'],'GLEASON'] = None

# Convert CAPSULE to a logical factor
df['CAPSULE'] = df['CAPSULE'].asfactor()

# Test/train split
r = df.runif()
train = df[r < 0.8]
test  = df[r >= 0.8]

# See that the data is ready
train.describe()
test.describe()


# Run GBM
from h2o.estimators.gbm import H2OGradientBoostingEstimator
gbm =H2OGradientBoostingEstimator(ntrees=5, max_depth=3, distribution="bernoulli")
gbm.train(x=list(range(2,train.ncol)), y="CAPSULE", training_frame=train, validation_frame=test)

mm = gbm.model_performance(test)
mm.show()
