package hex.tree;

import water.Iced;
import water.util.ArrayUtils;
import water.util.IcedHashMap;
import water.util.IcedHashSet;
import water.util.IcedInt;

import java.util.*;

/**
 * Class to process global interaction constraints information and use this information for 
 * make a split decision in a tree.
 */
public class GlobalInteractionConstraints extends Iced<GlobalInteractionConstraints> {

    // Map where key is column index and value is a set of columns indices which can interact with the key column
    private IcedHashMap<IcedInt, IcedHashSet<IcedInt>> allowedInteractionMap; 
    
    public GlobalInteractionConstraints(String[][] userFeatureInteractions, String[] treeFeatureNames){
        this.allowedInteractionMap = new IcedHashMap<>();
        parseInteractionsIndices(userFeatureInteractions, treeFeatureNames);
        // There should be always at least one column index in the map as a key
        assert this.allowedInteractionMap != null;
        assert this.allowedInteractionMap.size() != 0;
    }

    /**
     * Parse input interaction constraints String array into Map to easy use for split decision.
     * @param userInteractionConstraints input interaction constraints String array
     * @param columnNames column names from used dataset for training to match indices correctly
     */
    private void parseInteractionsIndices(String[][] userInteractionConstraints, String[] columnNames){
        IcedHashSet<IcedInt> interactions;
        for (String[] list : userInteractionConstraints) {
            interactions = new IcedHashSet<>();
            for (int i = 0; i < list.length; i++) {
                String item = list[i];
                // first find only name
                int start = ArrayUtils.findWithPrefix(columnNames, item);
                // find start index and add indices until end index
                assert start != -1 : "Column name should be in defined column names.";
                if (start > -1) {               // find exact position - no encoding  
                    interactions.add(new IcedInt(start));
                } else {                       // find first occur of the name with prefix - encoding
                    start = - start - 2;
                    assert columnNames[start].startsWith(item): "The column name should be find correctly.";
                    // iterate until find all encoding indices
                    int end = start;
                    while (end < columnNames.length && columnNames[end].startsWith(item)) {
                        interactions.add(new IcedInt(end));
                        end++;
                    }
                }
            }
            addInteractionsSetToMap(interactions);
        }
    }

    private void addInteractionsSetToMap(IcedHashSet<IcedInt> interactions){
        for (IcedInt index : interactions) {
            if (!allowedInteractionMap.containsKey(index)) {
                allowedInteractionMap.put(index, interactions);
            } else {
                IcedHashSet<IcedInt> set = new IcedHashSet<>();
                set.addAll(allowedInteractionMap.get(index));
                set.addAll(interactions);
                allowedInteractionMap.put(index, set);
            }
        }
    }
    
    public IcedHashSet<IcedInt> getAllowedInteractionForIndex(int columnIndex){
        return allowedInteractionMap.get(new IcedInt(columnIndex));
    }
    
    public boolean allowedInteractionContainsColumn(int columnIndex){
        return allowedInteractionMap.containsKey(new IcedInt(columnIndex));
    }
    
    public IcedHashSet<IcedInt> getAllAllowedColumnIndices(){
        IcedHashSet<IcedInt> indices = new IcedHashSet<>();
        indices.addAll(allowedInteractionMap.keySet());
        return indices;
    }
}
