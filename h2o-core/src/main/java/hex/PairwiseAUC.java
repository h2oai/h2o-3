package hex;

import water.Iced;

public class PairwiseAUC extends Iced {
    private double _auc;
    private double _prAuc;
    private double _sumPositives;
    private String _domainFirst;
    private String _domainSecond;
    

    public PairwiseAUC(AUC2 aucFirst, AUC2 aucSecond, String domainFirst, String domainSecond) {
        this._auc = (aucFirst._auc + aucSecond._auc)/2;
        if(Double.isNaN(this._auc)){
            this._auc = 0;
        }
        this._prAuc = (aucFirst._pr_auc + aucSecond._pr_auc)/2;
        if(Double.isNaN(this._prAuc)){
            this._prAuc = 0;
        }
        this._sumPositives = aucFirst._p + aucSecond._p;
        this._domainFirst = domainFirst;
        this._domainSecond = domainSecond;
    }

    public PairwiseAUC(double auc, double prauc, String domainFirst, String domainSecond) {
        this._auc = auc;
        this._prAuc = prauc;
        this._domainFirst = domainFirst;
        this._domainSecond = domainSecond;
    }
    
    public double getSumPositives(){
        return _sumPositives;
    }
    
    public double getAuc(){ return _auc; }
    
    public double getPrAuc(){ return _prAuc; }
    
    public String getDomainFirst() { return _domainFirst; }

    public String getDomainSecond() { return _domainSecond; }

    public String getPairwiseDomainsString(){
        return "Class "+_domainFirst+" vs. "+_domainSecond;
    }
}
