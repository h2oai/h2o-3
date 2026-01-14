from tests import pyunit_utils

# if datatable or polars/pyarrow is installed, this test will show that using datatable to convert h2o frame to pandas
# frame is much faster for large datasets.    
def test_polars_datatable():
    file1 = "smalldata/titanic/titanic_expanded.csv"
    file2 = "smalldata/glm_test/multinomial_3Class_10KRow.csv"
    file3 = "smalldata/timeSeries/CreditCard-ts_train.csv"

    original_converted_frame1 = pyunit_utils.single_thread_pandas_conversion(file1)
    pyunit_utils.test_frame_conversion(file1, original_converted_frame1)
    original_converted_frame2 = pyunit_utils.single_thread_pandas_conversion(file2)
    pyunit_utils.test_frame_conversion(file2, original_converted_frame2)
    original_converted_frame3 = pyunit_utils.single_thread_pandas_conversion(file3)
    pyunit_utils.test_frame_conversion(file3, original_converted_frame3)    
   
if __name__ == "__main__":
    pyunit_utils.standalone_test(test_polars_datatable)
else:
    test_polars_datatable()
