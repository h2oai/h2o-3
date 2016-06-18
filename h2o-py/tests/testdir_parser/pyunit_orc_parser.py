from builtins import str
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils


def separator_test():

    training1_filename = "smalldata/gridsearch/multinomial_training1_set.csv"
    frame1 = h2o.import_file(path=pyunit_utils.locate(training1_filename))


    training2_filename = "smalldata/parser/avro/sequence100k.avro"
    frame2 = h2o.import_file(path=pyunit_utils.locate(training2_filename))
    #Test tab seperated files by giving separator argument
    path_tab = "smalldata/parser/orc/demo-11-zlib.orc"
    tab_test = h2o.import_file(path=pyunit_utils.locate(path_tab))


if __name__ == "__main__":
    pyunit_utils.standalone_test(separator_test)
else:
    separator_test()






