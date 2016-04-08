package jacoco.report.internal.html.parse;
import jacoco.report.internal.html.parse.util.NameList;
import jacoco.report.internal.html.parse.util.NameString;
import org.jacoco.core.analysis.ICoverageNode.CounterEntity;

import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.util.*;

/**
 * Created by nkalonia1 on 3/23/16.
 */
public class YAMLParser {
    private Set<CounterEntity> _default_headers;

    private boolean _log_out;
    private boolean _log_err;
    private OutputStreamWriter _out;
    private OutputStreamWriter _err;

    private PackageName _package_name;
    private ClassName _class_name;
    private MethodName _method_name;
    private boolean _propagate;
    private static final NameString _wild_name = new NameString("*");
    private static final NameList _wild_list = new NameList(new NameString[] {_wild_name}, true);
    private Map<CounterEntity, Double> _default_values;
    private Map<CounterEntity, Double> _current_values;

    private List<ParseItem> _items;

    public YAMLParser(OutputStream out, OutputStream err) {
        if (out != null) {
            _out = new OutputStreamWriter(out);
            _log_out = true;
        } else _log_out = false;
        if (err != null) {
            _err = new OutputStreamWriter(err);
            _log_err = true;
        } else _log_err = false;
        _package_name = new PackageName();
        _class_name = new ClassName();
        _method_name = new MethodName();
        _propagate = false;
        _default_values = new HashMap<CounterEntity, Double>();
        _current_values = new HashMap<CounterEntity, Double>();
        _items = new ArrayList<ParseItem>();
        for (CounterEntity ce : CounterEntity.values()) {
            _default_values.put(ce, 0.0);
        }
        _default_headers = new HashSet<CounterEntity>();
        for (CounterEntity ce : CounterEntity.values()) {
            _default_headers.add(ce);
        }
    }

    public YAMLParser(OutputStream out) { this(out, out); }

    public YAMLParser() { this(null, null); }

    public List<ParseItem> parse(File params) {
        if (params == null) return _items;
        Yaml yaml = new Yaml();
        try {
            _current_values = new HashMap<CounterEntity, Double>();
            for(Object o : yaml.loadAll(new FileInputStream(params))) {
                if (!(o instanceof Map)) {err("Object " + o + " is not a map. Skipping..."); continue; }
                log("Starting block...");
                Map m = (Map) o;

                Object val;

                if ((val = m.get("default")) != null) {
                    log("Found \"default\"");
                    if (val instanceof Map) {
                        setDefault((Map) val);
                    } else if (val instanceof Number) {
                        setDefault((Number) val);
                    } else {
                        err("Invalid value for \"default\":" + val);
                    }
                }

                // Load Package Name
                if ((val = m.get("package")) != null) {
                    log("Found \"package\"");
                    if (val instanceof Map) {
                        setPackageName((Map) val);
                    } else if (val instanceof String) {
                        setPackageName((String) val);
                    } else {
                        err("Invalid value for \"package\":" + val);
                    }
                    _class_name = new ClassName();
                    _method_name = new MethodName();
                }

                // Load Class Name
                if ((val = m.get("class")) != null) {
                    log("Found \"class\"");
                    if (val instanceof Map) {
                        setClassName((Map) val);
                    } else if (val instanceof String) {
                        setClassName((String) val);
                    } else {
                        err("Invalid value for \"class\":" + val);
                    }
                    _method_name = new MethodName();
                }

                // Load Method Name
                if ((val = m.get("method")) != null) {
                    log("Found \"method\"");
                    if (val instanceof Map) {
                        setMethodName((Map) val);
                    } else if (val instanceof String) {
                        setMethodName((String) val);
                    } else {
                        err("Invalid value for \"method\":" + val);
                    }
                }

                // Set propagate
                if ((val = m.get("propagate")) != null) {
                    log("Found \"propagate\"");
                    if (val instanceof Boolean) {
                        setPropagate((boolean) val);
                    } else {
                        err("Invalid value for \"propagate\"");
                    }
                }

                // Apply values
                if ((val = m.get("values")) != null) {
                    log("Found \"values\"");
                    if (val instanceof Map) {
                        setValues((Map) val);
                    } else if (val instanceof Number) {
                        setValues((Number) val);
                    } else {
                        err("Invalid value for \"values\"");
                    }
                }

                _items.add(createParseItem());
                log("Ending block...");
            }
        } catch (FileNotFoundException fnfe) {
            err("Couldn't find file: " + params);
        }

        return _items;
    }

    private void setDefault(Map m) {
        for (CounterEntity ce :_default_headers) {
            String key = ce.name().toLowerCase();
            if (m.get(key) instanceof Number) {
                _default_values.put(ce, ((Number) m.get(key)).doubleValue());
            }
            log("For \"" + key + "\": " + _default_values.get(ce));
        }
    }

    private void setDefault(Number n) {
        Map<String, Number> m = new HashMap<String, Number>();
        for (CounterEntity ce :_default_headers) {
            m.put(ce.name().toLowerCase(), n);
        }
        setDefault(m);
    }

    private void setPackageName(Map m) {
        NameString name;
        if (m.containsKey("name") && m.get("name") instanceof String) {
            String string_name = (String) m.get("name");
            log("Found name: " + string_name);
            name = new NameString(string_name.replace('.','/'));
            _package_name = new PackageName(name);
        } else {
            log("Did not find valid value for \"name\"");
            _package_name = new PackageName(_wild_name);
        }
    }

    private void setPackageName(String s) {
        Map<String, String> m = new HashMap<String, String>();
        m.put("name", s);
        setPackageName(m);
    }

    private void setClassName(Map m) {
        NameString name;
        NameString signature;
        NameString superclass;
        NameList interfaces;
        if (m.containsKey("name") && m.get("name") instanceof String) {
            String string_name = (String) m.get("name");
            log("Found name: " + string_name);
            name = new NameString(_package_name.getPackageName().get() + "/" + string_name.replace('.','$'));
        } else {
            log("Did not find valid value for \"name\"");
            name = _wild_name;
        }
        if (m.containsKey("signature") && m.get("signature") instanceof String) {
            String string_sig = (String) m.get("signature");
            log("Found signature: " + string_sig);
            signature = new NameString(string_sig);
        } else {
            log("Did not find valid value for \"signature\"");
            signature = _wild_name;
        }
        if (m.containsKey("superclass") && m.get("superclass") instanceof String) {
            String string_super = (String) m.get("superclass");
            log("Found superclass: " + string_super);
            superclass = new NameString(string_super);
        } else {
            log("Did not find valid value for \"superclass\"");
            superclass = _wild_name;
        }
        if (m.containsKey("interfaces") && m.get("interfaces") instanceof String[]) {
            String[] string_inter = (String[]) m.get("interfaces");
            log("Found interfaces: " + Arrays.toString(string_inter));
            interfaces = new NameList(string_inter);
        } else {
            log("Did not find valid value for \"interfaces\"");
            interfaces = _wild_list;
        }
        _class_name = new ClassName(name, signature, superclass, interfaces);
    }

    private void setClassName(String s) {
        Map<String, String> m = new HashMap<String, String>();
        m.put("name", s);
        setClassName(m);
    }

    private void setMethodName(Map m) {
        NameString name;
        NameString desc;
        NameString signature;
        if (m.containsKey("name") && m.get("name") instanceof String) {
            String string_name = (String) m.get("name");
            log("Found name: " + string_name);
            name = new NameString(string_name);
        } else {
            log("Did not find valid value for \"name\"");
            name = _wild_name;
        }
        if (m.containsKey("desc") && m.get("desc") instanceof String) {
            String string_desc = (String) m.get("desc");
            log("Found description: " + string_desc);
            desc = new NameString(string_desc);
        } else {
            log("Did not find valid value for \"desc\"");
            desc = _wild_name;
        }
        if (m.containsKey("signature") && m.get("signature") instanceof String) {
            String string_sig = (String) m.get("signature");
            log("Found signature: " + string_sig);
            signature = new NameString(string_sig);
        } else {
            log("Did not find valid value for \"signature\"");
            signature = _wild_name;
        }
        _method_name = new MethodName(name, desc, signature);
    }

    private void setMethodName(String s) {
        Map<String, String> m = new HashMap<String, String>();
        m.put("name", s);
        setMethodName(m);
    }

    private void setValues(Map m) {
        for (CounterEntity ce :_default_headers) {
            String key = ce.name().toLowerCase();
            if (m.get(key) instanceof Number) {
                _current_values.put(ce, ((Number) m.get(key)).doubleValue());
            }
            log("For \"" + key + "\": " + _current_values.get(ce));
        }
    }

    private void setValues(Number n) {
        Map<String, Number> m = new HashMap<String, Number>();
        for (CounterEntity ce :_default_headers) {
            m.put(ce.name().toLowerCase(), n);
        }
        setValues(m);
    }

    private ParseItem createParseItem() {
        ParseItem p = new ParseItem(_package_name, _class_name, _method_name, _propagate);
        for (CounterEntity ce : _default_headers) {
            if (_current_values.containsKey(ce)) {
                p._values.put(ce, _current_values.get(ce));
            } else {
                p._values.put(ce, _default_values.get(ce));
            }
        }
        return p;
    }

    private void setPropagate(boolean b) {
        _propagate = b;
        log("\"Propagate\" is set to " + b);
    }

    private void log(String s) {
        if (_log_out) {
            try {
                _out.write(s = "LOG: " + s + "\n", 0, s.length());
                _out.flush();
            } catch (IOException ioe) {
                System.err.println(ioe);
            }
        }
    }

    private void err(String s) {
        if (_log_err) {
            try {
                _err.write(s = "ERROR: " + s + "\n", 0, s.length());
                _err.flush();
            } catch (IOException ioe) {
                System.err.println(ioe);
            }
        }
    }
}
