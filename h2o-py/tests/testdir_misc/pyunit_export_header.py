from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from os import path
import pandas as pd
'''
Export file with h2o.export_file with a custom header configuration
'''


def export_custom_headers():
    data = {'col1': [1, 2], 'col2': [3, 4]}
    expected = pd.DataFrame(data=data)
    data_h2o = h2o.H2OFrame(expected)

    target_default = path.join(pyunit_utils.locate("results"), "export_file_default.csv")
    target_no_header = path.join(pyunit_utils.locate("results"), "export_file_no_header.csv")
    target_no_quotes = path.join(pyunit_utils.locate("results"), "export_file_no_quotes.csv")

    h2o.export_file(data_h2o, target_default)
    h2o.export_file(data_h2o, target_no_header, header=False)
    h2o.export_file(data_h2o, target_no_quotes, quote_header=False)

    with open(target_default) as exp:
        assert exp.readline() == '"col1","col2"\n' 
    with open(target_no_header) as exp:
        assert exp.readline() == '1,3\n' 
    with open(target_no_quotes) as exp:
        assert exp.readline() == 'col1,col2\n' 


if __name__ == "__main__":
    pyunit_utils.standalone_test(export_custom_headers)
else:
    export_custom_headers()
