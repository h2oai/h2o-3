import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from os import path
import pandas as pd
'''
Export file with h2o.export_file with a custom separator
'''


def export_custom_separator():
    data = {'col1': [1, 2], 'col2': [3, 4]}
    expected = pd.DataFrame(data=data)
    prostate = h2o.H2OFrame(expected)

    target_default = path.join(pyunit_utils.locate("results"), "export_file_default_sep.csv")
    target_custom = path.join(pyunit_utils.locate("results"), "export_file_custom_sep.csv")

    h2o.export_file(prostate, target_default)
    h2o.export_file(prostate, target_custom, sep="|")

    parsed_default = pd.read_csv(target_default, sep=",")
    parsed_custom = pd.read_csv(target_custom, sep="|")

    assert expected.equals(parsed_default)
    assert expected.equals(parsed_custom)


if __name__ == "__main__":
    pyunit_utils.standalone_test(export_custom_separator)
else:
    export_custom_separator()



