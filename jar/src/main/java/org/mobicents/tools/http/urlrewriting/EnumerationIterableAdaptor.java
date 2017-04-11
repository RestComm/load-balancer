package org.mobicents.tools.http.urlrewriting;

import java.util.Enumeration;
import java.util.Iterator;

/**
 * @author code4crafter@gmail.com
 */
public class EnumerationIterableAdaptor<T> implements Enumeration<T> {

    private Iterable<T> wrapped;

    private Iterator<T> wrappedIterator;

    public EnumerationIterableAdaptor(Iterable<T> wrapped) {
        this.wrapped = wrapped;
        this.wrappedIterator = wrapped.iterator();
    }

    @Override
    public boolean hasMoreElements() {
        return wrappedIterator.hasNext();
    }

    @Override
    public T nextElement() {
        return wrappedIterator.next();
    }
}
