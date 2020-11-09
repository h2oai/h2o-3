/**
* Exception is thrown when the metric specified by the user could not be retrived.
*/
package water.rapids;

public class MetricNotFoundExeption extends Exception {
    public MetricNotFoundExeption(String message) {
        super(message);
    }
}
