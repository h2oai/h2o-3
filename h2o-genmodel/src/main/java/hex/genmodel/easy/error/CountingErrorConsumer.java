package hex.genmodel.easy.error;

import hex.genmodel.GenModel;
import hex.genmodel.easy.EasyPredictModelWrapper;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * An implementation of {@link hex.genmodel.easy.EasyPredictModelWrapper.ErrorConsumer}
 * counting number of each kind of error even received
 */
public class CountingErrorConsumer extends EasyPredictModelWrapper.ErrorConsumer {

  private Map<String, AtomicLong> dataTransformationErrorsCountPerColumn;
  private Map<String, AtomicLong> unknownCategoricalsPerColumn;
  private Map<String, ConcurrentMap<Object, AtomicLong>> unseenCategoricalsCollector;

  private final boolean collectUnseenCategoricals;
  
  /**
   * @param model An instance of {@link GenModel}
   */
  public CountingErrorConsumer(GenModel model) {
    this(model, DEFAULT_CONFIG);
  }

  /**
   * @param model An instance of {@link GenModel}
   * @param config An instance of {@link Config}
   */
  public CountingErrorConsumer(GenModel model, Config config) {
    collectUnseenCategoricals = config.isCollectUnseenCategoricals();

    initializeDataTransformationErrorsCount(model);
    initializeUnknownCategoricals(model);
  }

  /**
   * Initializes the map of data transformation errors for each column that is not related to response variable,
   * excluding response column. The map is initialized as unmodifiable and thread-safe.
   *
   * @param model {@link GenModel} the data trasnformation errors count map is initialized for
   */
  private void initializeDataTransformationErrorsCount(GenModel model) {
    String responseColumnName = model.isSupervised() ? model.getResponseName() : null;

    dataTransformationErrorsCountPerColumn = new ConcurrentHashMap<>();
    for (String column : model.getNames()) {
      // Do not perform check for response column if the model is unsupervised
      if (!model.isSupervised() || !column.equals(responseColumnName)) {
        dataTransformationErrorsCountPerColumn.put(column, new AtomicLong());
      }
    }
    dataTransformationErrorsCountPerColumn = Collections.unmodifiableMap(dataTransformationErrorsCountPerColumn);
  }

  /**
   * Initializes the map of unknown categoricals per column with an unmodifiable and thread-safe implementation of {@link Map}.
   *
   * @param model {@link GenModel} the unknown categorical per column map is initialized for
   */
  private void initializeUnknownCategoricals(GenModel model) {
    unknownCategoricalsPerColumn = new ConcurrentHashMap<>();
    unseenCategoricalsCollector = new ConcurrentHashMap<>();

    for (int i = 0; i < model.getNumCols(); i++) {
      String[] domainValues = model.getDomainValues(i);
      if (domainValues != null) {
        unknownCategoricalsPerColumn.put(model.getNames()[i], new AtomicLong());
        if (collectUnseenCategoricals)
          unseenCategoricalsCollector.put(model.getNames()[i], new ConcurrentHashMap<Object, AtomicLong>());
      }
    }

    unknownCategoricalsPerColumn = Collections.unmodifiableMap(unknownCategoricalsPerColumn);
  }

  @Override
  public void dataTransformError(String columnName, Object value, String message) {
    dataTransformationErrorsCountPerColumn.get(columnName).incrementAndGet();
  }

  @Override
  public void unseenCategorical(String columnName, Object value, String message) {
    unknownCategoricalsPerColumn.get(columnName).incrementAndGet();
    if (collectUnseenCategoricals) {
      ConcurrentMap<Object, AtomicLong> columnCollector = unseenCategoricalsCollector.get(columnName);
      assert columnCollector != null;
      AtomicLong counter = columnCollector.get(value);
      if (counter != null) {
        counter.incrementAndGet(); // best effort to avoid creating new AtomicLongs on each invocation 
      } else {
        counter = columnCollector.putIfAbsent(value, new AtomicLong(1));
        if (counter != null) {
          counter.incrementAndGet();
        }
      }
    }
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

  public Map<Object, AtomicLong> getUnseenCategoricals(String column) {
    if (! collectUnseenCategoricals) {
      throw new IllegalStateException("Unseen categorical values collection was not enabled.");
    }
    return unseenCategoricalsCollector.get(column);
  }
  
  /**
   * An unmodifiable, thread-safe map of all columns with counts of data transformation errors observed.
   * The map returned is a direct reference to the backing this {@link CountingErrorConsumer}.
   * Iteration during prediction phase may end up with undefined results.
   *
   * @return A thread-safe instance of {@link Map}
   */
  public Map<String, AtomicLong> getDataTransformationErrorsCountPerColumn() {
    return dataTransformationErrorsCountPerColumn;
  }

  /**
   * @return Number of transformation errors found
   */
  public long getDataTransformationErrorsCount() {
    long total = 0;
    for (AtomicLong l : dataTransformationErrorsCountPerColumn.values()) {
      total += l.get();
    }
    return total;
  }

  private static final Config DEFAULT_CONFIG = new Config();

  public static class Config {
    private boolean collectUnseenCategoricals;

    public boolean isCollectUnseenCategoricals() {
      return collectUnseenCategoricals;
    }

    public void setCollectUnseenCategoricals(boolean collectUnseenCategoricals) {
      this.collectUnseenCategoricals = collectUnseenCategoricals;
    }
  }

}
