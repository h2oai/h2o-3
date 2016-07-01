from builtins import str
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils


def orc_parser_test():

    #Test tab seperated files by giving separator argument
    path_tab = "smalldata/parser/orc/double_single_col.orc"
    tab_test = h2o.import_file(path=pyunit_utils.locate(path_tab))

    path_tab2 = "smalldata/parser/orc/TestOrcFile.columnProjection.orc"
    tab_test2 = h2o.import_file(path=pyunit_utils.locate(path_tab2))

    path_tab3 = "smalldata/parser/orc/demo-11-zlib.orc"
    tab_test3 = h2o.import_file(path=pyunit_utils.locate(path_tab3))

if __name__ == "__main__":
    pyunit_utils.standalone_test(orc_parser_test)
else:
    orc_parser_test()






