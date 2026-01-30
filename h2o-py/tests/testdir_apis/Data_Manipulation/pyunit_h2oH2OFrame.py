
import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils as pu
import h2o
from h2o.utils.typechecks import assert_is_type
from h2o.frame import H2OFrame


data = [
    [1, .4, "a",  1],
    [2, 5 , "b",  0],
    [3, 6 ,  "", 1],
]
col_names = ["num1", "num2", "str1", "enum1"]
col_types = ['numeric', 'numeric', 'string', 'enum']
col_types_back = dict(zip(col_names, ['int', 'real', 'string', 'enum']))
na_str = ['']


def check_frame(fr, dest=None):
    if dest is not None:
        assert fr.key == dest
    assert fr.nrows == len(data)
    assert fr.ncols == len(data[0])
    assert fr.columns == col_names
    assert fr.types == col_types_back
    for i, row in enumerate(data):
        for j, value in enumerate(row):
            expected_val = ('NA' if data[i][j] == na_str[0] 
                            else str(data[i][j]) if col_types[j] == 'enum'
                            else data[i][j])
            assert fr[i, j] == expected_val, "expected value at [%s, %s] = %s, but got %s" % (i, j, fr[i, j], expected_val)
    

def H2OFrame_from_list_no_header():
    fr = h2o.H2OFrame(data, destination_frame="from_list_no_header", 
                      column_names=col_names, column_types=col_types, na_strings=na_str)
    assert_is_type(fr, H2OFrame)
    check_frame(fr, "from_list_no_header")


def H2OFrame_from_list_with_header():
    fr = h2o.H2OFrame([col_names]+data, destination_frame="from_list_with_header",
                      header=1, column_types=col_types, na_strings=na_str)
    assert_is_type(fr, H2OFrame)
    check_frame(fr, "from_list_with_header")
    
    
def H2OFrame_from_dict():
    fr = h2o.H2OFrame(dict(zip(col_names, zip(*data))), destination_frame="from_dict",
                      column_types=col_types, na_strings=na_str)
    assert_is_type(fr, H2OFrame)
    check_frame(fr, "from_dict")
    
    
def H2OFrame_from_numpy():
    import numpy as np
    fr = h2o.H2OFrame(np.array(data), destination_frame="from_numpy_array",
                      column_names=col_names, column_types=col_types, na_strings=na_str)
    assert_is_type(fr, H2OFrame)
    check_frame(fr, "from_numpy_array")


def H2OFrame_from_pandas():
    import pandas as pd
    fr = h2o.H2OFrame(pd.DataFrame(data, columns=col_names), destination_frame="from_pandas_dataframe",
                      column_types=col_types, na_strings=na_str)
    assert_is_type(fr, H2OFrame)
    check_frame(fr, "from_pandas_dataframe")


def H2OFrame_from_scipy():
    from scipy.sparse import csr_matrix, csc_matrix
    nostr_data = [[0 if isinstance(v, str) else v for v in r] for r in data]
    def check_sparse_frame(fr, dest):
        assert fr.key == dest
        assert fr.nrows == len(nostr_data)
        assert fr.ncols == len(nostr_data[0])
        assert fr.columns == ["C%i"%i for i in range(1, fr.ncols+1)]  # BUG! construction with scipy matrix ignores `column_names` param: see issue https://github.com/h2oai/h2o-3/issues/15946
        assert fr.types == dict(C1='int', C2='real', C3='int', C4='int') # enum types are also ignored, but it makes sense for sparse matrices, we could add a warning though.
        for i, row in enumerate(data):
            for j, value in enumerate(row):
                assert fr[i, j] == nostr_data[i][j], "expected value at [%s, %s] = %s, but got %s" % (i, j, fr[i, j], nostr_data[i][j])
        
    
    fr = h2o.H2OFrame(csr_matrix(nostr_data), destination_frame="from_sparse_row_matrix",
                      column_names=col_names, column_types=col_types)
    assert_is_type(fr, H2OFrame)
    print(fr)
    check_sparse_frame(fr, "from_sparse_row_matrix")

    fr = h2o.H2OFrame(csc_matrix(nostr_data), destination_frame="from_sparse_column_matrix",
                      column_names=col_names, column_types=col_types)
    assert_is_type(fr, H2OFrame)
    check_sparse_frame(fr, "from_sparse_column_matrix")

def H2OFrame_from_H2OFrame():
    fr = h2o.H2OFrame(python_obj=data, destination_frame="h2oframe_ori",
                      column_names=col_names, column_types=col_types, na_strings=na_str)
    
    dupl1 = h2o.H2OFrame(python_obj=fr)
    assert_is_type(dupl1, H2OFrame)
    check_frame(dupl1)
    assert fr.key != dupl1.key
    
    dupl2 = h2o.H2OFrame(python_obj=fr, destination_frame="h2oframe_duplicate_with_new_name")
    assert_is_type(dupl2, H2OFrame)
    check_frame(dupl2, "h2oframe_duplicate_with_new_name")

    dupl3 = h2o.H2OFrame(python_obj=fr, column_names=["n1", "n2", "s1", "e1"], destination_frame="h2oframe_duplicate_with_renamed_columns")
    assert_is_type(dupl3, H2OFrame)
    assert dupl3.key == "h2oframe_duplicate_with_renamed_columns"
    assert dupl3.ncols == fr.ncols
    assert dupl3.nrows == fr.nrows
    assert dupl3.as_data_frame(use_pandas=False, header=False) == fr.as_data_frame(use_pandas=False, header=False)
    assert dupl3.columns == ["n1", "n2", "s1", "e1"]

    dupl4 = h2o.H2OFrame(python_obj=fr, skipped_columns=[1, 3], column_names=["n1", "s1"], destination_frame="h2oframe_duplicate_with_skipped_columns")
    assert_is_type(dupl3, H2OFrame)
    assert dupl4.key == "h2oframe_duplicate_with_skipped_columns"
    assert dupl4.ncols == fr.ncols - 2
    assert dupl4.nrows == fr.nrows
    assert dupl4.as_data_frame(use_pandas=False, header=False) == fr.drop([1, 3]).as_data_frame(use_pandas=False, header=False)
    assert dupl4.columns == ["n1", "s1"]


def H2OFrame_skipped_columns_BUG_fixed():
    f1 = h2o.H2OFrame(data, skipped_columns=[1])
    f2 = h2o.H2OFrame(data)
    assert f1.ncol == (f2.ncol-1), "expected number of columns: {0}, actual column numbers: {1}".format(f1.ncol, (f2.ncol-1))


pu.run_tests([
    H2OFrame_from_list_no_header,
    H2OFrame_from_list_with_header,
    H2OFrame_from_dict,
    H2OFrame_from_numpy,
    H2OFrame_from_pandas,
    H2OFrame_from_scipy,
    H2OFrame_from_H2OFrame,
    H2OFrame_skipped_columns_BUG_fixed
])
