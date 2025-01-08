package hex.knn;

import hex.DistanceType;
import water.H2O;

public class KNNDistanceFactory {
    
    public static KNNDistance createDistance(DistanceType type) {
        switch (type) {
            case EUCLIDEAN:
                return new EuclideanDistance();
            case MANHATTAN:
                return new ManhattanDistance();
            case COSINE:
                return new CosineDistance();
            default:
                throw H2O.unimpl("Try to get "+type+" which is not supported.");
        }
    }
    
}
