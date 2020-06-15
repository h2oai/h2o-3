package hex.genmodel.algos.gam;

import static hex.genmodel.utils.DistributionFamily.multinomial;

public class GamMojoMultinomialModel extends GamMojoModelBase {
    private boolean _trueMultinomial;

    GamMojoMultinomialModel(String[] columns, String[][] domains, String responseColumn) {
        super(columns, domains, responseColumn);
    }

    void init() {
        super.init();
        _trueMultinomial = _family.equals(multinomial);
    }
    
    @Override
    double[] gamScore0(double[] row, double[] preds) {
        if (row.length == nfeatures())
            _beta_multinomial = _beta_multinomial_center;
        else
            _beta_multinomial = _beta_multinomial_no_center;
        
        for (int c=0; c<_nclasses; ++c) 
                preds[c+1] = generateEta(_beta_multinomial[c], row);  // generate eta for each class

        if (_trueMultinomial)
            return postPredMultinomial(preds);
        else // post process predict for ordinal family
            return postPredOrdinal(preds);
    }
    
    double[] postPredMultinomial(double[] preds) {
        double max_row = 0;
        double sum_exp = 0;
        for (int c = 1; c < preds.length; ++c) if (preds[c] > max_row) max_row = preds[c];
        for (int c = 1; c < preds.length; ++c) { sum_exp += (preds[c] = Math.exp(preds[c]-max_row));}
        sum_exp = 1/sum_exp;
        double max_p = 0;
        for (int c = 1; c < preds.length; ++c) if ((preds[c] *= sum_exp) > max_p) { max_p = preds[c]; preds[0] = c-1; }
        return preds;
    }
    
    double[] postPredOrdinal(double[] preds) {
        double previousCDF = 0.0;
        preds[0] = _lastClass;
        for (int cInd = 0; cInd < _lastClass; cInd++) { // classify row and calculate PDF of each class
            double eta = preds[cInd + 1];
            double currCDF = 1.0 / (1 + Math.exp(-eta));
            preds[cInd + 1] = currCDF - previousCDF;
            previousCDF = currCDF;

            if (eta > 0) { // found the correct class
                preds[0] = cInd;
                break;
            }
        }
        for (int cInd = (int) preds[0] + 1; cInd < _lastClass; cInd++) {  // continue PDF calculation
            double currCDF = 1.0 / (1 + Math.exp(-preds[cInd + 1]));
            preds[cInd + 1] = currCDF - previousCDF;
            previousCDF = currCDF;

        }
        preds[_nclasses] = 1-previousCDF;
        return preds;
    }
}
