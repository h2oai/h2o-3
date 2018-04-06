package water.parser.csv.reader;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * This is the main class for reading CSV data.
 *
 * @author Oliver Siegmar
 */
public final class CsvReader {

    /**
     * Field separator character (default: ',' - comma).
     */
    private char fieldSeparator = ',';

    /**
     * Text delimiter character (default: '"' - double quotes).
     */
    private char textDelimiter = '"';

    /**
     * Read first line as header line? (default: false).
     */
    private boolean containsHeader;

    /**
     * Skip empty rows? (default: true)
     */
    private boolean skipEmptyRows = true;

    /**
     * Throw an exception if CSV data contains different field count? (default: false).
     */
    private boolean errorOnDifferentFieldCount;

    /**
     * Sets the field separator character (default: ',' - comma).
     */
    public void setFieldSeparator(final char fieldSeparator) {
        this.fieldSeparator = fieldSeparator;
    }

    /**
     * Sets the text delimiter character (default: '"' - double quotes).
     */
    public void setTextDelimiter(final char textDelimiter) {
        this.textDelimiter = textDelimiter;
    }

    /**
     * Specifies if the first line should be the header (default: false).
     */
    public void setContainsHeader(final boolean containsHeader) {
        this.containsHeader = containsHeader;
    }

    /**
     * Specifies if empty rows should be skipped (default: true).
     */
    public void setSkipEmptyRows(final boolean skipEmptyRows) {
        this.skipEmptyRows = skipEmptyRows;
    }

    /**
     * Specifies if an exception should be thrown, if CSV data contains different field count
     * (default: false).
     */
    public void setErrorOnDifferentFieldCount(final boolean errorOnDifferentFieldCount) {
        this.errorOnDifferentFieldCount = errorOnDifferentFieldCount;
    }

    /**
     * Reads an entire file and returns a CsvContainer containing the data.
     *
     * @param file the file to read data from.
     * @param charset the character set to use - must not be {@code null}.
     * @return the entire file's data - never {@code null}.
     * @throws IOException if an I/O error occurs.
     */
    public CsvContainer read(final File file, final Charset charset) throws IOException {
        return read(
            Objects.requireNonNull(file.toPath(), "file must not be null"),
            Objects.requireNonNull(charset, "charset must not be null")
        );
    }

    /**
     * Reads an entire file and returns a CsvContainer containing the data.
     *
     * @param path the file to read data from.
     * @param charset the character set to use - must not be {@code null}.
     * @return the entire file's data - never {@code null}.
     * @throws IOException if an I/O error occurs.
     */
    public CsvContainer read(final Path path, final Charset charset) throws IOException {
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(charset, "charset must not be null");
        try (final Reader reader = newPathReader(path, charset)) {
            return read(reader);
        }
    }

    /**
     * Reads from the provided reader until the end and returns a CsvContainer containing the data.
     *
     * This library uses built-in buffering, so you do not need to pass in a buffered Reader
     * implementation such as {@link java.io.BufferedReader}.
     * Performance may be even likely better if you do not.
     *
     * @param reader the data source to read from.
     * @return the entire file's data - never {@code null}.
     * @throws IOException if an I/O error occurs.
     */
    public CsvContainer read(final Reader reader) throws IOException {
        final CsvParser csvParser =
            parse(Objects.requireNonNull(reader, "reader must not be null"));

        final List<CsvRow> rows = new ArrayList<>();
        CsvRow csvRow;
        while ((csvRow = csvParser.nextRow()) != null) {
            rows.add(csvRow);
        }

        if (rows.isEmpty()) {
            return null;
        }

        final List<String> header = containsHeader ? csvParser.getHeader() : null;
        return new CsvContainer(header, rows);
    }

    /**
     * Constructs a new {@link CsvParser} for the specified arguments.
     *
     * @param path the file to read data from.
     * @param charset the character set to use - must not be {@code null}.
     * @return a new CsvParser - never {@code null}.
     * @throws IOException if an I/O error occurs.
     */
    public CsvParser parse(final Path path, final Charset charset) throws IOException {
        return parse(newPathReader(
            Objects.requireNonNull(path, "path must not be null"),
            Objects.requireNonNull(charset, "charset must not be null")
        ));
    }

    /**
     * Constructs a new {@link CsvParser} for the specified arguments.
     *
     * @param file the file to read data from.
     * @param charset the character set to use - must not be {@code null}.
     * @return a new CsvParser - never {@code null}.
     * @throws IOException if an I/O error occurs.
     */
    public CsvParser parse(final File file, final Charset charset) throws IOException {
        return parse(
            Objects.requireNonNull(file, "file must not be null").toPath(),
            Objects.requireNonNull(charset, "charset must not be null")
        );
    }

    /**
     * Constructs a new {@link CsvParser} for the specified arguments.
     *
     * This library uses built-in buffering, so you do not need to pass in a buffered Reader
     * implementation such as {@link java.io.BufferedReader}.
     * Performance may be even likely better if you do not.
     *
     * @param reader the data source to read from.
     * @return a new CsvParser - never {@code null}.
     * @throws IOException if an I/O error occurs.
     */
    public CsvParser parse(final Reader reader) throws IOException {
        return new CsvParser(Objects.requireNonNull(reader, "reader must not be null"),
            fieldSeparator, textDelimiter, containsHeader, skipEmptyRows,
            errorOnDifferentFieldCount);
    }

    private static Reader newPathReader(final Path path, final Charset charset) throws IOException {
        return new InputStreamReader(Files.newInputStream(path, StandardOpenOption.READ), charset);
    }

}
