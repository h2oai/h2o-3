package hex.genmodel.easy.error;

import hex.genmodel.GenModel;
import hex.genmodel.easy.EasyPredictModelWrapper;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * An implementation of {@link hex.genmodel.easy.EasyPredictModelWrapper.ErrorConsumer}
 * counting number of each kind of error even received
 */
public class CountingErrorConsumer extends EasyPredictModelWrapper.ErrorConsumer {

  private AtomicLong dataTransformationErrorsCount = new AtomicLong();
  private Map<String, AtomicLong> unknownCategoricalsPerColumn = new ConcurrentHashMap<>();

  /**
   * @param model An instance of {@link GenModel}
   */
  public CountingErrorConsumer(GenModel model) {
    for (int i = 0; i < model.getNumCols(); i++) {
      String[] domainValues = model.getDomainValues(i);
      if (domainValues != null) {
        unknownCategoricalsPerColumn.put(model.getNames()[i], new AtomicLong());
      }
    }
  }

  @Override
  public void dataTransformError(String columnName, Object value, String message) {
    dataTransformationErrorsCount.incrementAndGet();
  }

  @Override
  public void unseenCategorical(String columnName, Object value, String message) {
    unknownCategoricalsPerColumn.get(columnName).incrementAndGet();
  }

  /**
   * Counts and returns all previously unseen categorical variables across all columns.
   * Results may vary when called during prediction phase.
   *
   * @return A sum of all previously unseen categoricals across all columns
   */
  public long getTotalUnknownCategoricalLevelsSeen() {
    long total = 0;
    for (AtomicLong l : unknownCategoricalsPerColumn.values()) {
      total += l.get();
    }
    return total;
  }

  /***
   * Returns a thread-safe Map with column names as keys and number of observed unknown categorical values
   * associated with each column. The map returned is a direct reference to
   * the backing this {@link CountingErrorConsumer}. Iteration during prediction phase may end up with
   * undefined results.
   *
   * All the columns are listed.
   * @return A thread-safe map.
   */
  public Map<String, AtomicLong> getUnknownCategoricalsPerColumn() {
    return unknownCategoricalsPerColumn;
  }

  /**
   * @return Number of transformation errors found
   */
  public long getDataTransformationErrorsCount() {
    return dataTransformationErrorsCount.get();
  }
}
