import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils

def sql_table():

  citi_sql = h2o.import_sql_table("jdbc:mysql://172.16.2.178:3306/ingestSQL?&useSSL=false", "citibike20k", "root", "0xdata")
  citi_csv = h2o.import_file(pyunit_utils.locate("smalldata/demos/citibike_20k.csv"))
    
  py_citi_sql = citi_sql.as_data_frame(False)[1:] #don't compare headers
  py_citi_csv = citi_csv.as_data_frame(False)[1:]
  
  assert is_equal(py_citi_sql, py_citi_csv)

  citi_sql = h2o.import_sql_table("jdbc:mysql://172.16.2.178:3306/ingestSQL?&useSSL=false", "citibike20k", "root", "0xdata", ["starttime", "bikeid"])
  assert citi_sql.ncol == 2

def is_equal(sql, csv):
  if len(sql) != len(csv) or len(sql[0]) != len(csv[0]): return False
  for i in range(len(sql)):
    for j in range(len(sql[i])):
      if sql[i][j] != csv[i][j] and "{0:.4f}".format(float(sql[i][j])) != "{0:.4f}".format(float(csv[i][j])):
        print(sql[i][j], csv[i][j])
        print(sql[i])
        print(csv[i])
        print(i, j)
        return False
  return True
  

if __name__ == "__main__":
  pyunit_utils.standalone_test(sql_table)
else:
  sql_table()
