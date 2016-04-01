package jacoco.report.internal.html.parse;
import jacoco.report.internal.html.parse.util.NameList;
import jacoco.report.internal.html.parse.util.NameString;
import org.jacoco.core.analysis.ICoverageNode.CounterEntity;

import java.io.*;
import java.util.*;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by nkalonia1 on 3/23/16.
 */
public class DSVParser {
    private List<CounterEntity> _default_headers;

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
    private static final NameString _wild = new NameString("*");
    private Map<CounterEntity, Double> _default_values;

    private List<ParseItem> _items;

    public DSVParser(OutputStream out, OutputStream err) {
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
        _default_headers = new ArrayList<CounterEntity>(6);
        _default_headers.add(CounterEntity.INSTRUCTION);
        _default_headers.add(CounterEntity.BRANCH);
        _default_headers.add(CounterEntity.COMPLEXITY);
        _default_headers.add(CounterEntity.LINE);
        _default_headers.add(CounterEntity.METHOD);
        _default_headers.add(CounterEntity.CLASS);
    }

    public DSVParser(OutputStream out) { this(out, out); }

    public DSVParser() { this(null, null); }

    public List<ParseItem> parse(File params) {
        if (params == null) return _items;
        try {
            List<CounterEntity> headers = _default_headers;
            Scanner sc = new Scanner(params);
            while (sc.hasNext()) {
                Matcher m;
                String token = getNextQuoted(sc);

                m = _DEFAULT.matcher(token);
                if (m.matches()) {
                    log("Found default token: " + m.group(1));
                    String group = m.group(2) != null ? m.group(2) : "";
                    setDefaults(getNextQuoted(sc), getHeaders(group));
                    continue;
                }

                m = _HEADERS.matcher(token);
                if (m.matches()) {
                    log("Found headers token: " + m.group(1));
                    _default_headers = getHeaders(getNextQuoted(sc));
                    continue;
                }

                m = _PACKAGE.matcher(token);
                if (m.matches()) {
                    log("Found package token: " + m.group(1));
                    setPackageName(getNextQuoted(sc));
                    _class_name.clear();
                    _method_name.clear();
                    continue;
                }

                m = _CLASS.matcher(token);
                if (m.matches()) {
                    log("Found class token: " + m.group(1));
                    setClassName(getNextQuoted(sc));
                    _method_name.clear();
                    continue;
                }

                m = _METHOD.matcher(token);
                if (m.matches()) {
                    log("Found method token: " + m.group(1));
                    setMethodName(getNextQuoted(sc));
                    continue;
                }

                m = _PROPAGATE.matcher(token);
                if (m.matches()) {
                    log("Found propagate token: " + m.group(1));
                    setPropagate(getNextQuoted(sc));
                    continue;
                }
                log("Interpreting token '" + token + "' as coverage parameters...");
                _items.add(applyValues(token));
            }
        } catch (FileNotFoundException fnfe) {
            err("Couldn't find file: " + params);
        }

        return _items;
    }

    private void setPackageName(String s) {
        NameString name;

        Matcher m = _PACKAGE_NAME.matcher(s);
        if (m.matches()) {
            String p_name = m.group(1).replace('.', '/');
            name = isWild(p_name) ? _wild : new NameString(p_name);
            _package_name.set(name);
        } else {
            err("Invalid package name: " + s);
        }

    }

    private boolean isWild(String s) {
        Matcher m = _WILD_VALUE.matcher(s);
        return m.matches();
    }

    private void setClassName(String s) {
        _class_name.set(new NameString(_package_name.getPackageName().get() + "/" + s.trim().replace('.', '$')), _wild, _wild, new NameList(new NameString[] {_wild}, true));
    }

    private void setMethodName(String s) {
        _method_name.set(new NameString(s.trim()), _wild, _wild);
    }

    private ParseItem applyValues(String values) {
        log("Parsing values: " + values);
        Scanner sc = new Scanner(values.trim());
        sc.useDelimiter("\\s*,\\s*");
        ParseItem p = new ParseItem(_package_name.clone(), _class_name.clone(), _method_name.clone(), _propagate);
        String value = null;
        for (CounterEntity head : _default_headers) {
            if (sc.hasNext()) value = sc.next();
            if (value == null) {
                err("Could not find argument for header " + head.name());
                break;
            }
            log("Found value for header " + head.name() +": " + value);
            p._values.put(head, parseDouble(head, value));
        }
        if (sc.hasNext()) {
            err("Too many arguments: " + values);
        }
        return p;
    }

    private List<CounterEntity> getHeaders(String s) {
        s = s.trim();
        if (s.isEmpty()) return _default_headers;
        List<CounterEntity> headers_list = new ArrayList<CounterEntity>();
        Scanner sc = new Scanner(s);
        sc.useDelimiter("\\s*,\\s*");
        while (sc.hasNext()) {
            boolean found = false;
            String header = sc.next();
            for (CounterEntity ce : CounterEntity.values()) {
                if (header.toLowerCase().equals(ce.name().toLowerCase())) {
                    log("Found header: " + ce.name());
                    headers_list.add(ce);
                    found = true;
                    break;
                }
            }
            if (!found) {
                err("Could not identify header: " + header);
            }
        }
        return headers_list;
    }

    private String getNextQuoted(Scanner sc) {
        try {
            sc.skip(_QUOTE);
            MatchResult m = sc.match();
            return m.group(1) == null ? m.group(2) : m.group(1);
        } catch (NoSuchElementException nsee) {
            err("Could not find valid parameter");
        }
        return "";
    }

    private void setDefaults(String s, List<CounterEntity> headers) {
        Scanner sc = new Scanner(s);
        sc.useDelimiter(",");
        String val = null;
        for (CounterEntity ce : headers) {
            if (sc.hasNext()) {
                val = sc.next().trim();
                _default_values.put(ce, parseDouble(ce, val));
            } else {
                _default_values.put(ce, parseDouble(ce, val));
            }
        }
        if (sc.hasNext()) {
            err("Too many arguments: " + s);
        }
    }

    private void setPropagate(String s) {
        s = s.trim();
        _propagate = Boolean.parseBoolean(s);
    }

    private double parseDouble(CounterEntity ce, String s) {
        Matcher m = _DEFAULT_VALUE.matcher(s);
        if (m.matches()) {
            return _default_values.get(ce);
        } else {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException nfe) {
                err("Invalid value: " + s);
            }
        }
        return Double.NaN;
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
