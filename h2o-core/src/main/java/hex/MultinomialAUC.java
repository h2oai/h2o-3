package hex;

import water.Iced;
import water.util.TwoDimTable;

public class MultinomialAUC extends Iced {
    public AUC2[] _ovrAucs;
    public PairwiseAUC[] _ovoAucs;
    public final MultinomialAucType _default_auc_type;
    public final String[] _domain;
    public final static int MAX_DOMAIN_SIZE = 100;
    public final boolean _calculateAuc;

    // keep this final aggregate value outside to save time
    public final double macroOvrAuc;
    public final double weightedOvrAuc;
    public final double macroOvoAuc;
    public final double weightedOvoAuc;

    public final double macroOvrAucPr;
    public final double weightedOvrAucPr;
    public final double macroOvoAucPr;
    public final double weightedOvoAucPr;


    public MultinomialAUC(AUC2.AUCBuilder[] ovrAucs, AUC2.AUCBuilder[][] ovoAucs, String[] domain, boolean zeroWeights, MultinomialAucType type){
        _default_auc_type = type;
        _domain = domain;
        int domainLength = _domain.length;
        if(domainLength < MAX_DOMAIN_SIZE) {
            _ovoAucs = new PairwiseAUC[(domainLength * domainLength - domainLength) / 2];
            _ovrAucs = new AUC2[domainLength];
            int aucsIndex = 0;
            if (!zeroWeights) {
                for (int i = 0; i < domainLength - 1; i++) {
                    _ovrAucs[i] = ovrAucs[i]._n > 0 ? new AUC2(ovrAucs[i]) : new AUC2();
                    for (int j = i + 1; j < domainLength; j++) {
                        AUC2 first = ovoAucs[i][j]._n > 0 ? new AUC2(ovoAucs[i][j]) : new AUC2();
                        AUC2 second = ovoAucs[j][i]._n > 0 ? new AUC2(ovoAucs[j][i]) : new AUC2();
                        _ovoAucs[aucsIndex++] = new PairwiseAUC(first, second, _domain[i], _domain[j]);
                    }
                }
                _ovrAucs[domainLength - 1] = ovrAucs[domainLength - 1]._n > 0 ? new AUC2(ovrAucs[domainLength - 1]) : new AUC2();
            } else {
                for (int i = 0; i < ovoAucs.length - 1; i++) {
                    _ovrAucs[i] = new AUC2();
                    for (int j = i + 1; j < ovoAucs[0].length; j++) {
                        if (i < j) {
                            _ovoAucs[aucsIndex++] = new PairwiseAUC(new AUC2(), new AUC2(), _domain[i], _domain[j]);
                        }
                    }
                }
                _ovrAucs[domainLength - 1] = new AUC2();
            }
            macroOvoAuc = computeOvoMacroAuc(false);
            weightedOvoAuc = computeOvoWeightedAuc(false);
            macroOvrAuc = computeOvrMacroAuc(false);
            weightedOvrAuc = computeOvrWeightedAuc(false);

            macroOvoAucPr = computeOvoMacroAuc(true);
            weightedOvoAucPr = computeOvoWeightedAuc(true);
            macroOvrAucPr = computeOvrMacroAuc(true);
            weightedOvrAucPr = computeOvrWeightedAuc(true);
            _calculateAuc = true;
        } else { // else no result for multinomial AUC - memory issue
            macroOvoAuc = Double.NaN;
            weightedOvoAuc = Double.NaN;
            macroOvrAuc = Double.NaN;
            weightedOvrAuc = Double.NaN;

            macroOvoAucPr = Double.NaN;
            weightedOvoAucPr = Double.NaN;
            macroOvrAucPr = Double.NaN;
            weightedOvrAucPr = Double.NaN;
            _calculateAuc = false;
        }
    }

    public MultinomialAUC(TwoDimTable aucTable, TwoDimTable aucprTable, String[] domain, MultinomialAucType type){
        _default_auc_type = type;
        _domain = domain;
        if(_domain.length < MAX_DOMAIN_SIZE) {
            int domainLength = _domain.length;
            _ovoAucs = new PairwiseAUC[(domainLength * domainLength - domainLength) / 2];
            _ovrAucs = new AUC2[domainLength];
            int aucsIndex = 0;
            for (int i = 0; i < _ovrAucs.length; i++) {
                AUC2 auc = new AUC2();
                auc._auc = (double) aucTable.get(i,3);
                auc._pr_auc = (double) aucprTable.get(i,3);
                _ovrAucs[i] = auc;
            }
            macroOvrAuc = (double) aucTable.get(_ovrAucs.length,3);
            weightedOvrAuc = (double) aucTable.get(_ovrAucs.length + 1,3);

            macroOvrAucPr = (double) aucprTable.get(_ovrAucs.length,3);
            weightedOvrAucPr = (double) aucprTable.get(_ovrAucs.length + 1,3);

            int lastOvoIndex = _ovrAucs.length + _ovoAucs.length + 2;
            for (int j = _ovrAucs.length + 2; j < lastOvoIndex; j++) {
                _ovoAucs[aucsIndex++] = new PairwiseAUC((double)aucTable.get(j, 3),   /*AUC*/
                                                        (double)aucprTable.get(j, 3), /*PR AUC*/
                                                        (String) aucTable.get(j, 1)   /*first domain*/,
                                                        (String) aucTable.get(j, 2)   /*second domain*/);
            }
            macroOvoAuc = (double)aucTable.get(lastOvoIndex, 3);
            weightedOvoAuc = (double) aucTable.get(lastOvoIndex + 1, 3);

            macroOvoAucPr = (double)aucprTable.get(lastOvoIndex, 3);
            weightedOvoAucPr = (double)aucprTable.get(lastOvoIndex, 3);
            _calculateAuc = true;
        } else { // else no result for multinomial AUC - memory issue
            macroOvoAuc = Double.NaN;
            weightedOvoAuc = Double.NaN;
            macroOvrAuc = Double.NaN;
            weightedOvrAuc = Double.NaN;

            macroOvoAucPr = Double.NaN;
            weightedOvoAucPr = Double.NaN;
            macroOvrAucPr = Double.NaN;
            weightedOvrAucPr = Double.NaN;
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
            default:
                return getWeightedOvrAuc();
        }
    }

    public double pr_auc() {
        switch (_default_auc_type) {
            case MACRO_OVR:
                return getMacroOvrAucPr();
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
        for(AUC2 ovrAuc : _ovrAucs){
            macroAuc += isPr ? ovrAuc._pr_auc : ovrAuc._auc;
        }
        return macroAuc/_ovrAucs.length;
    }

    public double computeOvrWeightedAuc(boolean isPr){
        double weightedAuc = 0;
        double sumWeights = 0;
        for(AUC2 ovrAuc : _ovrAucs){
            int maxIndex = ovrAuc._max_idx;
            double tp = 0;
            if(maxIndex != -1){
                tp = ovrAuc.tp(maxIndex);
            }
            sumWeights += tp;
            weightedAuc += isPr ? ovrAuc._pr_auc * tp : ovrAuc._auc * tp;
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
        double n = _ovrAucs[0]._n+_ovrAucs[0]._p;
        double weightedAuc = 0;
        double sumWeights = 0;
        for(PairwiseAUC ovoAuc : _ovoAucs){
            double weight = ovoAuc.getSumTp() / n;
            weightedAuc += isPr ? ovoAuc.getPrAuc() * weight : ovoAuc.getAuc() * weight;
            sumWeights += weight;
        }
        return weightedAuc/sumWeights;
    }

    public double getMacroOvrAuc() {
        return macroOvrAuc;
    }

    public double getWeightedOvrAuc() {
        return weightedOvrAuc;
    }

    public double getMacroOvoAuc() {
        return macroOvoAuc;
    }

    public double getWeightedOvoAuc() {
        return weightedOvoAuc;
    }

    public double getMacroOvrAucPr() {
        return macroOvrAucPr;
    }

    public double getWeightedOvrAucPr() {
        return weightedOvrAucPr;
    }

    public double getMacroOvoAucPr() {
        return macroOvoAucPr;
    }

    public double getWeightedOvoAucPr() {
        return weightedOvoAucPr;
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
                AUC2 auc = _ovrAucs[i];
                double aucValue = isPr ? auc._pr_auc : auc._auc;
                table.set(i, 0, _domain[i]);
                table.set(i, 2, aucValue);
            }
            table.set(_ovrAucs.length, 2, isPr ? macroOvrAucPr : macroOvrAuc);
            table.set(_ovrAucs.length + 1, 2, isPr ? weightedOvrAucPr : weightedOvrAuc);
            sumWeights = 0;
            for (int i = 0; i < _ovoAucs.length; i++) {
                PairwiseAUC auc = _ovoAucs[i];
                double aucValue = isPr ? auc.getPrAuc() : auc.getAuc();
                table.set(_ovrAucs.length + 2 + i, 0, auc.getDomainFirst());
                table.set(_ovrAucs.length + 2 + i, 1, auc.getDomainSecond());
                table.set(_ovrAucs.length + 2 + i, 2, aucValue);
            }
            table.set(rows - 2, 2, isPr ? macroOvoAucPr : macroOvoAuc);
            table.set(rows - 1, 2, isPr ? weightedOvoAucPr : weightedOvoAuc);
            return table;
        } else {
            return null;
        }
    }
}
