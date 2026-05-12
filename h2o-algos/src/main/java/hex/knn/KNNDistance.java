package hex.knn;

import water.Iced;

/**
 * Template for various distance calculation.
 */
public abstract class KNNDistance extends Iced<KNNDistance> {
    
    // Lenght of values for calculation of the distance
    // For example for calculation euclidean and manhattan distance we need only one value,
    // for calculation cosine distance we need tree values.
    public int valuesLength = 1;
    
    // Array to cumulate partial calculations of distance
    public double[] values;
    
    /**
     * Method to calculate the distance between two points from two vectors.
     * @param v1 value of an item in the first vector
     * @param v2 value of an item in the second vector
     */
    public abstract double nom(double v1, double v2);

    /**
     * Initialize values array to store partial calculation of distance.
     */
    public void initializeValues(){
        this.values = new double[valuesLength];
    }

    /**
     * Method to cumulate partial calculations of distance between two vectors and save it to values array.
     * @param v1 value of an item in the first vector
     * @param v2 value of an item in the second vector
     */
    public abstract void calculateValues(double v1, double v2);

    /**
     * Calculate the result from cumulated values.
     * @return Final distance calculation.
     */
    public abstract double result();
    
}
