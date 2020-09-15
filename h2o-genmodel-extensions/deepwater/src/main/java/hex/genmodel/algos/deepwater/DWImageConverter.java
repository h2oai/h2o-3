package hex.genmodel.algos.deepwater;

import hex.genmodel.GenModel;
import hex.genmodel.easy.CategoricalEncoder;
import hex.genmodel.easy.DefaultRowToRawDataConverter;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.exception.PredictException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;

public class DWImageConverter extends DefaultRowToRawDataConverter<DeepwaterMojoModel> {
  
  private final DeepwaterMojoModel _model;

  DWImageConverter(DeepwaterMojoModel m, Map<String, Integer> modelColumnNameToIndexMap, Map<Integer, CategoricalEncoder> domainMap,
                   EasyPredictModelWrapper.ErrorConsumer errorConsumer, EasyPredictModelWrapper.Config config) {
    super(modelColumnNameToIndexMap, domainMap, errorConsumer, config);
    _model = m;
  }

  @Override
  protected boolean convertValue(String columnName, Object o, CategoricalEncoder catEncoder, int targetIndex, double[] rawData) throws PredictException {
    BufferedImage img = null;

    if (o instanceof String) {
      String s = ((String) o).trim();
      // Url to an image given
      boolean isURL = s.matches("^(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]");
      try {
        img = isURL ? ImageIO.read(new URL(s)) : ImageIO.read(new File(s));
      } catch (IOException e) {
        throw new PredictException("Couldn't read image from " + s);
      }
    } else if (o instanceof byte[]) {
      // Read the image from raw bytes
      InputStream is = new ByteArrayInputStream((byte[]) o);
      try {
        img = ImageIO.read(is);
      } catch (IOException e) {
        throw new PredictException("Couldn't interpret raw bytes as an image.");
      }
    }

    if (img != null) {
      int W = _model._width;
      int H = _model._height;
      int C = _model._channels;
      float[] _destData = new float[W * H * C];
      try {
        GenModel.img2pixels(img, W, H, C, _destData, 0, _model._meanImageData);
      } catch (IOException e) {
        throw new PredictException("Couldn't vectorize image.", e);
      }
      rawData = new double[_destData.length];
      for (int i = 0; i < rawData.length; ++i)
        rawData[i] = _destData[i];
      return true;
    } else
      return super.convertValue(columnName, o, catEncoder, targetIndex, rawData);
  }

}
