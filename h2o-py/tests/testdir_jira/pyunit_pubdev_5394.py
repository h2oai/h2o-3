
import h2o

from tests import pyunit_utils


def pubdev_5394():

    twoLineEntry = """ First line
    Second line
    Third line"""

    training_data = {
        'C1': [1.765, 2.35],
        'C2': [twoLineEntry, twoLineEntry]
    }

    training_data = h2o.H2OFrame(training_data)
    pandas_frame = training_data.as_data_frame()
    print(pandas_frame['C2'][0])



if __name__ == "__main__":
    pyunit_utils.standalone_test(pubdev_5394)
else:
    pubdev_5394()
