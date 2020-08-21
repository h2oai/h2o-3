package water.logging;

import java.io.PrintStream;

public class ConsoleLogger implements Logger {

    @Override
    public void trace(String message) {
        log(0, message);
    }

    @Override
    public void debug(String message) {
        log(1, message);
    }

    @Override
    public void info(String message) {
        log(2, message);
    }

    @Override
    public void warn(String message) {
        log(3, message);
    }

    @Override
    public void error(String message) {
        log(4, message);
    }

    @Override
    public void fatal(String message) {
        log(5, message);
    }

    @Override
    public boolean isTraceEnabled() {
        return true;
    }

    @Override
    public boolean isDebugEnabled() {
        return true;
    }

    @Override
    public boolean isInfoEnabled() {
        return true;
    }

    @Override
    public boolean isWarnEnabled() {
        return true;
    }

    @Override
    public boolean isErrorEnabled() {
        return true;
    }

    @Override
    public boolean isFatalEnabled() {
        return true;
    }

    private void log(int level, String message) {
        PrintStream ps;

        if (level < 4) {
            ps = System.out;
        } else {
            ps = System.err;
        }

        ps.println(message);
    }
}
