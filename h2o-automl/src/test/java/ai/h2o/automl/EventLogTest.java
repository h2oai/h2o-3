package ai.h2o.automl;

import ai.h2o.automl.EventLogEntry.Level;
import ai.h2o.automl.EventLogEntry.Stage;
import org.apache.commons.lang.time.DateUtils;
import org.joda.time.DateTime;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Key;
import water.util.TwoDimTable;

import java.util.Calendar;
import java.util.Date;

public class EventLogTest extends water.TestUtil {

    @BeforeClass
    public static void setup() {
        stall_till_cloudsize(1);
    }

    private static Key<AutoML> dummy = Key.make();

    @Test public void test_events_are_recorded_as_expected() throws Exception {
        EventLog eventLog = new EventLog(dummy);
        try {
            eventLog.debug(Stage.Workflow, "debug workflow");
            eventLog.info(Stage.Workflow, "info workflow");
            eventLog.warn(Stage.ModelTraining, "warn training");
            eventLog.info(Stage.ModelTraining, "this means what it is").setNamedValue("foo", "bar");
            eventLog.info(Stage.ModelTraining, "this doesn't mean what it looks").setNamedValue("bar", 777);

            TwoDimTable events = eventLog.toTwoDimTable("Test header");
            Assert.assertEquals(6, events.getColDim());
            Assert.assertEquals(5, events.getRowDim());

            Assert.assertEquals("Test header", events.getTableHeader());
            Assert.assertArrayEquals(new String[] {"timestamp", "level", "stage", "message", "name", "value",},
                                    events.getColHeaders());

            DateTime now = DateTime.now();
            DateTime entry_time = new DateTime(EventLogEntry.timeFormat.parse((String)events.get(0, 0)).getTime());
            Assert.assertEquals(now.getHourOfDay(), entry_time.getHourOfDay());
            Assert.assertEquals(now.getMinuteOfHour(), entry_time.getMinuteOfHour());
            Assert.assertEquals(now.getSecondOfMinute(), entry_time.getSecondOfMinute(), 1.0);

            Assert.assertEquals(Level.Debug.toString(), events.get(0, 1));
            Assert.assertEquals(Stage.Workflow.toString(), events.get(1, 2));
            Assert.assertEquals("warn training", events.get(2, 3));
            Assert.assertEquals("foo", events.get(3, 4));
            Assert.assertEquals(Integer.toString(777), events.get(4, 5));
        } finally {
            eventLog.remove();
        }

    }
}
