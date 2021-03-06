package ai.h2o.targetencoding.interaction;

import water.Iced;
import water.util.ArrayUtils;

/**
 * The interaction value is simply encoded as:
 * val = val1 + (val2 * card1) + … + (valN * card1 * … * cardN-1)
 * where val1, val2, …, valN are the interacting values
 * and card1, …, cardN are the extended domain cardinalities (taking NAs into account) for interacting columns.
 */
class InteractionsEncoder extends Iced {
    static final String UNSEEN = "_UNSEEN_";
    static final String NA = "_NA_";

    private boolean _encodeUnseenAsNA;
    private String[][] _interactingDomains;
    private long[] _encodingFactors;

    InteractionsEncoder(String[][] interactingDomains, boolean encodeUnseenAsNA) {
        _encodeUnseenAsNA = encodeUnseenAsNA;
        _interactingDomains = interactingDomains;
        _encodingFactors = createEncodingFactors();
    }


    long encode(int[] interactingValues) {
        long value = 0;
        for (int i = 0; i < interactingValues.length; i++) {
            int domainCard = _interactingDomains[i].length;
            long interactionFactor = _encodingFactors[i];
            int ival = interactingValues[i];
            if (ival >= domainCard) ival = domainCard;  // unseen value during training
            if (ival < 0) ival = _encodeUnseenAsNA ? domainCard : (domainCard + 1);  // NA
            value += ival * interactionFactor;
        }
        return value;
    }

    long encodeStr(String[] interactingValues) {
        int[] values = new int[interactingValues.length];
        for (int i = 0; i < interactingValues.length; i++) {
            String[] domain = _interactingDomains[i];
            String val = interactingValues[i];
            int ival = val==null ? -1 : ArrayUtils.find(domain, val);
            if (ival < 0 && val != null) {  //emulates distinction between NA and unseen.
                values[i] = domain.length; 
            } else {
                values[i] = ival;
            }
        }
        return encode(values);
    }

    int[] decode(long interactionValue) {
        int[] values = new int[_encodingFactors.length];
        long value = interactionValue;
        for (int i = _encodingFactors.length - 1; i >= 0; i--) {
            long factor = _encodingFactors[i];
            values[i] = (int)(value / factor);
            value %= factor;
        }
        return values;
    }

    String[] decodeStr(long interactionValue) {
        int[] values = decode(interactionValue);
        String[] catValues = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            String[] domain = _interactingDomains[i];
            int val = values[i];
            catValues[i] = val < domain.length ? domain[val]
                    : i==domain.length ? (_encodeUnseenAsNA ? null : UNSEEN)
                    : null;
        }
        return catValues;
    }

    private long[] createEncodingFactors() {
        long[] factors = new long[_interactingDomains.length];
        long multiplier = 1;
        for (int i = 0; i < _interactingDomains.length; i++) {
            int domainCard = _interactingDomains[i].length;
            int interactionFactor = _encodeUnseenAsNA ? (domainCard + 1) : (domainCard + 2);  // +1 for NAs, +1 for potential unseen values
            factors[i] = multiplier;
            multiplier *= interactionFactor;
        }
        return factors;
    }

}
