package water.hive;

import java.util.List;
import java.util.Map;

public interface HiveMetaData {
    
    Table getTable(String name) throws Exception;
    
    interface Storable {

        Map<String, String> getSerDeParams();

        String getLocation();

        String getSerializationLib();
        
        String getInputFormat();
        
    }
    
    interface Table extends Storable {
        
        String getName();
        
        boolean hasPartitions();
        
        List<Partition> getPartitions();
        
        List<Column> getColumns();
        
        List<Column> getPartitionKeys();
    }
    
    interface Column {
        
        String getName();
        
        String getType();
        
    }
    
    interface Partition extends Storable {
        
        List<String> getValues();
        
    }
    
}
