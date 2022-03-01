package hex.util;

import hex.DataInfo;
import hex.Interaction;
import hex.Model;
import water.util.ArrayUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class InteractionConstraintsUtils {
    
    public static FeatureProperties assembleFeatureNames(final DataInfo di) {
        String[] coefnames = di.coefNames();
        assert (coefnames.length == di.fullN());
        int numCatCols = di._catOffsets[di._catOffsets.length - 1];

        String[] featureNames = new String[di.fullN()];
        boolean[] oneHotEncoded = new boolean[di.fullN()];
        int[] originalColumnIndices = di.coefOriginalColumnIndices();
        for (int i = 0; i < di.fullN(); i++) {
            featureNames[i] = coefnames[i];
            if (i < numCatCols) {
                oneHotEncoded[i] = true;
            }
        }
        return new FeatureProperties(di._adaptedFrame._names, featureNames, oneHotEncoded, originalColumnIndices);
    }

    public static class FeatureProperties {
        public String[] _originalNames;
        public Map<String, Integer> _originalNamesMap;
        public String[] _names;
        public boolean[] _oneHotEncoded;
        public int[] _originalColumnIndices;

        public FeatureProperties(String[] originalNames, String[] names, boolean[] oneHotEncoded, int[] originalColumnIndices) {
            _originalNames = originalNames;
            _originalNamesMap = new HashMap<>();
            for(int i = 0; i < originalNames.length; i++){
                _originalNamesMap.put(originalNames[i], i);
            }
            _names = names;
            _oneHotEncoded = oneHotEncoded;
            _originalColumnIndices = originalColumnIndices;
        }

        public int getOriginalIndex(String originalName){
            return _originalNamesMap.get(originalName);
        }

        public Integer[] mapOriginalNamesToIndices(String[] names){
            Integer[] res = new Integer[names.length];
            for(int i = 0; i<names.length; i++){
                res[i] = getOriginalIndex(names[i]);
            }
            return res;
        }
    }

    public static int[][] createInteractionsIndices(String[][] interaction_constraints, String[] coefNames, Model.Parameters params){
        int[][] interactionsIndices = new int[interaction_constraints.length][];
        Set<Integer> interactions;
        for (String[] list : interaction_constraints) {
            interactions = new HashSet<>();
            for (int i = 0; i < list.length; i++) {
                String item = list[i];
                if(item.equals(params._response_column)){
                    throw new IllegalArgumentException("'interaction_constraints': Column with the name '" + item + "'is used as response column and cannot be used in interaction.");
                }
                if(item.equals(params._weights_column)){
                    throw new IllegalArgumentException("'interaction_constraints': Column with the name '" + item + "'is used as weights column and cannot be used in interaction.");
                }
                if(item.equals(params._fold_column)){
                    throw new IllegalArgumentException("'interaction_constraints': Column with the name '" + item + "'is used as fold column and cannot be used in interaction.");
                }
                if(params._ignored_columns != null && ArrayUtils.find(params._ignored_columns, item) != -1) {
                    throw new IllegalArgumentException("'interaction_constraints': Column with the name '" + item + "'is set in ignored columns and cannot be used in interaction.");
                }
                // first find only name
                int start = ArrayUtils.findWithPrefix(coefNames, item);
                // find start index and add indices until end index
                if (start == -1) {
                    throw new IllegalArgumentException("'interaction_constraints': Column with name '" + item + "' is not in the frame.");
                } else if(start > -1){               // find exact position - no encoding  
                    interactions.add(start);
                } else {              // find first occur of the name with prefix - encoding
                    start = -start - 2;
                    assert coefNames[start].startsWith(item): "The column name should be find correctly.";
                    // iterate until find all encoding indices
                    int end = start;
                    while (end < coefNames.length && coefNames[end].startsWith(item)) {
                        interactions.add(end);
                        end++;
                    }
                }
                interactionsIndices[i] = interactions.stream().mapToInt(Integer::intValue).toArray();
            }
        }
        return interactionsIndices;
    }

}
