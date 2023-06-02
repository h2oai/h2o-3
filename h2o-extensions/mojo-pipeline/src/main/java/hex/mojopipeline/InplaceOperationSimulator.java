package hex.mojopipeline;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

// MOJO2 doesn't support inplace conversions. The inplace function will be simulated via additional temporary columns
public class InplaceOperationSimulator {
    HashMap<String, String> _originalToReplacementMapping = new HashMap<>();
    List<String> _replacementNames = new ArrayList<>();
    List<String> _replacementTypes = new ArrayList<>();
    

    public String updateColumn(String column) {
        String tempColumn = _originalToReplacementMapping.get(column);
        if (tempColumn == null) {
            return column;
        } else {
            return tempColumn;
        }
    }
    
    public void setNewReplacement(String originalName, String replacementName, String replacementType) {
        _replacementNames.add(replacementName);
        _replacementTypes.add(replacementType);
        _originalToReplacementMapping.put(originalName, replacementName);
    }
    
    public String[] getReplacementColumnNames() { return _replacementNames.toArray(new String[0]);}

    public String[] getReplacementColumnTypes() { return _replacementTypes.toArray(new String[0]);}
}
