package ai.h2o.automl.events;

import ai.h2o.automl.AutoML;
import ai.h2o.automl.events.EventLogEntry.Stage;
import org.joda.time.DateTime;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.Key;
import water.logging.LoggingLevel;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.TwoDimTable;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class EventLogTest {

    private static Key<AutoML> dummy = Key.make();

    @Test public void test_events_are_recorded_as_expected() throws Exception {
        EventLog eventLog = new EventLog(dummy);
        try {
            eventLog.debug(Stage.Workflow, "debug workflow");
            eventLog.info(Stage.Workflow, "info workflow");
            eventLog.warn(Stage.ModelTraining, "warn training");
            eventLog.info(Stage.ModelTraining, "this means what it is").setNamedValue("foo", "bar");
            eventLog.info(Stage.ModelTraining, "this doesn't mean what it looks").setNamedValue("bar", 777);

            TwoDimTable events = eventLog.toTwoDimTable("Test header", null);
            assertEquals(6, events.getColDim());
            assertEquals(5, events.getRowDim());

            assertEquals("Test header", events.getTableHeader());
            assertArrayEquals(new String[] {"timestamp", "level", "stage", "message", "name", "value",},
                              events.getColHeaders());

            DateTime now = DateTime.now();
            DateTime entry_time = new DateTime(EventLogEntry.timeFormat.get().parse((String)events.get(0, 0)).getTime());
            assertEquals(now.getHourOfDay(), entry_time.getHourOfDay());
            assertEquals(now.getMinuteOfHour(), entry_time.getMinuteOfHour());
            assertEquals(now.getSecondOfMinute(), entry_time.getSecondOfMinute(), 1.0);

            assertEquals(LoggingLevel.DEBUG.toString(), events.get(0, 1));
            assertEquals(Stage.Workflow.toString(), events.get(1, 2));
            assertEquals("warn training", events.get(2, 3));
            assertEquals("foo", events.get(3, 4));
            assertEquals(Integer.toString(777), events.get(4, 5));
        } finally {
            eventLog.remove();
        }
    }
    
    @Test public void test_events_can_be_filtered() {
        EventLog eventLog = new EventLog(dummy);
        eventLog.debug(Stage.Workflow, "debug workflow");
        eventLog.info(Stage.Workflow, "info workflow");
        eventLog.warn(Stage.ModelTraining, "warn training");
        eventLog.info(Stage.ModelTraining, "this means what it is").setNamedValue("foo", "bar");
        eventLog.info(Stage.ModelTraining, "this doesn't mean what it looks").setNamedValue("bar", 777);
        
        TwoDimTable events = eventLog.toTwoDimTable();
        assertEquals(5, events.getRowDim());
        
        events = eventLog.toTwoDimTable(e -> e.getLevel() == LoggingLevel.INFO);
        assertEquals(3, events.getRowDim());

        events = eventLog.toTwoDimTable(e -> e.getStage() == Stage.Workflow);
        assertEquals(2, events.getRowDim());
    }
}
