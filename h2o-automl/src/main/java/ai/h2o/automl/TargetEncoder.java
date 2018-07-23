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
     * @param foldColumn should contain index of column as String. TODO Change later into suitable type.
     */
    //TODO do we need to do this preparation before as a separate phase? because we are grouping twice.
    //TODO At least it seems that way in the case of KFold. But even if we need to preprocess for other types of TE calculations... we should not affect KFOLD case anyway.
    public Frame prepareEncodingMap(Frame data, String[] columnNamesToEncode, String targetColumnName, String foldColumn) {

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

        // Maybe we should not convert here anything because API for JAVA backend is unambiguous and takes only names of columns.

            /* Case when `columnsToEncode` columns are specified with indexes not names. Replace indexes with names
            if (is.numeric(unlist(x))) {
                x <- sapply(x, function(i) colnames(data)[i]) //TODO btw this is maybe not the very efficient way to get all column names in R.
                //TODO We should be able take them at once (not one by one).
            }*/
            //TODO code goes here


            /* Replace target index by target column name.
            if (is.numeric(y)) {
                y <- colnames(data)[y]
            }*/
            //TODO code goes here

            /* Again converting index to name for fold_column.
            if (is.numeric(fold_column)) {
                fold_column <- colnames(data)[fold_column]
            }*/
            //TODO code goes here

        // Encoding part

        // 1) For encoding we can use only rows with defined target column
        // R:  encoding_data <- data[!is.na(data[[y]]), ]

        filterOutNAsFromTargetColumn(data, targetIndex);

        // 2) Iterating over the encoding columns, grouping and calculating `numerator` and `denominator` for each group.
        // for (cols in x) {   TODO in R: cols -> col ?

        Frame allColumnsEncodedFrame = null;

        for ( String teColumnName: columnNamesToEncode) {
            Frame teColumnFrame = null;
            int colIndex = getColumnIndexByName(data, teColumnName);
            String tree = null;
            if(foldColumn == null) {
                tree = String.format("(GB %s [%d] sum %s \"all\" nrow %s \"all\")", data._key, colIndex, targetIndex, targetIndex);
            }
            else {
                tree = String.format("(GB %s [%d, %s] sum %s \"all\" nrow %s \"all\")", data._key, colIndex, foldColumn, targetIndex, targetIndex);
            }
            Val val = Rapids.exec(tree);
            teColumnFrame = val.getFrame();

            printOutFrameAsTable(teColumnFrame);

            teColumnFrame = renameColumn(teColumnFrame, "sum_"+ targetColumnName, "numerator");
            teColumnFrame = renameColumn(teColumnFrame, "nrow", "denominator");

            if(allColumnsEncodedFrame == null)
                allColumnsEncodedFrame = teColumnFrame;
            else
                allColumnsEncodedFrame.add(teColumnFrame); // TODO should we CBind frames or it is cheaper to collect an array/map ?
        }

        Key<Frame> inputForTargetEncoding = Key.make("inputForTargetEncoding");
        allColumnsEncodedFrame._key = inputForTargetEncoding;  // TODO should we set key here?
        DKV.put(inputForTargetEncoding, allColumnsEncodedFrame);

        return allColumnsEncodedFrame;
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

    public Frame prepareEncodingMap(Frame data, int[] columnIndexesToEncode, int targetIndex) {
        String [] columnNamesToEncode = getColumnNamesBy(data, columnIndexesToEncode);
        return prepareEncodingMap(data, columnNamesToEncode, getColumnNameBy(data, targetIndex), null);
    }

    public Frame prepareEncodingMap(Frame data, int[] columnIndexesToEncode, int targetIndex, String foldColumn) {
        String [] columnNamesToEncode = getColumnNamesBy(data, columnIndexesToEncode);
        return prepareEncodingMap(data, columnNamesToEncode, getColumnNameBy(data, targetIndex), foldColumn);
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

    public Frame groupByTEColumnAndAggregate(Frame outOfFold, int teColumnIndex, String numeratorColumnIndex, String denominatorColumnIndex) {
        String tree = String.format("(GB %s [%d] sum %s \"all\" sum %s \"all\")", outOfFold._key, teColumnIndex, numeratorColumnIndex, denominatorColumnIndex);
        Val val = Rapids.exec(tree);
        Frame resFrame = val.getFrame();
        Key<Frame> key = Key.make(outOfFold._key.toString() + "_groupped");
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

    public Frame substractTargetValueForLOO(Frame data, int numeratorIndex, int denominatorIndex, int targetIndex)  {

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
                                     String[] columnIndexesToEncode,
                                     String targetColumnName,
                                     Frame targetEncodingMap, // TODO should be a Map( te_column -> Frame )
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

        for ( String teColumnName: columnIndexesToEncode) {
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
                        Frame outOfFoldData = getOutOfFoldData(targetEncodingMap, "1", foldValue); // In targetEncodingMap it is always 1st column
                        System.out.println(" #### OutOfFold dataframe before grouping");
                        printOutFrameAsTable(outOfFoldData);
                        Frame groupedByTEColumnAndAggregate = groupByTEColumnAndAggregate(outOfFoldData, teColumnIndex, "2", "3");
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

                    teFrame = mergeByTEColumn(teFrame, targetEncodingMap, teColumnIndex, "0");

                    int numeratorIndex = getColumnIndexByName(teFrame,"numerator");
                    int denominatorIndex = getColumnIndexByName(teFrame,"denominator");
                    int targetColumnIndex = getColumnIndexByName(teFrame, targetColumnName);

                    teFrame = substractTargetValueForLOO(teFrame, numeratorIndex, denominatorIndex, targetColumnIndex);

                    break;
                case HoldoutType.None:
                default:
            }

            System.out.println(" #### After merging teFrame");

            printOutFrameAsTable(teFrame);

            int numeratorIndex = getColumnIndexByName(teFrame,"numerator");
            int denominatorIndex = getColumnIndexByName(teFrame,"denominator");
            if (withBlendedAvg) {
                teFrame = calculateAndAppendBlendedTEEncoding(teFrame, numeratorIndex, denominatorIndex,"target_encode" + teColumnIndex);

            } else {
                teFrame = calculateAndAppendTEEncoding(teFrame, numeratorIndex, denominatorIndex, "target_encode" + teColumnIndex);


                System.out.println(" #### After appending calculated TE encoding");

                printOutFrameAsTable(teFrame);
            }

            if(noiseLevel > 0) {
                teFrame = addNoise(teFrame, "target_encode0", noiseLevel, seed);
            }

        }

        return teFrame;
    }

    public Frame applyTargetEncoding(Frame data,
                                     String[] columnsToEncode,
                                     String targetColumnName,
                                     Frame targetEncodingMap,
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
                                     Frame targetEncodingMap,
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
                                     Frame targetEncodingMap,
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
                                     Frame targetEncodingMap, // TODO should be a Map( te_column -> Frame )
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
                                     Frame targetEncodingMap, // TODO should be a Map( te_column -> Frame )
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
