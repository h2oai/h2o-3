package water.rapids.ast.prims.internal;

import water.rapids.Env;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValStr;

import java.lang.reflect.Method;

public class AstRunTool extends AstPrimitive<AstRunTool> {

    private static final String TOOLS_PACKAGE = "water.tools.";

    @Override
    public String[] args() {
        return new String[]{"tool_class", "tool_parameters"};
    }

    @Override
    public int nargs() {
        return 1 + 2;
    } // (run_tool tool_class tool_parameters)

    @Override
    public String str() {
        return "run_tool";
    }

    @Override
    public ValStr apply(Env env, Env.StackHelp stk, AstRoot[] asts) {
        String toolClassName = stk.track(asts[1].exec(env)).getStr();
        String[] args = stk.track(asts[2].exec(env)).getStrs();
        try {
            // only allow to run approved tools (from our package), not just anything on classpath
            Class<?> clazz = Class.forName(TOOLS_PACKAGE + toolClassName);
            Method mainMethod = clazz.getDeclaredMethod("mainInternal", String[].class);
            mainMethod.invoke(null, new Object[]{args});
        } catch (Exception e) {
            RuntimeException shorterException = new RuntimeException(e.getCause().getMessage());
            shorterException.setStackTrace(new StackTraceElement[0]);
            throw shorterException;
        }
        return new ValStr("OK");
    }

}
