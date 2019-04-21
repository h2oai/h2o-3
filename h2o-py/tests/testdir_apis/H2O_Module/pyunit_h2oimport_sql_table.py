from __future__ import print_function
import sys, os
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o
import inspect

def h2oimport_sql_table():
    """
    Python API test: h2o.import_sql_table(connection_url, table, username, password, columns=None, optimize=True)
    Not a real test, just make sure arguments are not changed.
    """

    command_list = ['connection_url', 'table', 'username', 'password', 'columns', 'optimize']
    allargs = inspect.getargspec(h2o.import_sql_table)
    for arg_name in command_list:
        assert arg_name in allargs.args, "argument "+arg_name+" is missing from h2o.import_sql_table() command"

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2oimport_sql_table)
else:
    h2oimport_sql_table()
