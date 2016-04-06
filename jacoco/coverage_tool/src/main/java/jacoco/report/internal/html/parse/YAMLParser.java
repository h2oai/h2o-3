package jacoco.report.internal.html.parse;
import jacoco.report.internal.html.parse.util.NameList;
import jacoco.report.internal.html.parse.util.NameString;
import org.jacoco.core.analysis.ICoverageNode.CounterEntity;

import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.util.*;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by nkalonia1 on 3/23/16.
 */
public class YAMLParser {
    private Set<CounterEntity> _default_headers;

    private static final Pattern _DEFAULT = Pattern.compile("(default(?:\\[([^\\]]*)\\])?):"); // Entire token in group 1, parameters in group 2
    private static final Pattern _HEADERS = Pattern.compile("(headers):"); // Entire token in group 1
    private static final Pattern _PACKAGE = Pattern.compile("(package):"); // Entire token in group 1
    private static final Pattern _CLASS = Pattern.compile("(class):"); // Entire token in group 1
    private static final Pattern _METHOD = Pattern.compile("(method):"); // Entire token in group 1
    private static final Pattern _PROPAGATE = Pattern.compile("(propagate):"); // Entire token in group 1
    private static final Pattern _QUOTE = Pattern.compile("\\s*(?:\\\"([^\"]*)\\\"|(\\S*)\\s*)", Pattern.MULTILINE); // Quoted result in group 1, unquoted in group 2
    private static final Pattern _DEFAULT_VALUE = Pattern.compile("\\*");
    private static final Pattern _WILD_VALUE = Pattern.compile("\\*");
    private static final Pattern _PACKAGE_NAME = Pattern.compile("\\s*(\\S*)\\s*");

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
            for(Object o : yaml.loadAll(new FileInputStream(params))) {
                if (!(o instanceof Map)) {err("Object " + o + " is not a map"); continue; }
                Map m = (Map) o;

                 // Load Package Name
                if (m.containsKey("package")) {
                    Object p_o = m.get("package");
                    if (p_o instanceof Map) {
                        setPackageName((Map) p_o);
                    } else if (p_o instanceof String) {
                        setPackageName((String) p_o);
                    } else {
                        err("Invalid value for \"package\"");
                    }
                    _class_name.clear();
                    _method_name.clear();
                }

                // Load Class Name
                if (m.containsKey("class")) {
                    Object c_o = m.get("class");
                    if (c_o instanceof Map) {
                        setClassName((Map) c_o);
                    } else if (c_o instanceof String) {
                        setClassName((String) c_o);
                    } else {
                        err("Invalid value for \"class\"");
                    }
                    _method_name.clear();
                }

                // Load Method Name
                if (m.containsKey("class")) {
                    Object m_o = m.get("class");
                    if (m_o instanceof Map) {
                        setMethodName((Map) m_o);
                    } else if (m_o instanceof String) {
                        setMethodName((String) m_o);
                    } else {
                        err("Invalid value for \"method\"");
                    }
                }

                // Set propagate
                if (m.containsKey("propagate")) {
                    Object b_o = m.get("propagate");
                    if (b_o instanceof Boolean) {
                        setPropagate((boolean) b_o);
                    } else {
                        err("Invalid value for \"propagate\"");
                    }
                }

                // Apply values
                if (m.containsKey("values")) {
                    Object v_o = m.get("values");
                    if (v_o instanceof Map) {
                        _items.add(setValues((Map) v_o));
                    } else if (v_o instanceof Number) {
                        _items.add(setValues(((Number) v_o).doubleValue()));
                    } else {
                        err("Invalid value for \"values\"");
                    }
                }
            }
        } catch (FileNotFoundException fnfe) {
            err("Couldn't find file: " + params);
        }

        return _items;
    }

    private void setPackageName(Map m) {
        NameString name;
        System.out.println(m.get("name") instanceof String);
        if (m.containsKey("name") && m.get("name") instanceof String) {
            name = new NameString(((String) m.get("name")).replace('.','/'));
            _package_name.set(name);
        } else {
            log("Did not find valid value for \"name\"");
            _package_name.set(_wild_name);
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
            name = new NameString(_package_name.getPackageName().get() + "/" + ((String) m.get("name")).replace('.','$'));
        } else {
            log("Did not find valid value for \"name\"");
            name = _wild_name;
        }
        if (m.containsKey("signature") && m.get("signature") instanceof String) {
            signature = new NameString((String) m.get("signature"));
        } else {
            log("Did not find valid value for \"signature\"");
            signature = _wild_name;
        }
        if (m.containsKey("superclass") && m.get("superclass") instanceof String) {
            superclass = new NameString((String) m.get("superclass"));
        } else {
            log("Did not find valid value for \"superclass\"");
            superclass = _wild_name;
        }
        if (m.containsKey("interfaces") && m.get("interfaces") instanceof String[]) {
            interfaces = new NameList((String[]) m.get("interfaces"));
        } else {
            log("Did not find valid value for \"interfaces\"");
            interfaces = _wild_list;
        }
        _class_name.set(name, signature, superclass, interfaces);
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
            name = new NameString(_package_name.getPackageName().get() + "/" + ((String) m.get("name")).replace('.','$'));
        } else {
            log("Did not find valid value for \"name\"");
            name = _wild_name;
        }
        if (m.containsKey("desc") && m.get("desc") instanceof String) {
            desc = new NameString((String) m.get("desc"));
        } else {
            log("Did not find valid value for \"desc\"");
            desc = _wild_name;
        }
        if (m.containsKey("signature") && m.get("signature") instanceof String) {
            signature = new NameString((String) m.get("superclass"));
        } else {
            log("Did not find valid value for \"signature\"");
            signature = _wild_name;
        }
        _method_name.set(name, desc, signature);
    }

    private void setMethodName(String s) {
        Map<String, String> m = new HashMap<String, String>();
        m.put("name", s);
        setMethodName(m);
    }

    private ParseItem setValues(Map m) {
        ParseItem p = new ParseItem(_package_name, _class_name, _method_name, _propagate);
        for (CounterEntity ce :_default_headers) {
            String key = ce.name().toLowerCase();
            if (m.containsKey(key) && m.get(key) instanceof Double) {
                p._values.put(ce, (Double) m.get(key));
            } else {
                p._values.put(ce, _default_values.get(ce));
            }
        }
        return p;
    }

    private ParseItem setValues(Double d) {
        Map<String, Double> m = new HashMap<String, Double>();
        for (CounterEntity ce :_default_headers) {
            m.put(ce.name().toLowerCase(), d);
        }
        return setValues(m);
    }

    private void setPropagate(boolean b) {
        _propagate = b;
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
