package hex;

import water.Iced;
import water.Scope;

public class PairwiseAUC extends Iced {
    private AUC2 _aucFirst;
    private AUC2 _aucSecond;
    private int _indexFirst;
    private int _indexSecond;
    private String _domainFirst;
    private String _domainSecond;

    public PairwiseAUC(AUC2 aucFirst, AUC2 aucSecond, int indexFirst, int indexSecond, String domainFirst, 
                       String domainSecond) {
        this._aucFirst = aucFirst;
        this._aucSecond = aucSecond;
        this._indexFirst = indexFirst;
        this._indexSecond = indexSecond;
        this._domainFirst = domainFirst;
        this._domainSecond = domainSecond;
    }
    
    public double getPairwiseAuc(){
        return (this._aucFirst._auc + this._aucSecond._auc)/2;
    }

    public double getPairwisePrAuc(){
        return (this._aucFirst._pr_auc + this._aucSecond._pr_auc)/2;
    }

    public String getPairwiseAucString(){
        return "AUC class "+_domainFirst+" vs "+_domainSecond+": "+getPairwiseAuc();
    }

    public String getPairwisePrAucString(){
        return "AUC_PR class "+_domainFirst+" vs "+_domainSecond+": "+getPairwisePrAuc();
    }

    public int getIndexFirst() {
        return _indexFirst;
    }

    public int getIndexSecond() {
        return _indexSecond;
    }
    
    public boolean hasIndices(int i, int j){
        return (_indexFirst == i && _indexSecond == j) || (_indexFirst == j && _indexSecond == i);
    }

    public boolean hasDomains(String domainFirst, String domainSecond){
        return (_domainFirst.equals(domainFirst) && _domainSecond.equals(domainSecond)) || 
                (_domainFirst.equals(domainSecond) && _domainSecond.equals(domainFirst));
    }
}
