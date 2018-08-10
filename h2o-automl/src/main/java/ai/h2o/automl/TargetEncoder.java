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
     * @param columnNamesToEncode names of columns to apply target encoding to
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

    public Frame ensureTargetColumnIsNumericOrBinaryCategorical(Frame data, String targetColumnName) {
        return ensureTargetColumnIsNumericOrBinaryCategorical(data, getColumnIndexByName(data, targetColumnName));
    };

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

    public Map<String, Frame> prepareEncodingMap(Frame data, int[] columnIndexesToEncode, int targetIndex, String foldColumnName) {
        String [] columnNamesToEncode = getColumnNamesBy(data, columnIndexesToEncode);
        return prepareEncodingMap(data, columnNamesToEncode, getColumnNameBy(data, targetIndex), foldColumnName);
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
        String tree = String.format("(rows %s  (!! (is.na (cols %s [%s] ) ) ) )", data._key, data._key, targetIndex);
        Val val = Rapids.exec(tree);
        if (val instanceof ValFrame)
            data = val.getFrame();

        return data;
    }

    public Frame transformBinaryTargetColumn(Frame data, int targetIndex)  {

        Vec targetVec = data.vec(targetIndex);
        String[] domains = targetVec.domain();
//        assert domains[1].equals("YES"); // TODO remove because we don't have YES/NO levels all the time. R's implementation relies on the fact that target class is always domain[1]. But who guarantees that?
        String tree = String.format("(:= %s (ifelse (is.na (cols %s [%d] ) ) NA (ifelse (== (cols %s [%d] ) '%s' ) 1.0 0.0 ) )  [%d] [] )",
                data._key, data._key, targetIndex,  data._key, targetIndex, domains[1], targetIndex);
        Val val = Rapids.exec(tree);
        Frame res = val.getFrame();
        res._key = data._key;
        DKV.put(res._key , res);
        return res;
    }

    public Frame appendBinaryTargetColumn(Frame data, int targetIndex)  {

        Vec targetVec = data.vec(targetIndex);
        String[] domains = targetVec.domain();
        String tree = String.format("(append %s (ifelse (is.na (cols %s [%d] ) ) NA (ifelse (== (cols %s [%d] ) '%s' ) 1.0 0.0 ) )  'appended' )",
                data._key, data._key, targetIndex,  data._key, targetIndex, domains[1], targetIndex);
        Val val = Rapids.exec(tree);
        Frame res = val.getFrame();
        res._key = data._key;
        DKV.put(res._key , res);
        return res;
    }

    public Frame getOutOfFoldData(Frame data, int foldColumnIndex, long currentFoldValue)  {

        String tree = String.format("(rows %s (!= (cols %s [%d] ) %d ) )", data._key, data._key, foldColumnIndex, currentFoldValue);
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

    public Frame mergeByTEColumnAndFold(Frame a, Frame b, int teColumnIndexOriginal, int foldColumnIndexOriginal, int teColumnIndex, int foldColumnIndex ) {
        String tree = String.format("(merge %s %s TRUE FALSE [%d, %d] [%d, %d] 'auto' )", a._key, b._key, teColumnIndexOriginal, foldColumnIndexOriginal, teColumnIndex, foldColumnIndex);
        Val val = Rapids.exec(tree);
        Frame res = val.getFrame();
        res._key = a._key;
        DKV.put(a._key, res);
        return res;
    }

    public Frame mergeByTEColumn(Frame a, Frame b, int teColumnIndexOriginal, int teColumnIndex) {
        String tree = String.format("(merge %s %s TRUE FALSE [%d] [%d] 'auto' )", a._key, b._key, teColumnIndexOriginal, teColumnIndex);
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
    public double calculateGlobalMean(Frame fr) {
        int numeratorIndex = getColumnIndexByName(fr,"numerator");
        int denominatorIndex = getColumnIndexByName(fr,"denominator");
        String tree = String.format("( / (sum (cols %s [%d] )) (sum (cols %s [%d] )) )", fr._key, numeratorIndex, fr._key, denominatorIndex);
        Val val = Rapids.exec(tree);
        return val.getNum();
    }

    public Frame calculateAndAppendBlendedTEEncoding(Frame fr, Frame encodingMap, String appendedColumnName ) {
        // TODO check support for denominator = 0
        double globalMean = calculateGlobalMean(encodingMap);
        int numeratorIndex = getColumnIndexByName(fr,"numerator");
        int denominatorIndex = getColumnIndexByName(fr,"denominator");

        int k = 20;
        int f = 10;
        String expTerm = String.format("(exp ( / ( - %d (cols %s [%s] )) %d ))", k, fr._key, denominatorIndex, f);
        String lambdaTree = String.format("(  / 1     ( + 1 %s  )  ) ", expTerm);

        String treeForLambda_1 = String.format(" ( * ( - 1 %s ) %f)", lambdaTree, globalMean);
        String treeForLambda_2 = String.format("( * %s  ( / (cols %s [%s]) (cols %s [%s])  )  )", lambdaTree, fr._key,  numeratorIndex, fr._key, denominatorIndex);
        String treeForLambda = String.format("( append %s ( + %s  %s )  '%s' )", fr._key, treeForLambda_1, treeForLambda_2, appendedColumnName);
        return Rapids.exec(treeForLambda).getFrame();
    }

    public Frame calculateAndAppendTEEncoding(Frame fr, Frame encodingMap, String targetColumnName, String appendedColumnName ) {
        // TODO int valueForSingleItemGroups = ??? ;
        // These groups have this singleness in common and we probably want to represent it somehow.
        // If we choose just global average then we just lose difference between single-row-groups that have different target values.
        // We can:  1) calculate averages per target value.   ( num. / denom. where target = [0,1] ).
        //              Group is so small that we even don't want to care about te_column's values.... just averages per target column's values.
        //         2) use  #single-item-groups_target0 / #of_targets_with_target0
        //         3) Count single-row-groups and calculate    #of_single_rows_with_target0 / #all_single_rows  ;  (and the same for target1)
        //TODO Introduce parameter for algorithm that will choose the way of calculating new value.
        int targetColumnIndex = getColumnIndexByName(fr, targetColumnName);
        int numeratorIndex = getColumnIndexByName(fr,"numerator");
        int denominatorIndex = getColumnIndexByName(fr,"denominator");
        double globalMeanForTargetClass = calculateGlobalMean(encodingMap);
        double globalMeanForNonTargetClass = 1 - globalMeanForTargetClass;
        String tree = String.format("( append %s ( ifelse ( == (cols %s [%d]) 0 ) ( ifelse ( == (cols %s [%d]) 1) %f  %f) ( / (cols %s [%d]) (cols %s [%d])) ) '%s' )",
                fr._key , fr._key, denominatorIndex, fr._key, targetColumnIndex, globalMeanForTargetClass, globalMeanForNonTargetClass,  fr._key, numeratorIndex, fr._key, denominatorIndex, appendedColumnName);
//        String tree = String.format("( append %s ( ifelse ( == (cols %s [%d]) 0 ) 1 ( / (cols %s [%d]) (cols %s [%d])) ) '%s' )",
//                fr._key , fr._key, denominatorIndex,  fr._key, numeratorIndex, fr._key, denominatorIndex, appendedColumnName);
        Val val = Rapids.exec(tree);
        Frame res = val.getFrame();
        res._key = fr._key;
        DKV.put(fr._key, res);
        return res;
    }

    public Frame addNoise(Frame fr, String applyToColumnName, double noiseLevel, double seed) {
        int appyToColumnIndex = getColumnIndexByName(fr, applyToColumnName);
        String tree = String.format("(:= %s (+ (cols %s [%d] ) (- (* (* (h2o.runif %s %f ) 2.0 ) %f ) %f ) ) [%d] [] )", fr._key, fr._key, appyToColumnIndex, fr._key, seed, noiseLevel, noiseLevel, appyToColumnIndex);
        Val val = Rapids.exec(tree);
        return val.getFrame();
    }

    public Frame subtractTargetValueForLOO(Frame data, String targetColumnName)  {
        int numeratorIndex = getColumnIndexByName(data,"numerator");
        int denominatorIndex = getColumnIndexByName(data,"denominator");
        int targetIndex = getColumnIndexByName(data, targetColumnName);

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

        //TODO Should we remove string columns from `data` as it is done in R version (see: https://0xdata.atlassian.net/browse/PUBDEV-5266) ?

        Frame teFrame = new Frame(data);
        teFrame._key = Key.make(data._key.toString() + "_applyTE");
        DKV.put(teFrame._key, teFrame);

        teFrame = ensureTargetColumnIsNumericOrBinaryCategorical(teFrame, targetColumnName);

        for ( String teColumnName: columnsToEncode) {
            Frame targetEncodingMap = columnToEncodingMap.get(teColumnName);

            int targetEncodingMapNumeratorIdx = getColumnIndexByName(targetEncodingMap,"numerator");
            int targetEncodingMapDenominatorIdx = getColumnIndexByName(targetEncodingMap,"denominator");

            int teColumnIndex = getColumnIndexByName(teFrame, teColumnName);
            int teColumnIndexInEncodingMap = getColumnIndexByName(targetEncodingMap, teColumnName);
            Frame holdoutEncodeMap = null;

            switch( holdoutType ) {
                case HoldoutType.KFold:
                    if(foldColumnName == null)
                        throw new IllegalStateException("`foldColumn` must be provided for holdoutType = KFold");

                    int foldColumnIndex = getColumnIndexByName(teFrame, foldColumnName);
                    long[] foldValues = getUniqueValuesOfTheFoldColumn(targetEncodingMap, 1);

                    for(long foldValue : foldValues) { // TODO what if our te column is not represented in every foldValue? Then when merging with original dataset we will get NA'a on the right side
                        int foldColumnIndexInEncodingMap = getColumnIndexByName(targetEncodingMap, foldColumnName);
                        assert foldColumnIndexInEncodingMap != -1 : "Target encoding map was created without fold, so KFold holdout_type can't be applied.";
                        printOutFrameAsTable(targetEncodingMap);
                        Frame outOfFoldData = getOutOfFoldData(targetEncodingMap, foldColumnIndexInEncodingMap, foldValue);

                        System.out.println(" #### OutOfFold dataframe before grouping");
                        printOutFrameAsTable(outOfFoldData);
                        int numeratorColumnIndex = getColumnIndexByName(outOfFoldData, "numerator");
                        int denominatorColumnIndex = getColumnIndexByName(outOfFoldData, "denominator");
                        Frame groupedByTEColumnAndAggregate = groupByTEColumnAndAggregate(outOfFoldData, teColumnIndexInEncodingMap, numeratorColumnIndex, denominatorColumnIndex);

                        System.out.println(" #### OutOfFold dataframe after grouping");
                        printOutFrameAsTable(groupedByTEColumnAndAggregate);
                        groupedByTEColumnAndAggregate = renameColumn(groupedByTEColumnAndAggregate, "sum_numerator", "numerator");
                        groupedByTEColumnAndAggregate = renameColumn(groupedByTEColumnAndAggregate, "sum_denominator", "denominator");

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

                    int foldColumnIndexInEncodingMap = getColumnIndexByName(holdoutEncodeMap, "foldValueForMerge");
                    teFrame = mergeByTEColumnAndFold(teFrame, holdoutEncodeMap, teColumnIndex, foldColumnIndex, teColumnIndexInEncodingMap, foldColumnIndexInEncodingMap);

                    break;
                case HoldoutType.LeaveOneOut:

                    targetEncodingMap = groupingIgnoringFordColumn(foldColumnName, targetEncodingMap, targetEncodingMapNumeratorIdx, targetEncodingMapDenominatorIdx, teColumnName);
//                    printOutFrameAsTable(targetEncodingMap, true, true);

//                    System.out.println(" #### Merging targetEncodingMap to teFrame");

                    teFrame = mergeByTEColumn(teFrame, targetEncodingMap, teColumnIndex, teColumnIndexInEncodingMap);

//                    System.out.println(" #### After merging teFrame");
//                    printOutFrameAsTable(teFrame);

                    teFrame = subtractTargetValueForLOO(teFrame,  targetColumnName);
//                    System.out.println(" #### After subtractTargetValueForLOO teFrame");
//                    printOutFrameAsTable(teFrame, false, true);

                    break;
                case HoldoutType.None:
                    // TODO we'd better don't group it with folds during creation of targetEncodingMap
                    targetEncodingMap = groupingIgnoringFordColumn(foldColumnName, targetEncodingMap, targetEncodingMapNumeratorIdx, targetEncodingMapDenominatorIdx, teColumnName);

                    printOutFrameAsTable(targetEncodingMap);
                    teFrame = mergeByTEColumn(teFrame, targetEncodingMap, teColumnIndex, teColumnIndexInEncodingMap);
            }

            if (withBlendedAvg) {
                teFrame = calculateAndAppendBlendedTEEncoding(teFrame, targetEncodingMap, teColumnName + "_te");

            } else {

                teFrame = calculateAndAppendTEEncoding(teFrame, targetEncodingMap, targetColumnName,teColumnName + "_te");

//                System.out.println(" #### After appending calculated TE encoding");
//                printOutFrameAsTable(teFrame, true, true);
            }

            if(noiseLevel > 0) {
                teFrame = addNoise(teFrame, teColumnName + "_te", noiseLevel, seed);
            }

            teFrame.remove("numerator");
            teFrame.remove("denominator");
        }

//        System.out.println(" #### Final result of applying TE encoding");
//        printOutFrameAsTable(teFrame);

        return teFrame;
    }

    private Frame groupingIgnoringFordColumn(String foldColumnName, Frame targetEncodingMap, int targetEncodingMapNumeratorIdx, int targetEncodingMapDenominatorIdx, String teColumnName) {
        if(foldColumnName != null) { // TODO we can't rely only on absence of the column name passed. User is able not to provide foldColumn name to apply method.
          System.out.println(" #### Grouping (back) targetEncodingMap without folds");
          int teColumnIndex = getColumnIndexByName(targetEncodingMap, teColumnName);

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
    private void printOutFrameAsTable(Frame fr, boolean full, boolean rollups) {

        TwoDimTable twoDimTable = fr.toTwoDimTable(0, 10000, rollups);
        System.out.println(twoDimTable.toString(2, full));
    }
}
