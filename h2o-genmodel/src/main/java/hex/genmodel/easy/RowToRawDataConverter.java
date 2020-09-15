package hex.genmodel.easy;

import hex.genmodel.easy.exception.PredictException;

public interface RowToRawDataConverter {

    /**
     *
     * @param data instance of RowData for which we want to get predictions.
     * @param rawData array that will be filled up from RowData instance.
     * @return `rawData` array with data from RowData, potentially extended with additional data as a result from model conversion..
     * @throws PredictException Note: name of the exception feels like out of scope of the class with name `RowToRawDataConverter` 
     *      but this conversion is only needed to make it possible to produce predictions so it makes sense.
     */
    double[] convert(RowData data, double[] rawData) throws PredictException;
}
