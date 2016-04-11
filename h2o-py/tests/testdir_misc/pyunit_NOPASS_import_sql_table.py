import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils


def sql_table():

  pet = h2o.import_sql_table("jdbc:mysql://localhost:3306/menagerie?&useSSL=false", "pet", "root", "ludi")
  print(pet)


if __name__ == "__main__":
  pyunit_utils.standalone_test(sql_table)
else:
  sql_table()
