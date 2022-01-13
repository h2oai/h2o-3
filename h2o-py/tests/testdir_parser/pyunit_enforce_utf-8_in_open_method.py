# coding=utf-8
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
import pandas as pd
import locale

def enforce_utf8_encoding():
    orig_locale = locale.getlocale()
    print("original locale is:")
    print(orig_locale)
    all_rows = pd.read_csv(pyunit_utils.locate("smalldata/gbm_test/titanic.csv"))
    all_rows.at[0,0] = "�"
    try:
        locale.setlocale(locale.LC_ALL, 'en_US.ISO8859-1')
        # this reproduces the encoding error when certain codec can't encode certain character 
        h2o.H2OFrame(all_rows)
    except locale.Error: # in run in dev-python-3.7 there is not en_US.ISO8859-1 available, but there is POSIX which also reproduces:
        locale.setlocale(locale.LC_ALL, 'POSIX')
        h2o.H2OFrame(all_rows)
    finally:
        try:
            locale.setlocale(locale.LC_ALL, orig_locale)
        except locale.Error: # for dev-python-3.7
            locale.setlocale(locale.LC_ALL, 'C.UTF-8')
    
if __name__ == "__main__":
    pyunit_utils.standalone_test(enforce_utf8_encoding)
else:
    enforce_utf8_encoding()

