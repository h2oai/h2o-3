package hex.genmodel.descriptor.models.convert;

import hex.genmodel.descriptor.models.Common;

import java.util.Arrays;

public class TableConverter {
  
  public static Common.Table convert(final hex.genmodel.descriptor.Table table){
    final Common.Table.Builder builder = Common.Table.newBuilder()
            .addAllColumnHeader(Arrays.asList(table.getColHeaders()))
            .addAllRowHeader(Arrays.asList(table.getRowHeaders()))
            .setDescription(table.getTableDescription())
            .setHeader(table.getTableHeader());
    
    for (int i = 0; i < table.columns(); i++) {
      builder.addColumnType(Common.Table.ColumnType.valueOf(table.getColTypes()[i].toString()));
      
      final Common.StringList.Builder columnValuesBuilder = Common.StringList.newBuilder();
      for (int j = 0; j < table.getCellValues()[i].length; j++) {
        columnValuesBuilder.addValue(table.getCell(i,j).toString());
      }
      builder.addColumnValues(columnValuesBuilder.build());
    }
            
    return builder.build();    
  }
}
