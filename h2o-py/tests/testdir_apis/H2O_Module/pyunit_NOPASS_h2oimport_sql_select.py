from __future__ import print_function
import sys, os
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o

def h2oimport_sql_select():
    """
    Python API test: h2o.import_sql_table(connection_url, table, username, password, columns=None, optimize=True)
    Copied from pyunit_NOPASS_import_sql_select.py.

    HELP: Please help fix this one if you can!
    """

    try:
        #conn_url = "jdbc:mysql://172.16.2.178:3306/ingestSQL?&useSSL=false"
        #conn_url = "jdbc:postgresql://mr-0xf2/ingestSQL"
        conn_url = os.getenv("SQLCONNURL")
        table = "citibike20k"

        #figure out username and password
        db_type = conn_url.split(":",3)[1]
        username = password = ""
        if db_type == "mysql":
            username = "root"
            password = "0xdata"
        elif db_type == "postgresql":
            username = password = "postgres"

        citi_sql = h2o.import_sql_table(conn_url, table, username, password, ["starttime", "bikeid"])
        assert citi_sql.nrow == 2e4, "h2o.import_sql_select() command is not working."
        assert citi_sql.ncol == 2, "h2o.import_sql_select() command is not working."

        sql_select = h2o.import_sql_select(conn_url, "SELECT starttime FROM citibike20k", username, password)
        assert sql_select.nrow == 2e4, "h2o.import_sql_select() command is not working."
        assert sql_select.ncol == 1, "h2o.import_sql_select() command is not working."
    except Exception as e:
        assert False, "h2o.import_sql_select() command is not working."

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2oimport_sql_select)
else:
    h2oimport_sql_select()
