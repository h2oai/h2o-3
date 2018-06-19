package water.api;

import water.Key;
import water.Lockable;
import water.api.schemas3.KeyV3;

public class ScalaCodeResult extends Lockable<ScalaCodeResult> {
    public String code;
    public String scalaStatus;
    public  String scalaResponse;
    public String scalaOutput;
    /**
     * Create a Lockable object, if it has a {@link Key}.
     *
     * @param key key
     */
    public ScalaCodeResult(Key<ScalaCodeResult> key) {
        super(key);
    }

    @Override public Class<KeyV3.ScalaCodeResultV3> makeSchema() { return KeyV3.ScalaCodeResultV3.class; }
}
