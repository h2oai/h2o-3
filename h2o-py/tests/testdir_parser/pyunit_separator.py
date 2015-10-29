import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils


def separator_test():
    #Test tab seperated files
    path_tab = "smalldata/parser/tabs.tsv"
    tab_test = h2o.import_file(path=pyunit_utils.locate(path_tab), destination_frame="tab_hex", sep="\t")

    #Test pipe separated files
    path_pipe = "smalldata/parser/pipes.psv"
    pipe_test = h2o.import_file(path=pyunit_utils.locate(path_pipe), destination_frame="pipe_hex", sep="|")

    #Test hive files
    path_hive = "smalldata/parser/test.hive"
    hive_test = h2o.import_file(path=pyunit_utils.locate(path_hive), destination_frame="hive_hex", sep="\001")

    #Test semi colon separated files
    path_semi = "smalldata/parser/semi.scsv"
    semi_test = h2o.import_file(path=pyunit_utils.locate(path_semi), destination_frame="semi_hex", sep=";")

    #Below will fail so commented out.
    # JIRA ticket placed.

    #Test caret separated files
    #path_caret = "smalldata/parser/caret.csv"
    #caret_test = h2o.import_file(path=pyunit_utils.locate(path_caret), destination_frame="caret_hex", sep="^")

    #Test asterisk separated files
    #path_asterisk = "smalldata/parser/asterisk.asv"
    #asterisk_test = h2o.import_file(path=pyunit_utils.locate(path_asterisk), destination_frame="asterisk_hex", sep="*")

if __name__ == "__main__":
    pyunit_utils.standalone_test(separator_test)
else:
    separator_test()






