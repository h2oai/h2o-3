import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils

def sort():
    df = h2o.import_file(pyunit_utils.locate("smalldata/synthetic/smallIntFloats.csv.zip"))
    sorted_column_indices = [0,1]
    print("Performing and checking on descending sort...")
    df2 = df.sort(sorted_column_indices, ascending=[False, False]).asnumeric()
    pyunit_utils.check_sorted_2_columns(df2, sorted_column_indices, prob=0.001, ascending=[False, False]) # check some rows
    print("Performing and checking on descending first column and ascending second column sort...")
    df1 = df.sort(sorted_column_indices, [False, True]).asnumeric()    # ascending sort
    pyunit_utils.check_sorted_2_columns(df1, sorted_column_indices, prob=0.001, ascending=[False, True]) # check some rows
    print("Performing and checking on ascending first column and descending second column sort...")
    df1 = df.sort(sorted_column_indices, [True, False]).asnumeric()    # ascending sort
    pyunit_utils.check_sorted_2_columns(df1, sorted_column_indices, prob=0.001, ascending=[True, False]) # check some rows


if __name__ == "__main__":
    pyunit_utils.standalone_test(sort)
else:
    sort()

