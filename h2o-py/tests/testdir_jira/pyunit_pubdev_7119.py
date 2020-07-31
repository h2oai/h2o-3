import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
import pandas as pd

def pubdev_7119():

    # Test 1
    pd_df = pd.DataFrame({'col1': [1,2], 'col2': ['foo"foo\nfoo','foo2'], 'col3': [1,2]})
    h2o_df = h2o.H2OFrame(pd_df)
    pd_df2 = h2o_df.as_data_frame()
    
    print(pd_df)
    print(h2o_df)
    print(pd_df2)
    
    if not pd_df.equals(pd_df2):
        raise Exception('Failed to convert to pandas dataframe if there are fields containing double quotes and line breaks')
    
    # Test 2: just double quotes
    pd_df = pd.DataFrame({'col1': [1,2], 'col2': ['foo"foo','foo2'], 'col3': [1,2]})
    h2o_df = h2o.H2OFrame(pd_df)
    pd_df2 = h2o_df.as_data_frame()
    print(pd_df)
    print(h2o_df)
    print(pd_df2)
    if not pd_df.equals(pd_df2):
        raise Exception('Failed to convert to pandas dataframe if there are fields containing double quotes')

if __name__ == "__main__":
    pyunit_utils.standalone_test(pubdev_7119)
else:
    pubdev_7119()
