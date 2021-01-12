package hex.tree.uplift;

public class KullbackMetric {
    public static double metric(double p, double q){
        return p * Math.log(p/q);
    }
    
    public static double divergenceMeasure(double pLeft, double qLeft, double pRight, double qRight){
        return metric(pLeft, qLeft) + metric(pRight, qRight);
    }
    
    public static double gain(double pBefore, double qBefore, double pLeft, double qLeft, double pRight, double qRight){
        return divergenceMeasure(pLeft, qLeft, pRight, qRight) - metric(pBefore, qBefore);
    }
}
