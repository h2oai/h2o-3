import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils


def hist_test():
    kwargs = {}
    kwargs['server'] = True

    print "Import small prostate dataset"
    hex = h2o.import_file(pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    hex["AGE"].hist(**kwargs)
    hex["VOL"].hist(**kwargs)


if __name__ == "__main__":
    pyunit_utils.standalone_test(hist_test)
else:
    hist_test()
