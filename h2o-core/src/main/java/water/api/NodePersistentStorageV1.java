package water.api;

import water.Iced;

/**
* Created by rpeck on 11/30/14.
*/
public class NodePersistentStorageV1 extends Schema<Iced, NodePersistentStorageV1> {
  @API(help="Category name", required=false, direction=API.Direction.INOUT)
  String category;

  @API(help="Key name", required=false, direction=API.Direction.INOUT)
  String name;

  @API(help="Value", required=false, direction=API.Direction.INOUT)
  String value;

  @API(help="List of key names", required=false, direction=API.Direction.OUTPUT)
  String[] names;

//    @API(help="List of entries", required=false, direction=API.Direction.OUTPUT)
//    APIPojoListEntryV1[] entries;
}
