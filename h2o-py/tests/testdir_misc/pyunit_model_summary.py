from __future__ import print_function
from builtins import range
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator


def model_summary():

    df = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    df.describe()

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
    train.describe()

    # Run GBM
    my_gbm = H2OGradientBoostingEstimator(ntrees=50,
                                          learn_rate=0.1,
                                          distribution="bernoulli")
    my_gbm.train(x=list(range(1, train.ncol)),
                 y="CAPSULE",
                 training_frame=train,
                 validation_frame=train)

    summary = my_gbm.summary()

    #Convert to dataframe and take out metrics of interest from the summary
    summary_df = summary.as_data_frame()
    for i in summary_df.columns.values:
        print(summary_df[i])


if __name__ == "__main__":
    pyunit_utils.standalone_test(model_summary)
else:
    model_summary()
