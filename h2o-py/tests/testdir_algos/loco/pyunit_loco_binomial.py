from __future__ import print_function
from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils

def loco_binomial():
    df = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))

    # Remove ID from training frame
    train = df.drop("ID")

    # For VOL & GLEASON, a zero really means "missing"
    vol = train['VOL']
    vol[vol == 0] = None
    gle = train['GLEASON']
    gle[gle == 0] = None

    # Convert CAPSULE to a logical factor
    train['CAPSULE'] = train['CAPSULE'].asfactor()
    g = h2o.h2o.H2OGradientBoostingEstimator()
    g.train(x=list(range(1, train.ncol)), y="CAPSULE",training_frame=train)

    #Run LOCO
    g.loco(df)
    g.loco(df, replace_val="mean")
    g.loco(df, replace_val="median")

if __name__ == "__main__":
    pyunit_utils.standalone_test(loco_binomial)
else:
    loco_binomial()