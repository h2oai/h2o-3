package hex.genmodel.algos.targetencoder;

import hex.ModelCategory;
import hex.genmodel.GenModel;
import hex.genmodel.MojoPreprocessor;
import hex.genmodel.easy.*;
import hex.genmodel.easy.exception.PredictException;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static hex.genmodel.algos.targetencoder.TargetEncoderMojoModel.name2Idx;

class TargetEncoderAsModelProcessor implements MojoPreprocessor.ModelProcessor {

    private final GenModel _model;
    private final TargetEncoderMojoModel _teModel;
    private final Map<String, Integer> _teColumnsToOffset = new HashMap<>();
    private final Map<Integer, CategoricalEncoder> _teOffsetToEncoder = new HashMap<>();
    private final Map<String, String[]> _columnsToDomainAfterTE = new LinkedHashMap<>();

    public TargetEncoderAsModelProcessor(TargetEncoderMojoModel teModel, GenModel model) {
        _teModel = teModel;
        _model = model;
        fillMaps();
    }

    @Override
    public RowToRawDataConverter makeRowConverter(EasyPredictModelWrapper.ErrorConsumer errorConsumer,
                                                  EasyPredictModelWrapper.Config config) {
        return new TargetEncoderRowToRawDataConverter(
                _teModel, 
                _teColumnsToOffset,
                _teOffsetToEncoder,
                errorConsumer,
                config
        );
    }

    @Override
    public GenModel getProcessedModel() {
        String[] newNames = _columnsToDomainAfterTE.keySet().toArray(new String[0]);
        String[][] newDomains = _columnsToDomainAfterTE.values().toArray(new String[0][]);
        return new VirtualTargetEncodedModel(_model, newNames, newDomains, _model.getResponseName());
    }

    public void fillMaps() {
        final String[] origNames = _model.getOrigNames();
        final String[][] origDomainValues = _model.getOrigDomainValues();
        final String[] namesAfterAllEnc = _model.getNames();
        final String[] responseDomain = _model.getDomainValues(_model.getResponseIdx());
        final Map<String, Integer> nameToIdx = name2Idx(_model.getOrigNames());
        List<String> sanitizedResponseDomain = new ArrayList<>(responseDomain==null ? 0:responseDomain.length);
        if (responseDomain!=null)
            for (String clas : responseDomain) sanitizedResponseDomain.add(sanitize(clas));

        //trying to sort columns in the same way as in TargetEncoderModel#reorderColumns:
        // 1. non-categorical predictors
        // 2. TE-encoded predictors
        // 3. remaining categorical predictors are excluded (they will be encoded by the next preprocessor or categorical encoder)
        int offset = 0;
        final Set<Integer> catPredictors = new TreeSet<>();
        for (int i = 0; i < _model.getOrigNumCols(); i++) { //adding non-categoricals
            if (origDomainValues[i]!=null) {
                catPredictors.add(i);
                continue;
            }
            if (_teModel._nonPredictors.contains(origNames[i])) continue; //non-predictors (fold, offset, weights...) are added to the end
            
//            _teColumnsToOffset.put(origNames[i], offset++);
            offset++;
            _columnsToDomainAfterTE.put(origNames[i], null);
        }
        for (Map.Entry<String, EncodingMap> colToEncodings : _teModel._encodingsByCol.entrySet()) { //adding TE encoded cols: iteration order enforced by TEModel.
            String teCatPredictor = colToEncodings.getKey();
            EncodingMap encodings = colToEncodings.getValue();

            int oriIdx = nameToIdx.get(teCatPredictor);
            String[] domain = origDomainValues[oriIdx];
            _teColumnsToOffset.put(origNames[oriIdx], offset);
            _teOffsetToEncoder.put(offset, new TargetEncoderAsCategoricalEncoder(_teModel, encodings, teCatPredictor, offset, domain));
            offset += _teModel.getNumEncColsPerPredictor();
            for (String teCol : findTEEncodedColumnFor(teCatPredictor, namesAfterAllEnc, sanitizedResponseDomain)) {
                _columnsToDomainAfterTE.put(teCol, null);
            }
            if (!_teModel._keepOriginalCategoricalColumns) catPredictors.remove(oriIdx);
        }

        for (int idx : catPredictors) { //adding remaining categorical predictors
//        _teColumnsToOffset.put(origNames[idx], offset);
//        offset++;
            _columnsToDomainAfterTE.put(origNames[idx], origDomainValues[idx]);
        }
        for (String col : _teModel._nonPredictors) {
            if (!col.equals(_teModel.getResponseName()))
                _columnsToDomainAfterTE.put(col, null);
        }
    }

    private List<String> findTEEncodedColumnFor(String column, String[] names, List<String> responseDomain) {
        List<String> result = new ArrayList<>();
        if (responseDomain.size() <= 2) { // regression + binary
            for (String name : names) {
                if ((column + "_te").equals(name)) {
                    result.add(name);
                    break;
                }
            }
        } else { // multiclass
            Pattern p = Pattern.compile(column + "_(\\w+)_te"); // target domain value extracted in group 1
            for (String name : names) {
                Matcher m = p.matcher(name);
                if (m.matches() && responseDomain.contains(m.group(1))) result.add(name);
            }
        }
        return result;
    }
    
    private static String sanitize(String s) {
        char[] cs = s.toCharArray();
        for (int i=1; i<cs.length; i++)
            if (!Character.isJavaIdentifierPart(cs[i]))
                cs[i] = '_';
        return new String(cs);
    }

    public static class TargetEncoderAsCategoricalEncoder implements CategoricalEncoder {
    
        private final TargetEncoderMojoModel _teModel;
        private final EncodingMap _encodings;
        private final String _columnName;
        private final int _offsetIndex;
        private final Map<String, Integer> _domainMap;
        
        public TargetEncoderAsCategoricalEncoder(TargetEncoderMojoModel teModel, 
                                                 EncodingMap encodings, String columnName, 
                                                 int offsetIndex, String[] domainValues) {
            _teModel = teModel;
            _encodings = encodings;
            _columnName = columnName;
            _offsetIndex = offsetIndex;
            _domainMap = new HashMap<>(domainValues.length);
            for (int j = 0; j < domainValues.length; j++) {
                _domainMap.put(domainValues[j], j);
            }
        }
    
        @Override
        public boolean encodeCatValue(String level, double[] rawData) {
            Integer category = _domainMap.get(level);
            if (category == null) return false;
            _teModel.encodeCategory(rawData, _offsetIndex, _encodings, category);
            return true;
        }
    
        @Override
        public void encodeNA(double[] rawData) {
            _teModel.encodeNA(rawData, _offsetIndex, _encodings, _columnName);
        }
        
    }

    public static class TargetEncoderRowToRawDataConverter extends DefaultRowToRawDataConverter<TargetEncoderMojoModel> {
        
        private final TargetEncoderMojoModel _teModel;
        
        
        public TargetEncoderRowToRawDataConverter(TargetEncoderMojoModel teModel,
                                                  Map<String, Integer> columnToOffsetIdx,
                                                  Map<Integer, CategoricalEncoder> offsetToEncoder,
                                                  EasyPredictModelWrapper.ErrorConsumer errorConsumer,
                                                  EasyPredictModelWrapper.Config config) {
            // domainMap is ignored here, however we need the TE columns to be enum encoded to be able to apply TE.
            super(columnToOffsetIdx,
                  offsetToEncoder,
                  errorConsumer, 
                  config);
            _teModel = teModel;
        }
    
        @Override
        public double[] convert(RowData data, double[] rawData) throws PredictException {
            double[] converted = super.convert(data, rawData);
            if (!_teModel._keepOriginalCategoricalColumns) {
                for (String teColumn : _teModel._encodingsByCol.keySet()) data.remove(teColumn);
            }
            return converted;
        }
    }

    public static class VirtualTargetEncodedModel extends GenModel {
        
        GenModel _m;
        
        public VirtualTargetEncodedModel(GenModel m, String[] names, String[][] domains, String responseColumn) {
            super(names, domains, responseColumn);
            _m = m;
        }
    
        @Override
        public ModelCategory getModelCategory() {
            return _m.getModelCategory();
        }
    
        @Override
        public String getUUID() {
            return _m.getUUID();
        }
    
        @Override
        public double[] score0(double[] row, double[] preds) {
            throw new IllegalStateException("This virtual model should not be called for scoring");
    //        return _m.score0(row, preds);
        }
    
        @Override
        public int getOrigNumCols() {
            return getNumCols();
        }
    
        @Override
        public String[] getOrigNames() {
            return getNames();
        }
    
        @Override
        public String[][] getOrigDomainValues() {
            return getDomainValues();
        }
    
        @Override
        public double[] getOrigProjectionArray() {
            return _m.getOrigProjectionArray();
        }
    }
}
