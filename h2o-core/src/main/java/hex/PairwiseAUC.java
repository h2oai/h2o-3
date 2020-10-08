package hex;

import water.Iced;

public class PairwiseAUC extends Iced {
    private double _aucFirst;
    private double _aucSecond;
    private double _praucFirst;
    private double _praucSecond;
    private double _tpFirst;
    private double _tpSecond;
    private String _domainFirst;
    private String _domainSecond;
    

    public PairwiseAUC(AUC2 aucFirst, AUC2 aucSecond, String domainFirst, String domainSecond) {
        this._aucFirst = aucFirst._auc;
        this._aucSecond = aucSecond._auc;
        this._praucFirst = aucFirst._pr_auc;
        this._praucSecond = aucSecond._pr_auc;
        int firstMaxId = aucFirst._max_idx;
        if(firstMaxId != -1) {
            this._tpFirst = aucFirst.tp(firstMaxId);
        } 
        int secondMaxId = aucSecond._max_idx;
        if(secondMaxId != -1) {
            this._tpSecond = aucSecond.tp(secondMaxId);
        }
        this._domainFirst = domainFirst;
        this._domainSecond = domainSecond;
    }

    public PairwiseAUC(Double aucFirst, double aucSecond, double praucFirst, double praucSecond, 
                       double tpFirst, double tpSecond, String domainFirst, String domainSecond) {
        this._aucFirst = aucFirst;
        this._aucSecond = aucSecond;
        this._praucFirst = praucFirst;
        this._praucSecond = praucSecond;
        this._tpFirst = tpFirst;
        this._tpSecond = tpSecond;
        this._domainFirst = domainFirst;
        this._domainSecond = domainSecond;
    }
    
    public double getSumTp(){
        return _tpFirst + _tpSecond;
    }
    
    public double getAuc(){ return (this._aucFirst + this._aucSecond) / 2; }
    
    public double getWeightedAuc() { return getAuc() * getSumTp(); }

    public double getPrAuc(){ return (this._praucFirst + this._praucSecond) / 2; }

    public double getWeightedPrAuc(){ return getPrAuc() * getSumTp(); }

    public String getAucString(){
        return "AUC class "+_domainFirst+" vs "+_domainSecond+": "+getAuc();
    }

    public String getPrAucString(){
        return "AUC_PR class "+_domainFirst+" vs "+_domainSecond+": "+getPrAuc();
    }

    public String getDomainFirst() { return _domainFirst; }

    public String getDomainSecond() { return _domainSecond; }

    public String getWeightedAucString(){
        return "AUC class "+_domainFirst+" vs "+_domainSecond+": "+getWeightedAuc();
    }

    public String getWeightedPrAucString(){
        return "AUC_PR class "+_domainFirst+" vs "+_domainSecond+": "+getWeightedPrAuc();
    }

    public String getPairwiseDomainsString(){
        return "Class "+_domainFirst+" vs. "+_domainSecond;
    }
}
