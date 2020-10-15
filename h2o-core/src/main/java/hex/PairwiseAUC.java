package hex;

import water.Iced;

public class PairwiseAUC extends Iced {
    private double _auc;
    private double _prAuc;
    private double _weightedAuc;
    private double _weightedPrAuc;
    private double _tpSum;
    private String _domainFirst;
    private String _domainSecond;
    

    public PairwiseAUC(AUC2 aucFirst, AUC2 aucSecond, String domainFirst, String domainSecond) {
        this._auc = (aucFirst._auc + aucSecond._auc)/2;
        this._prAuc = (aucFirst._pr_auc + aucSecond._pr_auc)/2;
        double tpFirst = 0, tpSecond = 0;
        int firstMaxId = aucFirst._max_idx;
        if(firstMaxId != -1) {
            tpFirst = aucFirst.tp(firstMaxId);
        } 
        int secondMaxId = aucSecond._max_idx;
        if(secondMaxId != -1) {
            tpSecond = aucSecond.tp(secondMaxId);
        }
        this._tpSum = tpFirst + tpSecond;
        this._domainFirst = domainFirst;
        this._domainSecond = domainSecond;
        this._weightedAuc = _auc * _tpSum;
        this._weightedPrAuc = _prAuc * _tpSum;
    }

    public PairwiseAUC(double auc, double weightedAuc, double prauc, double weightedPrAuc, String domainFirst, 
                       String domainSecond) {
        this._auc = auc;
        this._prAuc = prauc;
        this._weightedAuc = weightedAuc;
        this._weightedPrAuc = weightedPrAuc;
        this._domainFirst = domainFirst;
        this._domainSecond = domainSecond;
    }


    // this constructor can be used only if weighted auc each class is not used for calculation weighted average auc
    public PairwiseAUC(double auc, double prauc, String domainFirst, String domainSecond) {
        this(auc, 0, prauc, 0, domainFirst, domainSecond);
    }
    
    public double getSumTp(){
        return _tpSum;
    }
    
    public double getAuc(){ return _auc; }
    
    public double getWeightedAuc() { return _weightedAuc; }

    public double getPrAuc(){ return _prAuc; }

    public double getWeightedPrAuc(){ return _weightedPrAuc; } 

    public String getDomainFirst() { return _domainFirst; }

    public String getDomainSecond() { return _domainSecond; }

    public String getPairwiseDomainsString(){
        return "Class "+_domainFirst+" vs. "+_domainSecond;
    }
}
