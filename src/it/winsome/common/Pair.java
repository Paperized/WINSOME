package it.winsome.common;

/**
 * A helper Pair class
 * @param <S> first type
 * @param <T> second type
 */
public class Pair<S, T> {
    public S first;
    public T second;

    public Pair(S s, T t) {
        first = s;
        second = t;
    }
}
