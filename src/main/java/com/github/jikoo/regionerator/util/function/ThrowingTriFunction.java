package com.github.jikoo.regionerator.util.function;

public interface ThrowingTriFunction<T, U, R, S> {

	T apply(U u, R r, S s) throws Exception;

}
