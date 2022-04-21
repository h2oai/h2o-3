import sys, os
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils


# local setup:
#
# docker pull postgres
# docker run -d --name postgres -e POSTGRES_PASSWORD='postgres' -v /Users/zuzanaolajcova/dev/postgres:/var/lib/postgresql/data -p 5432:5432 postgres
#
# docker pull mysql
# docker run -d --name mysql -e MYSQL_ROOT_PASSWORD='mysql'MYSQL_PASSWORD='mysql' -v /Users/zuzanaolajcova/dev/mysql:/var/lib/mysql -p 3306:3306 mysql
#
# docker pull mcr.microsoft.com/mssql/server
# docker run --name mssql -e 'ACCEPT_EULA=Y' -e 'MSSQL_SA_PASSWORD=<YourStrong!Passw0rd>' -p 1433:1433 -d mcr.microsoft.com/mssql/server:latest
#
# docker pull mariadb
# docker run --name mariadb -e MYSQL_ROOT_PASSWORD=mypass MYSQL_PASSWORD=mypass -v /Users/zuzanaolajcova/dev/mariadb:/var/lib/mysql -p 3307:3307 mariadb
#
# and import  https://s3.amazonaws.com/h2o-public-test-data/smalldata/demos/citibike_20k.csv to each new db as a new table citibike_new


# postgre OK:
def test_postgre():
  conn_url = "jdbc:postgresql://localhost:5432/postgres"
  password = "postgres"
  user = "postgres"

  testSelectQueries(conn_url=conn_url, password=password, user=user)


# mysql OK:
def test_mysql():
  conn_url = "jdbc:mysql://localhost:3306/mysql"
  password = "mysql"
  user = "root"

  testSelectQueries(conn_url=conn_url, password=password, user=user)


#sql server NOK, reproduces:
def test_sqlserver():
  conn_url = "jdbc:sqlserver://localhost:1433;database=master;encrypt=true;trustServerCertificate=true;"
  password = "<YourStrong!Passw0rd>"
  user = "SA"

  # - citi_sql and citi_sql_single_fm on MS SQL reproduces sql error:
  #   SQLException: Incorrect syntax near the keyword 'SELECT'.
  # - citi_sql_single_fm_no_tmp and citi_sql_single_fm_no_tmp2 on MS SQL reproduces sql error:
  #   Caused by: com.microsoft.sqlserver.jdbc.SQLServerException: The ORDER BY clause is invalid in views, inline functions,
  #   derived tables, subqueries, and common table expressions, unless TOP, OFFSET or FOR XML is also specified.
  testSelectQueries(conn_url=conn_url, password=password, user=user)

# maria db NOK, reproduces:
def test_mariadb():
  conn_url = "jdbc:mariadb://localhost:3307/mysql"
  password = "mypass"
  user = "root"

  # citi_sql and citi_sql_single_fm ok
  # citi_sql_single_fm_no_tmp and citi_sql_single_fm_no_tmp2 not failing but ORDER BY clause was ignored
  testSelectQueries(conn_url=conn_url, password=password, user=user)



def testSelectQueries(conn_url, password, user):
  simple_query = "SELECT * FROM citibike_new ORDER BY tripduration"

  citi_sql = h2o.import_sql_select(conn_url, simple_query, user, password)

  citi_sql_single_fm = h2o.import_sql_select(conn_url, simple_query, user, password, fetch_mode='SINGLE')

  citi_sql_single_fm_no_tmp = h2o.import_sql_select(conn_url, simple_query, user, password, fetch_mode='SINGLE', use_temp_table=False)

  citi_sql_single_fm_no_tmp2 = h2o.import_sql_select(conn_url, "SELECT starttime, stoptime, tripduration, bikeid FROM citibike_new ORDER BY bikeid DESC", user, password, fetch_mode='SINGLE', use_temp_table=False)





pyunit_utils.run_tests([
  # test_postgre,
  # test_mysql,
  # test_sqlserver,
  # test_mariadb,
])
