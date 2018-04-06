/*
 * Copyright 2015 Oliver Siegmar
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package water.parser.csv.reader;

import java.util.Collections;
import java.util.List;

/**
 * Class for holding a complete CSV file.
 *
 * @author Oliver Siegmar
 */
public final class CsvContainer {

    private final List<String> header;
    private final List<CsvRow> rows;

    CsvContainer(final List<String> header, final List<CsvRow> rows) {
        this.header = header;
        this.rows = rows;
    }

    /**
     * Returns the number of rows in this container.
     *
     * @return the number of rows in this container
     */
    public int getRowCount() {
        return rows.size();
    }

    /**
     * Returns the header row - might be {@code null} if no header exists.
     * The returned list is unmodifiable.
     *
     * @return the header row - might be {@code null} if no header exists
     */
    public List<String> getHeader() {
        return header;
    }

    /**
     * Returns a CsvRow by its index (starting with 0).
     *
     * @param index index of the row to return
     * @return the row by its index
     * @throws IndexOutOfBoundsException if index is out of range
     */
    public CsvRow getRow(final int index) {
        return rows.get(index);
    }

    /**
     * Returns an unmodifiable list of rows.
     *
     * @return an unmodifiable list of rows
     */
    public List<CsvRow> getRows() {
        return Collections.unmodifiableList(rows);
    }

}
