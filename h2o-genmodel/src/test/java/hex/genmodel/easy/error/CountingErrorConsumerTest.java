package hex.genmodel.easy.error;

import static org.mockito.Mockito.*;

import hex.genmodel.GenModel;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

@RunWith(MockitoJUnitRunner.class)
public class CountingErrorConsumerTest {

  @Mock
  public GenModel model;
  
  @Rule
  public ExpectedException ee = ExpectedException.none();
  
  @Before
  public void setupMock() {
    when(model.isSupervised()).thenReturn(false);
    when(model.getNumCols()).thenReturn(1);
    when(model.getDomainValues(0)).thenReturn(new String[]{"anything"});
    when(model.getNames()).thenReturn(new String[]{"col1"});
  }
  
  @Test
  public void getUnseenCategoricals_notEnabled() {
    CountingErrorConsumer consumer = new CountingErrorConsumer(model);
    consumer.unseenCategorical("col1", "val1", null);

    ee.expectMessage("Unseen categorical values collection was not enabled.");
    
    consumer.getUnseenCategoricals("col1");
  }

  @Test
  public void getUnseenCategoricals() {
    CountingErrorConsumer.Config config = new CountingErrorConsumer.Config();
    config.setCollectUnseenCategoricals(true);
    CountingErrorConsumer consumer = new CountingErrorConsumer(model, config);

    Map<Object, AtomicLong> unseen = consumer.getUnseenCategoricals("col1");
    assertTrue(unseen.isEmpty());

    consumer.unseenCategorical("col1", "val1", null);
    assertEquals(1, unseen.size());
    AtomicLong val1 = unseen.get("val1");
    assertEquals(1, val1.longValue());

    consumer.unseenCategorical("col1", "val2", null);
    assertEquals(2, unseen.size());
    AtomicLong val2 = unseen.get("val2");
    assertEquals(1, val2.longValue());

    consumer.unseenCategorical("col1", "val2", null);
    consumer.unseenCategorical("col1", "val2", null);
    assertEquals(3, val2.longValue());
  }

}
