/*******************************************************************************
 * Copyright (c) 2009, 2016 Mountainminds GmbH & Co. KG and Contributors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Marc R. Hoffmann - initial API and implementation
 *
 *******************************************************************************/
package jacoco.report.internal.html.table;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * A index on a list of items sorted with a given {@link Comparator}. The index
 * does not change the list itself.
 *
 * @param <T>
 *            type of the items
 */
final class SortIndex<T> {

    private final Comparator<? super T> comparator;

    private class Entry implements Comparable<Entry> {

        final int idx;

        final T item;

        Entry(final int idx, final T item) {
            this.idx = idx;
            this.item = item;
        }

        public int compareTo(final Entry o) {
            return comparator.compare(item, o.item);
        }

    }

    private final List<Entry> list = new ArrayList<Entry>();

    private int[] positions;

    /**
     * Creates a new index based in the given comparator.
     *
     * @param comparator
     *            comparator to sort items
     */
    public SortIndex(final Comparator<? super T> comparator) {
        this.comparator = comparator;
    }

    /**
     * Initializes the index for the given list of items.
     *
     * @param items
     *            list of items
     */
    public void init(final List<? extends T> items) {
        this.list.clear();
        int idx = 0;
        for (final T i : items) {
            final Entry entry = new Entry(idx++, i);
            this.list.add(entry);
        }
        Collections.sort(list);
        if (positions == null || positions.length < items.size()) {
            positions = new int[items.size()];
        }
        int pos = 0;
        for (final Entry e : this.list) {
            positions[e.idx] = pos++;
        }
    }

    /**
     * Returns the sorted position of the element with the given index in the
     * items list provided to the init() method.
     *
     * @param idx
     *            index of a element of the list
     * @return its position in a sorted list
     */
    public int getPosition(final int idx) {
        return positions[idx];
    }

}
