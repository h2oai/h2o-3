package hex.deepwater;



import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.devtools.build.lib.syntax.Environment;
import com.google.devtools.build.lib.syntax.Expression;
import com.google.devtools.build.lib.syntax.Mutability;
import com.google.devtools.build.lib.syntax.Parser;
import com.google.devtools.build.lib.syntax.ParserInputSource;
import com.google.devtools.build.lib.syntax.Statement;
import hex.deeplearning.Storage;
import hex.deepwater.models.Sequential;
import junit.framework.TestCase;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.tensorflow;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.tensorflow.framework.AllocationDescription;
import org.tensorflow.framework.GraphDef;
import org.tensorflow.framework.NodeDef;
import org.tensorflow.framework.OpDef;
import org.tensorflow.framework.OpList;
import org.tensorflow.framework.TypesProtos;
import water.TestUtil;

import com.google.devtools.build.lib.events.EventHandler;
import com.google.devtools.build.lib.events.EventKind;
import water.fvec.Frame;
import water.fvec.Vec;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static hex.deepwater.models.Sequential.checkStatus;
import static org.bytedeco.javacpp.tensorflow.*;
import static water.TestUtil.stall_till_cloudsize;
//import com.google.devtools.build.lib.events.util.EventCollectionApparatus;

/**
 * Created by fmilo on 8/15/16.
 */

class Layer  {

}

class Sequence {


}

public class SequenceModelTest extends TestUtil {

    @BeforeClass public static void stall() { stall_till_cloudsize(1); }

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
    public void testDeepWaterSequentialModel() throws ScriptException, FileNotFoundException {
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

        new Sequential();

        ScriptEngine engine = manager.getEngineByName("python");
        String [] script = new String[
                ]{
                "import sys",
                "from hex.deepwater.layers import Dense",
                "from hex.deepwater.models import Sequential",
                "model = Sequential()",
                "model.add(Dense(32, input_shape=(500,)))",
                "model.add(Dense(10, activation='softmax'))",
                "model.compile(optimizer='rmsprop', loss='categorical_crossentropy', metrics=['accuracy'])",
        };

        for(String stmt: script){
            engine.eval(stmt);
        }


        TensorFrame<Float> x = TensorFrame.allocateFloat32(100);

        engine.put("x", x);
        engine.put("y", x);
        engine.eval("model.fit(x,y, 32, 24)");

//        new DeviceSpec();
    }

    @Test
    public void loadOpDefinitions() {
        Map<String, OpDef> ops = new HashMap<>();
        OpList l = Graph.loadOps();

        for (int i = 0; i < l.getOpCount(); i++) {
            OpDef op = l.getOp(i);
            System.out.println(op.getName());

            ops.put(op.getName(), op);

            List<OpDef.ArgDef> args = op.getInputArgList();
            for (int j = 0; j < args.size(); j++) {
                System.out.println("\t" + args.get(j).getName() + ":" + args.get(j).getTypeAttr());
            }
        }

        OpDef abs = ops.get("Abs");

        GraphDef g = GraphDef.newBuilder().addNode(NodeDef.newBuilder().setOp("Abs").setInput(0, "x").build()).build();

        tensorflow.SessionOptions opt = new tensorflow.SessionOptions();
        tensorflow.Session sess = new tensorflow.Session(opt);



    }

   @Test
   public void testDotGraph(){
       tensorflow.GraphDef graph_def = new tensorflow.GraphDef();
       tensorflow.Status status = ReadBinaryProto(Env.Default(), "/tmp/my_graph.pbtxt", graph_def);
       checkStatus(status);

       GraphConstructorOptions opts = new GraphConstructorOptions();
       tensorflow.Graph gg = new tensorflow.Graph(OpRegistry.Global());

       status = ConvertGraphDefToGraph(opts, graph_def, gg);
       checkStatus(status);

       tensorflow.DotOptions gopts = new tensorflow.DotOptions();
       BytePointer result = tensorflow.DotGraph(gg, gopts);

       System.out.println(result.getString());
    }

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


        //new OpRegistrationData();
        tensorflow.OpDef def = new tensorflow.OpDef();
        new tensorflow.OpDefBuilder("Restrict").Input("a: T").Output("out: T").Attr("T: {string,bool}").Finalize(def);


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
