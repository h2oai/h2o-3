package water;

import water.util.ArrayUtils;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class KeyGen extends Iced<KeyGen> {

  public abstract <T extends Keyed<T>> Key<T> make(Object... params);
  
  public static final class ConstantKeyGen extends KeyGen {
    private final Key key;

    public ConstantKeyGen(Key key) {
      this.key = key;
    }

    @Override
    public <T extends Keyed<T>> Key<T> make(Object... params) {
      return key;
    }
  }
  
  public static final class RandomKeyGen extends KeyGen {
    public RandomKeyGen() {}

    @Override
    public <T extends Keyed<T>> Key<T> make(Object... params) {
      return Key.make(Key.rand());
    }
  }
  
  public static final class PatternKeyGen extends KeyGen {
    
    private static final String PIPE = "\\s*\\|\\s*";
    
    
    private enum Command {
      SUBSTITUTE() {
        private final Pattern CMD = Pattern.compile("s/(.*?)/(.*?)/?");

        @Override
        boolean matches(String cmd) {
          return cmd.startsWith("s/");
        }

        @Override
        String apply(String cmd, String str) {
          Matcher m = CMD.matcher(cmd);
          if (m.matches()) {
            return str.replace(m.group(1), m.group(2));
          }
          throw new IllegalArgumentException("invalid command `"+cmd+"` for "+name());
        }
      },
      ;
      
      abstract boolean matches(String cmd);
      
      abstract String apply(String cmd, String str);
    }
    
    private static final String RANDOM_STR = "{rstr}"; //this will be replaced by random string on each new key;
    private static final String COUNTER = "{n}"; //this will be replaced by incremented integer on each new key;
    
    private final String pattern;
    
    private final String[] commands;
    private final AtomicInteger count = new AtomicInteger(0);

    /**
     * creates a key generator using the given pattern supporting the following placeholders:
     * <ul>
     *   <li>{rstr} -> will be replaced by a randomly generated string</li>
     *   <li>{n} -> will be replaced by an integer incremented at each call to {@link #make(Object...)}</li>
     *   <li>{0}, {1}, {2}, ... -> dynamically replaced by positional parameters of {@link #make(Object...)}</li>
     *   <li>piped commands applied after the pattern <ul>
     *     <li>{0}_suffix | s/foo/bar/  -> adds a suffix to the first {@link #make(Object...)} param and then substitute occurrences of "foo" with "bar"</li>
     *   </ul></li>
     * </ul>
     *
     * @param pattern
     */
    public PatternKeyGen(String pattern) {
      String[] tokens = pattern.split(PIPE);
      this.pattern = tokens[0];
      this.commands = ArrayUtils.remove(tokens, 0);
    }

    public <T extends Keyed<T>> Key<T> make(Object... params) {
      String keyStr = pattern;
      for (int i = 0; i < params.length; i++) {
        keyStr = keyStr.replace("{"+i+"}", Objects.toString(params[i]));
      }
      keyStr = keyStr
              .replace(RANDOM_STR, Key.rand())
              .replace(COUNTER, Integer.toString(count.incrementAndGet()));
      for (String cmd : commands) {
        keyStr = applyCommand(cmd, keyStr);
      }
      return Key.make(keyStr);
    }
    
    private String applyCommand(String cmd, String str) {
      for (Command c : Command.values()) {
        if (c.matches(cmd)) 
          return c.apply(cmd, str);
      }
      throw new IllegalArgumentException("Invalid command: "+cmd);
    }

    @Override
    public String toString() {
      final StringBuilder sb = new StringBuilder("PatternKeyGen{");
      sb.append(", pattern='").append(pattern).append('\'');
      sb.append(", commands=").append(Arrays.toString(commands));
      sb.append(", count=").append(count);
      sb.append('}');
      return sb.toString();
    }
  }
}
