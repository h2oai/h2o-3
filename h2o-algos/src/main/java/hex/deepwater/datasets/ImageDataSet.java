package hex.deepwater.datasets;

import water.exceptions.H2OIllegalArgumentException;

public class ImageDataSet {

    int channels;
    float[] meanData; //mean pixel value of the training data
    private int width;
    private int height;

    public ImageDataSet(int width, int height, int channels){
        this.height = height;
        this.width = width;
        this.channels = channels;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getChannels() {
        return channels;
    }

    public void setChannels(int channels) {
        this.channels = channels;
    }

    public float[] getMeanData() {
        return meanData;
    }

    public void setMeanData(float[] meanData) {
        int dim = channels*width*height;
        if (meanData.length != dim) {
            throw new H2OIllegalArgumentException("Invalid mean image data format. Expected length: " + dim + ", but has length: " + meanData.length);
        }

        this.meanData = meanData;
    }


}
