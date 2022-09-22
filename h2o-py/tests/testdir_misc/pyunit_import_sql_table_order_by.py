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

# Drivers needed:
#  // https://mvnrepository.com/artifact/mysql/mysql-connector-java
#  implementation group: 'mysql', name: 'mysql-connector-java', version: '8.0.28'
#  // https://mvnrepository.com/artifact/org.postgresql/postgresql
#  implementation group: 'org.postgresql', name: 'postgresql', version: '42.3.3'
#// https://mvnrepository.com/artifact/com.microsoft.sqlserver/mssql-jdbc
#  implementation group: 'com.microsoft.sqlserver', name: 'mssql-jdbc', version: '10.2.0.jre8'
#
#  implementation 'org.mariadb.jdbc:mariadb-java-client:2.1.2'


# postgre OK:
def test_postgre():
  conn_url = "jdbc:postgresql://localhost:5432/postgres"
  password = "postgres"
  user = "postgres"

  testSelectQuery(conn_url=conn_url, password=password, user=user, query="SELECT * FROM citibike_new ORDER BY tripduration", exp_first_val=60, exp_last_val=280727)


# mysql OK:
def test_mysql():
  conn_url = "jdbc:mysql://localhost:3306/mysql"
  password = "mysql"
  user = "root"

  testSelectQuery(conn_url=conn_url, password=password, user=user, query="SELECT * FROM citibike_new ORDER BY tripduration", exp_first_val=60, exp_last_val=280727)


#sql server NOK, reproduces:
def test_sqlserver():
  conn_url = "jdbc:sqlserver://localhost:1433;database=master;encrypt=true;trustServerCertificate=true;"
  password = "<YourStrong!Passw0rd>"
  user = "SA"

  # - citi_sql and citi_sql_single_fm on MS SQL reproduces sql error:
  #   SQLException: Incorrect syntax near the keyword 'SELECT'. (=wrong syntax in water.jdbc.SQLManager.createTempTableSql,
  #   but when syntax fixed the order is not preserved)
  # - citi_sql_single_fm_no_tmp and citi_sql_single_fm_no_tmp2 on MS SQL reproduces sql error:
  #   Caused by: com.microsoft.sqlserver.jdbc.SQLServerException: The ORDER BY clause is invalid in views, inline functions,
  #   derived tables, subqueries, and common table expressions, unless TOP, OFFSET or FOR XML is also specified.
  testSelectQuery(conn_url=conn_url, password=password, user=user, query="SELECT * FROM citibike_new ORDER BY tripduration", exp_first_val=60, exp_last_val=280727)

 # testSelectQueries(conn_url=conn_url, password=password, user=user)

# maria db NOK, reproduces:
def test_mariadb():
  conn_url = "jdbc:mariadb://localhost:3307/mysql"
  password = "mypass"
  user = "root"

  # citi_sql and citi_sql_single_fm ok
  # citi_sql_single_fm_no_tmp and citi_sql_single_fm_no_tmp2 not failing but ORDER BY clause was ignored
  testSelectQuery(conn_url=conn_url, password=password, user=user, query="SELECT * FROM citibike_new ORDER BY tripduration", exp_first_val=60, exp_last_val=280727)


  # fix for maria db: not doing sub select but temp view
  # fix for sql server: adding 'offset 0 rows' to  each order by
  # adding 'offset 0 rows' solves the problem on sql server db but not on maria db
  # fix for sql server with use_temp_table=True:
  #   temp table has to be created with content from select by 'select into' statement and 'select into' doesn't support order by,
  #   so fallback to use_temp_table=False and tmp view creation which supports order by
def testSelectQuery(conn_url, password, user, query, exp_first_val, exp_last_val):

  citi_sql = h2o.import_sql_select(conn_url, query, user, password, use_temp_table=True)
  assert  citi_sql.as_data_frame().at[0, 'tripduration'] == exp_first_val
  assert citi_sql.as_data_frame().at[citi_sql.nrow - 1, 'tripduration'] == exp_last_val

  citi_sql_single_fm = h2o.import_sql_select(conn_url, query, user, password, fetch_mode='SINGLE')
  assert  citi_sql_single_fm.as_data_frame().at[0, 'tripduration'] == exp_first_val
  assert citi_sql_single_fm.as_data_frame().at[citi_sql_single_fm.nrow - 1, 'tripduration'] == exp_last_val

  citi_sql_single_fm_no_tmp = h2o.import_sql_select(conn_url, query, user, password, fetch_mode='SINGLE', use_temp_table=False)
  assert  citi_sql_single_fm_no_tmp.as_data_frame().at[0, 'tripduration'] == exp_first_val
  assert citi_sql_single_fm_no_tmp.as_data_frame().at[citi_sql_single_fm_no_tmp.nrow - 1, 'tripduration'] == exp_last_val


pyunit_utils.run_tests([
   # test_postgre,
   # test_mysql,
   # test_sqlserver,
   # test_mariadb,
])
