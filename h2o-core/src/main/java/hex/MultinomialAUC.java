package hex;

import water.Iced;
import water.util.Log;
import water.util.TwoDimTable;

public class MultinomialAUC extends Iced {
    public SimpleAUC[] _ovrAucs;
    public PairwiseAUC[] _ovoAucs;
    public final MultinomialAucType _default_auc_type;
    public final String[] _domain;
    public static final int MAX_AUC_CLASSES = 50;
    public final boolean _calculateAuc;

    // keep this final aggregate value outside to save time
    public final double _macroOvrAuc;
    public final double _weightedOvrAuc;
    public final double _macroOvoAuc;
    public final double _weightedOvoAuc;

    public final double _macroOvrAucPr;
    public final double _weightedOvrAucPr;
    public final double _macroOvoAucPr;
    public final double _weightedOvoAucPr;


    public MultinomialAUC(AUC2.AUCBuilder[] ovrAucs, AUC2.AUCBuilder[][] ovoAucs, String[] domain, boolean zeroWeights, MultinomialAucType type){
        _default_auc_type = type;
        _domain = domain;
        int domainLength = _domain.length;
        _calculateAuc = !_default_auc_type.equals(MultinomialAucType.AUTO) && !_default_auc_type.equals(MultinomialAucType.NONE) && domainLength <= MAX_AUC_CLASSES;
        if(_calculateAuc) {
            _ovoAucs = new PairwiseAUC[(domainLength * domainLength - domainLength) / 2];
            _ovrAucs = new SimpleAUC[domainLength];
            int aucsIndex = 0;
            if (!zeroWeights) {
                for (int i = 0; i < domainLength - 1; i++) {
                    AUC2 tmpAucObject = ovrAucs[i]._n > 0 ? new AUC2(ovrAucs[i]) : new AUC2();
                    _ovrAucs[i] = new SimpleAUC(tmpAucObject._auc, tmpAucObject._pr_auc, tmpAucObject._p, tmpAucObject._n+tmpAucObject._p);
                    for (int j = i + 1; j < domainLength; j++) {
                        AUC2 first = ovoAucs[i][j]._n > 0 ? new AUC2(ovoAucs[i][j]) : new AUC2();
                        AUC2 second = ovoAucs[j][i]._n > 0 ? new AUC2(ovoAucs[j][i]) : new AUC2();
                        _ovoAucs[aucsIndex++] = new PairwiseAUC(first, second, _domain[i], _domain[j]);
                    }
                }
                AUC2 tmpAucObject = ovrAucs[domainLength - 1]._n > 0 ? new AUC2(ovrAucs[domainLength - 1]) : new AUC2();
                _ovrAucs[domainLength - 1] = new SimpleAUC(tmpAucObject._auc, tmpAucObject._pr_auc, tmpAucObject._p, tmpAucObject._n+tmpAucObject._p);
            } else {
                for (int i = 0; i < ovoAucs.length - 1; i++) {
                    _ovrAucs[i] = new SimpleAUC();
                    for (int j = i + 1; j < ovoAucs[0].length; j++) {
                        if (i < j) {
                            _ovoAucs[aucsIndex++] = new PairwiseAUC(new AUC2(), new AUC2(), _domain[i], _domain[j]);
                        }
                    }
                }
                _ovrAucs[domainLength - 1] = new SimpleAUC();
            }
            _macroOvoAuc = computeOvoMacroAuc(false);
            _weightedOvoAuc = computeOvoWeightedAuc(false);
            _macroOvrAuc = computeOvrMacroAuc(false);
            _weightedOvrAuc = computeOvrWeightedAuc(false);

            _macroOvoAucPr = computeOvoMacroAuc(true);
            _weightedOvoAucPr = computeOvoWeightedAuc(true);
            _macroOvrAucPr = computeOvrMacroAuc(true);
            _weightedOvrAucPr = computeOvrWeightedAuc(true);
        } else { // else no result for multinomial AUC - memory issue
            _macroOvoAuc = Double.NaN;
            _weightedOvoAuc = Double.NaN;
            _macroOvrAuc = Double.NaN;
            _weightedOvrAuc = Double.NaN;

            _macroOvoAucPr = Double.NaN;
            _weightedOvoAucPr = Double.NaN;
            _macroOvrAucPr = Double.NaN;
            _weightedOvrAucPr = Double.NaN;
        }
    }

    public MultinomialAUC(TwoDimTable aucTable, TwoDimTable aucprTable, String[] domain, MultinomialAucType type){
        _default_auc_type = type;
        _domain = domain;
        if(_domain.length < MAX_AUC_CLASSES) {
            int domainLength = _domain.length;
            _ovoAucs = new PairwiseAUC[(domainLength * domainLength - domainLength) / 2];
            _ovrAucs = new SimpleAUC[domainLength];
            int aucsIndex = 0;
            for (int i = 0; i < _ovrAucs.length; i++) {
                AUC2 auc = new AUC2();
                auc._auc = (double) aucTable.get(i,3);
                auc._pr_auc = (double) aucprTable.get(i,3);
                _ovrAucs[i] = new SimpleAUC(auc._auc, auc._pr_auc, 0, 0);
            }
            _macroOvrAuc = (double) aucTable.get(_ovrAucs.length,3);
            _weightedOvrAuc = (double) aucTable.get(_ovrAucs.length + 1,3);

            _macroOvrAucPr = (double) aucprTable.get(_ovrAucs.length,3);
            _weightedOvrAucPr = (double) aucprTable.get(_ovrAucs.length + 1,3);

            int lastOvoIndex = _ovrAucs.length + _ovoAucs.length + 2;
            for (int j = _ovrAucs.length + 2; j < lastOvoIndex; j++) {
                _ovoAucs[aucsIndex++] = new PairwiseAUC((double) aucTable.get(j, 3),   /*AUC*/
                                                        (double) aucprTable.get(j, 3), /*PR AUC*/
                                                        (String) aucTable.get(j, 1)   /*first domain*/,
                                                        (String) aucTable.get(j, 2)   /*second domain*/);
            }
            _macroOvoAuc = (double)aucTable.get(lastOvoIndex, 3);
            _weightedOvoAuc = (double) aucTable.get(lastOvoIndex + 1, 3);

            _macroOvoAucPr = (double)aucprTable.get(lastOvoIndex, 3);
            _weightedOvoAucPr = (double)aucprTable.get(lastOvoIndex, 3);
            _calculateAuc = true;
        } else { // else no result for multinomial AUC - memory issue
            _macroOvoAuc = Double.NaN;
            _weightedOvoAuc = Double.NaN;
            _macroOvrAuc = Double.NaN;
            _weightedOvrAuc = Double.NaN;

            _macroOvoAucPr = Double.NaN;
            _weightedOvoAucPr = Double.NaN;
            _macroOvrAucPr = Double.NaN;
            _weightedOvrAucPr = Double.NaN;
            _calculateAuc = false;
        }
    }
    
    public double auc() {
        switch (_default_auc_type) {
            case MACRO_OVR:
                return getMacroOvrAuc();
            case MACRO_OVO:
                return getMacroOvoAuc();
            case WEIGHTED_OVO:
                return getWeightedOvoAuc();
            case WEIGHTED_OVR:
                return getWeightedOvrAuc();
            default: 
                return Double.NaN;
        }
    }

    public double pr_auc() {
        switch (_default_auc_type) {
            case MACRO_OVR:
                return get_macroOvrAucPr();
            case MACRO_OVO:
                return getMacroOvoAucPr();
            case WEIGHTED_OVO:
                return getWeightedOvoAucPr();
            default:
                return getWeightedOvrAucPr();
        }
    }

    public double computeOvrMacroAuc(boolean isPr){
        double macroAuc = 0;
        for(SimpleAUC ovrAuc : _ovrAucs){
            macroAuc += isPr ? ovrAuc.aucpr() : ovrAuc.auc();
        }
        return macroAuc/_ovrAucs.length;
    }

    public double computeOvrWeightedAuc(boolean isPr){
        double weightedAuc = 0;
        double sumWeights = 0;
        for(SimpleAUC ovrAuc : _ovrAucs){
            double positives = ovrAuc.positives();
            sumWeights += positives;
            weightedAuc += isPr ? ovrAuc.aucpr() * positives : ovrAuc.auc() * positives;
        }
        return weightedAuc/sumWeights;
    }

    public double computeOvoMacroAuc(boolean isPr){
        double macroAuc = 0;
        for(PairwiseAUC ovoAuc : _ovoAucs){
            macroAuc += isPr ? ovoAuc.getPrAuc() : ovoAuc.getAuc();
        }
        return macroAuc/_ovoAucs.length;
    }

    public double computeOvoWeightedAuc(boolean isPr){
        double n = _ovrAucs[0].ncases();
        double weightedAuc = 0;
        double sumWeights = 0;
        for(PairwiseAUC ovoAuc : _ovoAucs){
            double weight = ovoAuc.getSumPositives() / n;
            weightedAuc += isPr ? ovoAuc.getPrAuc() * weight : ovoAuc.getAuc() * weight;
            sumWeights += weight;
        }
        return weightedAuc/sumWeights;
    }

    public double getMacroOvrAuc() {
        return _macroOvrAuc;
    }

    public double getWeightedOvrAuc() {
        return _weightedOvrAuc;
    }

    public double getMacroOvoAuc() {
        return _macroOvoAuc;
    }

    public double getWeightedOvoAuc() {
        return _weightedOvoAuc;
    }

    public double get_macroOvrAucPr() {
        return _macroOvrAucPr;
    }

    public double getWeightedOvrAucPr() {
        return _weightedOvrAucPr;
    }

    public double getMacroOvoAucPr() {
        return _macroOvoAucPr;
    }

    public double getWeightedOvoAucPr() {
        return _weightedOvoAucPr;
    }

    public TwoDimTable getAucTable(){
        return getTable(false);
    }
    
    public TwoDimTable getAucPrTable(){
        return getTable(true);
    }
    
    public TwoDimTable getTable(boolean isPr) {
        if(_calculateAuc) {
            String metric = isPr ? "auc_pr" : "AUC";
            String tableHeader = "Multinomial " + metric + " values";
            int rows = _ovrAucs.length + _ovoAucs.length + 4 /*2 + 2 weighted aucs*/;
            String[] rowHeaders = new String[rows];
            for (int i = 0; i < _ovrAucs.length; i++)
                rowHeaders[i] = _domain[i] + " vs Rest";
            rowHeaders[_ovrAucs.length] = "Macro OVR";
            rowHeaders[_ovrAucs.length + 1] = "Weighted OVR";
            for (int i = 0; i < _ovoAucs.length; i++)
                rowHeaders[_ovrAucs.length + 2 + i] = _ovoAucs[i].getPairwiseDomainsString();
            rowHeaders[rows - 2] = "Macro OVO";
            rowHeaders[rows - 1] = "Weighted OVO";
            String[] colHeaders = new String[]{"First class domain", "Second class domain", metric};
            String[] colTypes = new String[]{"String", "String", "double"};
            String[] colFormats = new String[]{"%s", "%s", "%d"};
            String colHeaderForRowHeaders = "Type";
            TwoDimTable table = new TwoDimTable(tableHeader, null, rowHeaders, colHeaders, colTypes, colFormats, colHeaderForRowHeaders);
            double sumWeights = 0;
            for (int i = 0; i < _ovrAucs.length; i++) {
                SimpleAUC auc = _ovrAucs[i];
                double aucValue = isPr ? auc.aucpr() : auc.auc();
                table.set(i, 0, _domain[i]);
                table.set(i, 2, aucValue);
            }
            table.set(_ovrAucs.length, 2, isPr ? _macroOvrAucPr : _macroOvrAuc);
            table.set(_ovrAucs.length + 1, 2, isPr ? _weightedOvrAucPr : _weightedOvrAuc);
            sumWeights = 0;
            for (int i = 0; i < _ovoAucs.length; i++) {
                PairwiseAUC auc = _ovoAucs[i];
                double aucValue = isPr ? auc.getPrAuc() : auc.getAuc();
                table.set(_ovrAucs.length + 2 + i, 0, auc.getDomainFirst());
                table.set(_ovrAucs.length + 2 + i, 1, auc.getDomainSecond());
                table.set(_ovrAucs.length + 2 + i, 2, aucValue);
            }
            table.set(rows - 2, 2, isPr ? _macroOvoAucPr : _macroOvoAuc);
            table.set(rows - 1, 2, isPr ? _weightedOvoAucPr : _weightedOvoAuc);
            return table;
        } else {
            return null;
        }
    }
}

/**
 * Simple AUC object to store only auc and aucpr and other important values to save memory
 */
class SimpleAUC extends Iced {
    private final double _auc;
    private final double _aucpr;
    private final double _positives;
    private final double _ncases;

    public SimpleAUC(double auc, double aucpr, double positives, double n) {
        this._auc = auc;
        this._aucpr = aucpr;
        this._positives = positives;
        this._ncases = n;
    }

    public SimpleAUC() {
        this._auc = Double.NaN;
        this._aucpr = Double.NaN;
        this._positives = Double.NaN;
        this._ncases = Double.NaN;
    }


    public double auc() {
        return _auc;
    }

    public double aucpr() {
        return _aucpr;
    }


    public double positives() {
        return _positives;
    }

    public double ncases() {
        return _ncases;
    }
}

