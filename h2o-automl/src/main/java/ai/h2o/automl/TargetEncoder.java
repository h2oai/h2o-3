package ai.h2o.automl;

import water.DKV;
import water.Key;
import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.Rapids;
import water.rapids.Val;
import water.rapids.vals.ValFrame;
import water.util.TwoDimTable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

// TODO probably should call this logic from FrameUtils
public class TargetEncoder {

    public static class HoldoutType {
        public static final byte LeaveOneOut  =  0;
        public static final byte KFold  =  1;
        public static final byte None  =  2;
    }

    /**
     *
     * @param data
     * @param columnNamesToEncode
     * @param targetColumnName target column index
     * @param foldColumnName should contain index of column as String. TODO Change later into suitable type.
     */
    //TODO do we need to do this preparation before as a separate phase? because we are grouping twice.
    //TODO At least it seems that way in the case of KFold. But even if we need to preprocess for other types of TE calculations... we should not affect KFOLD case anyway.
    public Map<String, Frame> prepareEncodingMap(Frame data, String[] columnNamesToEncode, String targetColumnName, String foldColumnName) {

        //Validate input data. Not sure whether we should check some of these.

        if(data == null) throw new IllegalStateException("Argument 'data' is missing, with no default");

        if(columnNamesToEncode == null || columnNamesToEncode.length == 0)
            throw new IllegalStateException("Argument 'columnsToEncode' is not defined or empty");

        if(targetColumnName == null || targetColumnName.equals(""))
            throw new IllegalStateException("Argument 'target' is missing, with no default");

        if(! checkAllTEColumnsAreCategorical(data, columnNamesToEncode))
            throw new IllegalStateException("Argument 'columnsToEncode' should contain only names of categorical columns");

        int targetIndex = getColumnIndexByName(data, targetColumnName);

        data = ensureTargetColumnIsNumericOrBinaryCategorical(data, targetIndex);

        if(Arrays.asList(columnNamesToEncode).contains(targetColumnName)) {
            throw new IllegalArgumentException("Columns for target encoding contain target column.");
        }

        filterOutNAsFromTargetColumn(data, targetIndex);

        Map<String, Frame> columnToEncodingMap = new HashMap<String, Frame>();

        for ( String teColumnName: columnNamesToEncode) { // TODO maybe we can do it in parallel
            Frame teColumnFrame = null;
            int colIndex = getColumnIndexByName(data, teColumnName);
            String tree = null;
            if (foldColumnName == null) {
                tree = String.format("(GB %s [%d] sum %s \"all\" nrow %s \"all\")", data._key, colIndex, targetIndex, targetIndex);
            } else {
                int foldColumnIndex = getColumnIndexByName(data, foldColumnName);

                tree = String.format("(GB %s [%d, %d] sum %s \"all\" nrow %s \"all\")", data._key, colIndex, foldColumnIndex, targetIndex, targetIndex);
            }
            Val val = Rapids.exec(tree);
            teColumnFrame = val.getFrame();
            teColumnFrame._key = Key.make(data._key.toString() + "_" + teColumnName + "_encodingMap");
            DKV.put(teColumnFrame._key, teColumnFrame);

            teColumnFrame = renameColumn(teColumnFrame, "sum_"+ targetColumnName, "numerator");
            teColumnFrame = renameColumn(teColumnFrame, "nrow", "denominator");

            columnToEncodingMap.put(teColumnName, teColumnFrame);
        }

        return columnToEncodingMap;
    }

    public Frame ensureTargetColumnIsNumericOrBinaryCategorical(Frame data, int targetIndex) {
        if (data.vec(targetIndex).isCategorical()){
            Vec targetVec = data.vec(targetIndex);
            if(targetVec.cardinality() == 2) {
                return transformBinaryTargetColumn(data, targetIndex);
            }
            else {
                throw new IllegalStateException("`target` must be a binary vector");
            }
        }
        else {
            if(! data.vec(targetIndex).isNumeric()) {
                throw new IllegalStateException("`target` must be a numeric or binary vector");
            }
            return data;
        }
    };

    public Map<String, Frame> prepareEncodingMap(Frame data, int[] columnIndexesToEncode, int targetIndex) {
        String [] columnNamesToEncode = getColumnNamesBy(data, columnIndexesToEncode);
        return prepareEncodingMap(data, columnNamesToEncode, getColumnNameBy(data, targetIndex), null);
    }

    public Map<String, Frame> prepareEncodingMap(Frame data, int[] columnIndexesToEncode, int targetIndex, String foldColumn) {
        String [] columnNamesToEncode = getColumnNamesBy(data, columnIndexesToEncode);
        return prepareEncodingMap(data, columnNamesToEncode, getColumnNameBy(data, targetIndex), foldColumn);
    }

    public Map<String, Frame> prepareEncodingMap(Frame data, int[] columnIndexesToEncode, int targetIndex, int foldColumnIndex) {
        String [] columnNamesToEncode = getColumnNamesBy(data, columnIndexesToEncode);
        String foldColumnName = getColumnNameBy(data, foldColumnIndex);
        return prepareEncodingMap(data, columnNamesToEncode, getColumnNameBy(data, targetIndex), foldColumnName);
    }

    public String[] getColumnNamesBy(Frame data, int[] columnIndexes) {
        String [] allColumnNames = data._names.clone();
        ArrayList<String> columnNames = new ArrayList<String>();

        for(int idx : columnIndexes) {
            columnNames.add(allColumnNames[idx]);
        }
        return columnNames.toArray(new String[columnIndexes.length]);
    }
    public String getColumnNameBy(Frame data, int columnIndex) {
        String [] allColumnNames = data._names.clone();
        return allColumnNames[columnIndex];
    }

    public Frame renameColumn(Frame fr, int indexOfColumnToRename, String newName) {
        String[] names = fr.names();
        names[indexOfColumnToRename] = newName;
        fr.setNames(names);
        return fr;
    }

    public Frame renameColumn(Frame fr, String oldName, String newName) {
        return renameColumn(fr, getColumnIndexByName(fr, oldName), newName);
    }

    public int getColumnIndexByName(Frame fr, String name) {
        String[] names = fr.names();
        return Arrays.asList(names).indexOf(name);
    }

    public Frame filterOutNAsFromTargetColumn(Frame data, int targetIndex)  {
        // Option 1 ?
        // Why the name of the method is DEEP-select? Is it really that deep? Or it is just usual select?

        //      Frame result = new Frame.DeepSelect().doAll(Vec.T_CAT, data).outputFrame();

        // Option 2
        String tree = String.format("(rows %s  (!! (is.na (cols %s [%s] ) ) ) )", data._key, data._key, targetIndex);
        Val val = Rapids.exec(tree);
        if (val instanceof ValFrame)
            data = val.getFrame();

        return data;
    }

    public Frame transformBinaryTargetColumn(Frame data, int targetIndex)  {

        Vec targetVec = data.vec(targetIndex);
        String[] domains = targetVec.domain();
        String tree = String.format("(:= %s (ifelse (is.na (cols %s [%d] ) ) NA (ifelse (== (cols %s [%d] ) '%s' ) 1.0 0.0 ) )  [%d] [] )",
                data._key, data._key, targetIndex,  data._key, targetIndex, domains[1], targetIndex);
        Val val = Rapids.exec(tree);
        Frame res = val.getFrame();
        res._key = data._key;
        DKV.put(res._key , res);
        return res;
    }

    public Frame getOutOfFoldData(Frame data, String foldColumn, long currentFoldValue)  {

        String tree = String.format("(rows %s (!= (cols %s [%s] ) %d ) )", data._key, data._key, foldColumn, currentFoldValue);
        Val val = Rapids.exec(tree);
        Frame outOfFoldFrame = val.getFrame();
        Key<Frame> outOfFoldKey = Key.make(data._key.toString() + "_outOfFold-" + currentFoldValue);
        outOfFoldFrame._key = outOfFoldKey;
        DKV.put(outOfFoldKey, outOfFoldFrame);
        return outOfFoldFrame;
    }

    private long[] getUniqueValuesOfTheFoldColumn(Frame data, int columnIndex) {
        String tree = String.format("(unique (cols %s [%d]))", data._key, columnIndex);
        Val val = Rapids.exec(tree);
        printOutFrameAsTable(val.getFrame());
        Vec uniqueValues = val.getFrame().vec(0);
        int length = (int) uniqueValues.length(); // We assume that fold column should not has many different values and we will fit into node's memory
        long[] uniqueValuesArr = new long[length];
        for(int i = 0; i < uniqueValues.length(); i++) {
            uniqueValuesArr[i] = uniqueValues.at8(i);
        }
        return uniqueValuesArr;
    }

    private boolean checkAllTEColumnsAreCategorical(Frame data, String[] columnsToEncode)  {
        for( String columnName : columnsToEncode) {
            int columnIndex = getColumnIndexByName(data, columnName);
            if(! data.vec(columnIndex).isCategorical()) return false;
        }
        return true;
    }

    public Frame groupByTEColumnAndAggregate(Frame fr, int teColumnIndex, int numeratorColumnIndex, int denominatorColumnIndex) {
        String tree = String.format("(GB %s [%d] sum %d \"all\" sum %d \"all\")", fr._key, teColumnIndex, numeratorColumnIndex, denominatorColumnIndex);
        Val val = Rapids.exec(tree);
        Frame resFrame = val.getFrame();
        Key<Frame> key = Key.make(fr._key.toString() + "_groupped");
        resFrame._key = key;
        DKV.put(key, resFrame);
        return resFrame;
    }

    public Frame rBind(Frame a, Frame b) {
        Frame rBindRes = null;
        if(a == null) {
            assert b != null;
            rBindRes = b;
        } else {
            String tree = String.format("(rbind %s %s)", a._key, b._key);
            Val val = Rapids.exec(tree);
            rBindRes = val.getFrame();
        }
        Key<Frame> key = Key.make("holdoutEncodeMap");
        rBindRes._key = key;
        DKV.put(key, rBindRes);
        return rBindRes;
    }

    public Frame mergeByTEColumnAndFold(Frame a, Frame b, int teColumnIndexOriginal, int foldColumnIndexOriginal, String teColumnIndex, String foldColumnIndex ) {
        String tree = String.format("(merge %s %s TRUE FALSE [%d, %d] [%s, %s] 'auto' )", a._key, b._key, teColumnIndexOriginal, foldColumnIndexOriginal, teColumnIndex, foldColumnIndex);
        Val val = Rapids.exec(tree);
        Frame res = val.getFrame();
        res._key = a._key;
        DKV.put(a._key, res);
        return res;
    }

    public Frame mergeByTEColumn(Frame a, Frame b, int teColumnIndexOriginal, String teColumnIndex) {
        String tree = String.format("(merge %s %s TRUE FALSE [%d] [%s] 'auto' )", a._key, b._key, teColumnIndexOriginal, teColumnIndex);
        Val val = Rapids.exec(tree);
        Frame res = val.getFrame();
        res._key = a._key;
        DKV.put(a._key, res);
        return res;
    }

    public Frame appendColumn(Frame a, long columnValue, String appendedColumnName ) {
        String tree = String.format("( append %s %d '%s' )", a._key , columnValue, appendedColumnName);
        Val val = Rapids.exec(tree);
        Frame withAppendedColumn = val.getFrame();
        withAppendedColumn._key = a._key;  // TODO should we set key here?
        DKV.put(a._key, withAppendedColumn);
        return withAppendedColumn;
    }

    // Maybe it's better to calculate mean before any aggregations?
    public double calculateGlobalMean(Frame a, int numeratorIndex, int denominatorIndex) {
        String tree = String.format("( / (sum (cols %s [%d] )) (sum (cols %s [%d] )) )", a._key, numeratorIndex, a._key, denominatorIndex);
        Val val = Rapids.exec(tree);
        return val.getNum();
    }

    public Frame calculateAndAppendBlendedTEEncoding(Frame a, int numeratorIndex, int denominatorIndex, String appendedColumnName ) {
        double globalMean = calculateGlobalMean(a, numeratorIndex, denominatorIndex);

        int k = 20;
        int f = 10;
        String expTerm = String.format("(exp ( / ( - %d (cols %s [%s] )) %d ))", k, a._key, denominatorIndex, f);
        String lambdaTree = String.format("(  / 1     ( + 1 %s  )  ) ", expTerm);

        String treeForLambda_1 = String.format(" ( * ( - 1 %s ) %f)", lambdaTree, globalMean);
        String treeForLambda_2 = String.format("( * %s  ( / (cols %s [%s]) (cols %s [%s])  )  )", lambdaTree, a._key,  numeratorIndex, a._key, denominatorIndex);
        String treeForLambda = String.format("( append %s ( + %s  %s )  '%s' )", a._key, treeForLambda_1, treeForLambda_2, appendedColumnName);
        return Rapids.exec(treeForLambda).getFrame();
    }

    public Frame calculateAndAppendTEEncoding(Frame a, int numeratorIndex, int denominatorIndex, String appendedColumnName ) {
        String tree = String.format("( append %s ( / (cols %s [%d]) (cols %s [%d])) '%s' )",a._key , a._key,  numeratorIndex, a._key, denominatorIndex, appendedColumnName);
        Val val = Rapids.exec(tree);
        Frame res = val.getFrame();
        res._key = a._key;
        DKV.put(a._key, res);
        return res;
    }

    public Frame addNoise(Frame fr, String applyToColumnName, double noiseLevel, double seed) {
        int appyToColumnIndex = getColumnIndexByName(fr, applyToColumnName);
        String tree = String.format("(:= %s (+ (cols %s [%d] ) (- (* (* (h2o.runif %s %f ) 2.0 ) %f ) %f ) ) [%d] [] )", fr._key, fr._key, appyToColumnIndex, fr._key, seed, noiseLevel, noiseLevel, appyToColumnIndex);
        Val val = Rapids.exec(tree);
        return val.getFrame();
    }

    public Frame subtractTargetValueForLOO(Frame data, int numeratorIndex, int denominatorIndex, int targetIndex)  {

        String treeNumerator = String.format("(:= %s (ifelse (is.na (cols %s [%d] ) )   (cols %s [%d] )   (- (cols %s [%d] )  (cols %s [%d] )  ) )  [%d] [] )",
                data._key, data._key, targetIndex, data._key, numeratorIndex, data._key, numeratorIndex, data._key, targetIndex,  numeratorIndex);
        Val val = Rapids.exec(treeNumerator);
        Frame res = val.getFrame();
        res._key = data._key;
        DKV.put(res._key, res);

        String treeDenominator = String.format("(:= %s (ifelse (is.na (cols %s [%d] ) )   (cols %s [%d] )   (- (cols %s [%d] )  1  ) )  [%d] [] )",
                data._key, data._key, targetIndex, data._key, denominatorIndex, data._key, denominatorIndex, denominatorIndex);
        Frame finalFrame = Rapids.exec(treeDenominator).getFrame();

        finalFrame._key = data._key;
        DKV.put(finalFrame._key, finalFrame);
        return finalFrame;
    }

    public Frame applyTargetEncoding(Frame data,
                                     String[] columnsToEncode,
                                     String targetColumnName,
                                     Map<String, Frame> columnToEncodingMap,
                                     byte holdoutType,
                                     String foldColumnName,
                                     boolean withBlendedAvg,
                                     double noiseLevel,
                                     double seed) {

        if(noiseLevel < 0 )
            throw new IllegalStateException("`noiseLevel` must be non-negative");

        //TODO add validation checks as in preparation phase. Validation and test frames should comply with the same requirements as training ones.

        //TODO Should we remove string columns from `data` as it is done in R version (see: https://0xdata.atlassian.net/browse/PUBDEV-5266) ?

        Frame teFrame = data;

        for ( String teColumnName: columnsToEncode) {
            Frame targetEncodingMap = columnToEncodingMap.get(teColumnName);

            int targetEncodingMapNumeratorIdx = getColumnIndexByName(targetEncodingMap,"numerator");
            int targetEncodingMapDenominatorIdx = getColumnIndexByName(targetEncodingMap,"denominator");

            int teColumnIndex = getColumnIndexByName(data, teColumnName);
            Frame holdoutEncodeMap = null;

            switch( holdoutType ) {
                case HoldoutType.KFold:
                    if(foldColumnName == null)
                        throw new IllegalStateException("`foldColumn` must be provided for holdoutType = KFold");

                    int foldColumnIndex = getColumnIndexByName(data, foldColumnName);
                    // I assume here that fold column is numerical not categorical. Otherwise we could calculate it with following piece of code.
                    // String[] folds = targetEncodingMap.vec(Integer.parseInt(foldColumn)).domain();
                    long[] foldValues = getUniqueValuesOfTheFoldColumn(targetEncodingMap, Integer.parseInt("1")); // "1" fold column in targetEncodingMap

                    for(long foldValue : foldValues) { // TODO what if our te column is not represented in every foldValue? Then when merging with original dataset we will get NA'a on the right side
                        Frame outOfFoldData = getOutOfFoldData(targetEncodingMap, "1", foldValue); // TODO In targetEncodingMap it is always 1st column. Change
                        System.out.println(" #### OutOfFold dataframe before grouping");
                        printOutFrameAsTable(outOfFoldData);
                        Frame groupedByTEColumnAndAggregate = groupByTEColumnAndAggregate(outOfFoldData, teColumnIndex, 2, 3);
                        System.out.println(" #### groupedByTEColumnAndAggregate dataframe");
                        printOutFrameAsTable(groupedByTEColumnAndAggregate);
                        groupedByTEColumnAndAggregate = appendColumn(groupedByTEColumnAndAggregate, foldValue, "foldValueForMerge"); // TODO for now we don't need names for columns since we are working with indices

                        holdoutEncodeMap = rBind(holdoutEncodeMap, groupedByTEColumnAndAggregate);

                        TwoDimTable twoDimTable = holdoutEncodeMap.toTwoDimTable();
                        System.out.println(String.format("Column with foldValueForMerge=%d were appended.", foldValue) + twoDimTable.toString());
                    }

                    System.out.println(" #### Merging holdoutEncodeMap to teFrame");

                    printOutFrameAsTable(teFrame);

                    printOutFrameAsTable(holdoutEncodeMap);

                    teFrame = mergeByTEColumnAndFold(teFrame, holdoutEncodeMap, teColumnIndex, foldColumnIndex, "0", "3");

                    break;
                case HoldoutType.LeaveOneOut:
                    System.out.println(" #### Merging targetEncodingMap to teFrame");

                    printOutFrameAsTable(teFrame);

                    printOutFrameAsTable(targetEncodingMap);

                    // TODO we'd better don't group it with folds during creation of targetEncodingMap
                    targetEncodingMap = groupingIgnoringFordColumn(foldColumnName, targetEncodingMap, targetEncodingMapNumeratorIdx, targetEncodingMapDenominatorIdx, teColumnName);
                    printOutFrameAsTable(targetEncodingMap);

                    teFrame = mergeByTEColumn(teFrame, targetEncodingMap, teColumnIndex, "0");

                    int numeratorIndex = getColumnIndexByName(teFrame,"numerator");
                    int denominatorIndex = getColumnIndexByName(teFrame,"denominator");
                    int targetColumnIndex = getColumnIndexByName(teFrame, targetColumnName);

                    teFrame = subtractTargetValueForLOO(teFrame, numeratorIndex, denominatorIndex, targetColumnIndex);

                    break;
                case HoldoutType.None:
                    // TODO we'd better don't group it with folds during creation of targetEncodingMap
                    targetEncodingMap = groupingIgnoringFordColumn(foldColumnName, targetEncodingMap, targetEncodingMapNumeratorIdx, targetEncodingMapDenominatorIdx, teColumnName);

                    teFrame = mergeByTEColumn(teFrame, targetEncodingMap, teColumnIndex, "0");
            }

            System.out.println(" #### After merging teFrame");

            printOutFrameAsTable(teFrame);

            int numeratorIndex = getColumnIndexByName(teFrame,"numerator");
            int denominatorIndex = getColumnIndexByName(teFrame,"denominator");
            if (withBlendedAvg) {
                teFrame = calculateAndAppendBlendedTEEncoding(teFrame, numeratorIndex, denominatorIndex, teColumnName + "_te");

            } else {
                teFrame = calculateAndAppendTEEncoding(teFrame, numeratorIndex, denominatorIndex, teColumnName + "_te");


                System.out.println(" #### After appending calculated TE encoding");

                printOutFrameAsTable(teFrame);
            }

            if(noiseLevel > 0) {
                teFrame = addNoise(teFrame, teColumnName + "_te", noiseLevel, seed);
            }

            teFrame.remove("numerator");
            teFrame.remove("denominator");
        }

        printOutFrameAsTable(teFrame);

        return teFrame;
    }

    private Frame groupingIgnoringFordColumn(String foldColumnName, Frame targetEncodingMap, int targetEncodingMapNumeratorIdx, int targetEncodingMapDenominatorIdx, String teColumnName) {
        System.out.println(" #### Grouping (back) targetEncodingMap without folds");
        int teColumnIndex = getColumnIndexByName(targetEncodingMap, teColumnName);
        if(foldColumnName != null) { // TODO we can't rely only on absence of the column name passed. User is able not to provide foldColumn name to apply method.
            targetEncodingMap = groupByTEColumnAndAggregate(targetEncodingMap, teColumnIndex, targetEncodingMapNumeratorIdx, targetEncodingMapDenominatorIdx);
            targetEncodingMap = renameColumn(targetEncodingMap, "sum_numerator", "numerator");
            targetEncodingMap = renameColumn(targetEncodingMap, "sum_denominator", "denominator");
        }
        return targetEncodingMap;
    }

    public Frame applyTargetEncoding(Frame data,
                                     String[] columnsToEncode,
                                     String targetColumnName,
                                     Map<String, Frame> targetEncodingMap,
                                     byte holdoutType,
                                     String foldColumn,
                                     boolean withBlendedAvg) {
        double defaultNoiseLevel = 0.01;
        double noiseLevel = 0.0;
        int targetIndex = getColumnIndexByName(data, targetColumnName);
        Vec targetVec = data.vec(targetIndex);
        if(targetVec.isNumeric()) {
            noiseLevel = defaultNoiseLevel * (targetVec.max() - targetVec.min());
        } else {
            noiseLevel = defaultNoiseLevel;
        }
        return this.applyTargetEncoding(data, columnsToEncode, targetColumnName, targetEncodingMap, holdoutType, foldColumn, withBlendedAvg, noiseLevel, 1234.0);
    }

    public Frame applyTargetEncoding(Frame data,
                                     int[] columnIndexesToEncode,
                                     int targetIndex,
                                     Map<String, Frame> targetEncodingMap,
                                     byte holdoutType,
                                     int foldColumnIndex,
                                     boolean withBlendedAvg) {
        String[] columnNamesToEncode = getColumnNamesBy(data, columnIndexesToEncode);
        String targetColumnName = getColumnNameBy(data, targetIndex);
        String foldColumnName = getColumnNameBy(data, foldColumnIndex);
        return this.applyTargetEncoding(data, columnNamesToEncode, targetColumnName, targetEncodingMap, holdoutType, foldColumnName, withBlendedAvg);

    }

    public Frame applyTargetEncoding(Frame data,
                                     int[] columnIndexesToEncode,
                                     int targetIndex,
                                     Map<String, Frame> targetEncodingMap,
                                     byte holdoutType,
                                     int foldColumnIndex,
                                     boolean withBlendedAvg,
                                     double noiseLevel,
                                     double seed) {
        String[] columnNamesToEncode = getColumnNamesBy(data, columnIndexesToEncode);
        String targetColumnName = getColumnNameBy(data, targetIndex);
        String foldColumnName = getColumnNameBy(data, foldColumnIndex);
        return this.applyTargetEncoding(data, columnNamesToEncode, targetColumnName, targetEncodingMap, holdoutType, foldColumnName, withBlendedAvg, noiseLevel, seed);

    }

    public Frame applyTargetEncoding(Frame data,
                                     int[] columnIndexesToEncode,
                                     int targetColumnIndex,
                                     Map<String, Frame> targetEncodingMap,
                                     byte holdoutType,
                                     boolean withBlendedAvg,
                                     double noiseLevel,
                                     double seed) {
        String[] columnNamesToEncode = getColumnNamesBy(data, columnIndexesToEncode);
        String targetColumnName = getColumnNameBy(data, targetColumnIndex);
        return applyTargetEncoding(data, columnNamesToEncode, targetColumnName, targetEncodingMap, holdoutType, withBlendedAvg, noiseLevel, seed);
    }

    public Frame applyTargetEncoding(Frame data,
                                     String[] columnNamesToEncode,
                                     String targetColumnName,
                                     Map<String, Frame> targetEncodingMap,
                                     byte holdoutType,
                                     boolean withBlendedAvg,
                                     double noiseLevel,
                                     double seed) {
        assert holdoutType != HoldoutType.KFold : "Use another overloaded method for KFold holdout type.";
        return applyTargetEncoding(data, columnNamesToEncode, targetColumnName, targetEncodingMap, holdoutType, null, withBlendedAvg, noiseLevel, seed);
    }

    // TODO remove.
    private void printOutFrameAsTable(Frame fr) {

        TwoDimTable twoDimTable = fr.toTwoDimTable();
        System.out.println(twoDimTable.toString());
    }
}
