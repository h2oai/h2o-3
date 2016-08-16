package hex.deepwater;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.devtools.build.lib.syntax.Environment;
import com.google.devtools.build.lib.syntax.Expression;
import com.google.devtools.build.lib.syntax.Mutability;
import com.google.devtools.build.lib.syntax.Parser;
import com.google.devtools.build.lib.syntax.ParserInputSource;
import com.google.devtools.build.lib.syntax.Statement;
import junit.framework.TestCase;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.tensorflow.framework.AllocationDescription;
import water.TestUtil;

import com.google.devtools.build.lib.events.EventHandler;
import com.google.devtools.build.lib.events.EventKind;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static water.TestUtil.stall_till_cloudsize;
//import com.google.devtools.build.lib.events.util.EventCollectionApparatus;

/**
 * Created by fmilo on 8/15/16.
 */

class Layer  {

}

class Sequence {

}

public class SequenceModelTest {

    protected Environment env;
    protected Mutability mutability = Mutability.create("test");
    private EventCollectionApparatus eventCollectionApparatus =
            new EventCollectionApparatus(EventKind.ALL_EVENTS);

    @Before
    public final void initialize() throws Exception {
        env = newSkylarkEnvironment();
    }

    @Test
    public void buildSequence() throws Exception {
      eval("a = 10 * 11 ");

      Integer result = (Integer) lookup("a");
    }

    public Environment newSkylarkEnvironment() {
        return Environment.builder(mutability)
                .setSkylark()
                .setGlobals(Environment.SKYLARK)
                .setEventHandler(getEventHandler())
                .build();
    }

    protected EventHandler getEventHandler() {
        return eventCollectionApparatus.reporter();
    }
    protected List<Statement> parseFile(String... input) {
        return env.parseFile(input);
    }

    /** Parses an Expression from string without a supporting file */
    @VisibleForTesting
    public Expression parseExpression(String... input) {
        return Parser.parseExpression(
                ParserInputSource.create(Joiner.on("\n").join(input), null), getEventHandler());
    }

    /*
    public EvaluationTestCase update(String varname, Object value) throws Exception {
        env.update(varname, value);
        return this;
    }*/

    public Object lookup(String varname) throws Exception {
        return env.lookup(varname);
    }

    public Object eval(String... input) throws Exception {
        return env.eval(input);
    }
    /*
    public void checkEvalError(String msg, String... input) throws Exception {
        setFailFast(true);
        try {
            eval(input);
            fail("Expected error '" + msg + "' but got no error");
        } catch (IllegalArgumentException | EvalException e) {
            assertThat(e).hasMessage(msg);
        }
    }

    public void checkEvalErrorContains(String msg, String... input) throws Exception {
        try {
            eval(input);
            fail("Expected error containing '" + msg + "' but got no error");
        } catch (IllegalArgumentException | EvalException e) {
            assertThat(e.getMessage()).contains(msg);
        }
    }
    */

    @Test
    public void runScriptExample() throws ScriptException, FileNotFoundException {
        ScriptEngineManager manager = new ScriptEngineManager();
        List<ScriptEngineFactory> factories = manager.getEngineFactories();
        for (ScriptEngineFactory f : factories) {
            System.out.println("engine name:" + f.getEngineName());
            System.out.println("engine version:" + f.getEngineVersion());
            System.out.println("language name:" + f.getLanguageName());
            System.out.println("language version:" + f.getLanguageVersion());
            System.out.println("names:" + f.getNames());
            System.out.println("mime:" + f.getMimeTypes());
            System.out.println("extension:" + f.getExtensions());
            System.out.println("-----------------------------------------------");
        }


        AllocationDescription.getDescriptor();

        ScriptEngine engine = manager.getEngineByName("python");
        engine.eval("import sys");
        engine.eval("import org.tensorflow.framework");
        engine.eval("sys.path.append('/home/fmilo/workspace/jkeras')");
        engine.eval(new FileReader("/home/fmilo/workspace/jkeras/run.py"));
        engine.eval("print sys");
        engine.put("a", 42);
        engine.eval("print a");
        engine.eval("x = 2 + 2");
        Object x = engine.get("x");
        System.out.println("x: " + x);

        @SuppressWarnings("rawtypes")
        Map m = new HashMap();
        m.put("c", 10);
        engine.put("m", m);
        engine.eval("def max_num(a,b):\n" +
                "\treturn a if a > b else b");
        engine.eval("x= max_num(a,m.get('c'));");
        System.out.println("max_num:" + engine.get("x"));
    }
}
