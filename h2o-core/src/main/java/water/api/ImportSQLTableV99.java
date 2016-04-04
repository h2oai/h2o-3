package water.api;


import water.Iced;

public class ImportSQLTableV99 extends RequestSchema<Iced,ImportSQLTableV99> {
  
  //Input fields
  @API(help="database_sys", required = true)
  String database_sys;

  @API(help="database", required = true)
  String database;

  @API(help="table", required = true)
  String table;

  @API(help="username", required = true)
  String username;

  @API(help="password", required = true)
  String password;

  @API(help="host")
  String host="localhost";

  @API(help="port")
  String port="3306";

  @API(help="optimize")
  boolean optimize = true;
  
}
