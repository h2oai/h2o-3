import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils


def parse_partitionBy():
    """
    Tests Parquet parser by comparing the summary of the original csv frame with the h2o parsed Parquet frame.
    Basic use case of importing files with auto-detection of column types.
    :return: None if passed.  Otherwise, an exception will be thrown.
    """
    parquet = h2o.import_file(path=pyunit_utils.locate("smalldata/partitioned/partitioned_arilines/"), partition_by=["Year", "IsArrDelayed"])
    print(parquet.as_data_frame())
    csv = h2o.import_file(path=pyunit_utils.locate("smalldata/partitioned/partitioned_arilines_csv/"), partition_by=["Year", "IsArrDelayed"])
    original = h2o.import_file(path=pyunit_utils.locate("smalldata/airlines/modified_airlines.csv"))
    print(original.as_data_frame())
    
    assert original.nrows == parquet.nrows == csv.nrows
    assert original.ncols == parquet.ncols == csv.ncols
    
    # Column names must remain the same, order might be different (partitioned columns are append at the end).
    for i in range(0, original.ncols):
        assert original.names[i] in parquet.names
        assert original.names[i] in csv.names
        
    
    assert parquet.type("Year") == "enum"
    assert parquet.type("IsArrDelayed") == "enum"
    assert csv.type("Year") == "enum"
    assert csv.type("IsArrDelayed") == "enum"
    
if __name__ == "__main__":
    pyunit_utils.standalone_test(parse_partitionBy)
else:
    parse_partitionBy()
