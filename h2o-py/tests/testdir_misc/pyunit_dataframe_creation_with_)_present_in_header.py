from __future__ import print_function

import sys
import os

sys.path.insert(1, os.path.join("..", "..", ".."))
import h2o
import pandas as pd
from tests import pyunit_utils


def data_from_csv():
    path = pyunit_utils.locate("smalldata/parser/single_quotes_mixed.csv")
    training_df = pd.read_csv(path, quotechar="'")
    training_df.rename(columns={'age': 'ag)e', 'drugs': ')'}, inplace=True)

    hf = h2o.H2OFrame(training_df)
    hf.describe()

    assert hf.col_names[00] == 'ag)e'
    assert hf.columns[00] == 'ag)e'
    assert hf.as_data_frame()['ag)e'].get(0) == 22
    assert hf.col_names[4] == ')'
    assert hf.columns[4] == ')'
    assert hf.as_data_frame()[')'].get(0) == 'never'

    columns = [ c.replace(")", "]") for c in training_df.columns]
    training_df.columns = columns
    hf = h2o.H2OFrame(training_df)
    hf.describe()

    assert hf.col_names[00] == 'ag]e'
    assert hf.columns[00] == 'ag]e'
    assert hf.as_data_frame()['ag]e'].get(0) == 22
    assert hf.col_names[4] == ']'
    assert hf.columns[4] == ']'
    assert hf.as_data_frame()[']'].get(0) == 'never'


def synthetic_data():
    df_col0 = pd.DataFrame({"abcd)efgh": ["\"Test\"" for _ in range(5)]})
    df_col1 = pd.DataFrame({")": ["\"Test2\"" for _ in range(5)]})
    training_df = df_col0.join(df_col1)
    training_df.head()

    hf = h2o.H2OFrame(training_df)
    hf.describe()

    assert hf.col_names[00] == 'abcd)efgh'
    assert hf.columns[00] == 'abcd)efgh'
    assert hf.as_data_frame()['abcd)efgh'].get(0) == '"Test"'
    assert hf.col_names[1] == ')'
    assert hf.columns[1] == ')'
    assert hf.as_data_frame()[')'].get(0) == '"Test2"'

    columns = [ c.replace(")", "]") for c in training_df.columns ]
    training_df.columns = columns
    hf = h2o.H2OFrame(training_df)
    hf.describe()

    assert hf.col_names[00] == 'abcd]efgh'
    assert hf.columns[00] == 'abcd]efgh'
    assert hf.as_data_frame()['abcd]efgh'].get(0) == '"Test"'
    assert hf.col_names[1] == ']'
    assert hf.columns[1] == ']'
    assert hf.as_data_frame()[']'].get(0) == '"Test2"'


pyunit_utils.run_tests([
    data_from_csv,
    synthetic_data
])

