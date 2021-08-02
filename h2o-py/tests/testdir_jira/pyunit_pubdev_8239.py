import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils


def pubdev_8239():
    df = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/parser/parquet/as_df_err.parquet", header=1)

    # check that content doesn't become corrupted when downloading line 561:
    # this was ok
    assert df.head(rows=560).get_frame_data().__contains__("~{fy?$ZWN")
    # here it become broken in failing case
    assert df.head(rows=561).get_frame_data().__contains__("~{fy?$ZWN")
    assert df.get_frame_data().__contains__("~{fy?$ZWN")
    df3 = df.as_data_frame(use_pandas=False, header=False)
    assert df3[2][0].__contains__("~{fy?$ZWN")
    assert len(df3) == df.nrow

    # check that in py2 this is not failing on pandas.errors.ParserError: Error tokenizing data. C error: ...    
    print(df.as_data_frame())
   
    
if __name__ == "__main__":
    pyunit_utils.standalone_test(pubdev_8239)
else:
    pubdev_8239()
