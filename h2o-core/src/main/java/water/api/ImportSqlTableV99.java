package water.api;


import water.Iced;

public class ImportSQLTableV99 extends RequestSchema<Iced,ImportSQLTableV99> {
  
  //Input fields
  @API(help="database_sys", required = true)
  String database_sys;

  @API(help="host", required = true)
  String host;
  
  @API(help="port", required = true)
  String port;
  
  @API(help="database", required = true)
  String database;
  
  @API(help="table", required = true)
  String table;
  
  @API(help="username", required = true)
  String username;
  
  @API(help="password", required = true)
  String password;
  
  //Output fields
  @API(help="name", direction = API.Direction.OUTPUT)
  String destination_frame;
  
}
