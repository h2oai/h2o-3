from __future__ import print_function
import sys
sys.path.insert(1,"../../")
from tests import pyunit_utils
from h2o.utils.typechecks import assert_is_type
from h2o.assembly import *
from h2o.transforms.preprocessing import *

def h2oassembly_fit():
    """
    Python API test: H2OAssembly.fit(fr)

    Copied from pyunit_assembly_demo.py
    """

    fr = h2o.import_file(pyunit_utils.locate("smalldata/iris/iris_wheader.csv"),
                       col_types=["numeric","numeric","numeric","numeric","string"])  # import data
    assembly = H2OAssembly(steps=[("col_select",      H2OColSelect(["sepal_len", "petal_len", "class"])),
                                ("cos_sep_len",     H2OColOp(op=H2OFrame.cos, col="sepal_len", inplace=True)),
                                ("str_cnt_species", H2OColOp(op=H2OFrame.countmatches, col="class", inplace=False,
                                                             pattern="s"))])     # string operation

    assert_is_type(assembly, H2OAssembly)
    result = assembly.fit(fr)  # fit the assembly, which performs column select
    assert_is_type(result, H2OFrame)
    assert result.ncol==4, "H2OAssembly.fit() command is not working" # selects 3 columns, added one from countmatches.


if __name__ == "__main__":
    pyunit_utils.standalone_test(h2oassembly_fit)
else:
    h2oassembly_fit()
