package dev.shadowsoffire.placebo.util;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Supplier;

import com.google.common.base.Suppliers;

public record DeferredSet<E>(Supplier<Set<E>> supplier) implements Set<E> {

    public DeferredSet(Supplier<Set<E>> supplier) {
        this.supplier = Suppliers.memoize(supplier::get);
    }

    @Override
    public int size() {
        return this.set().size();
    }

    @Override
    public boolean isEmpty() {
        return this.set().isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return this.set().contains(o);
    }

    @Override
    public Iterator<E> iterator() {
        return this.set().iterator();
    }

    @Override
    public Object[] toArray() {
        return this.set().toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return this.set().toArray(a);
    }

    @Override
    public boolean add(E e) {
        return this.set().add(e);
    }

    @Override
    public boolean remove(Object o) {
        return this.set().remove(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return this.set().containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        return this.set().addAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return this.set().retainAll(c);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return this.set().removeAll(c);
    }

    @Override
    public void clear() {
        this.set().clear();
    }

    public Set<E> set() {
        return this.supplier.get();
    }

}
