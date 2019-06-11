package com.github.jikoo.regionerator.tuple;

import org.jetbrains.annotations.NotNull;

public class Pair<L, R> {

	private L left;
	private R right;

	public Pair(@NotNull L left, @NotNull R right) {
		this.left = left;
		this.right = right;
	}

	public @NotNull
    L getLeft() {
		return left;
	}

	public @NotNull
    R getRight() {
		return right;
	}

	public void setLeft(@NotNull L left) {
		this.left = left;
	}

	public void setRight(@NotNull R right) {
		this.right = right;
	}

	@Override
	public int hashCode() {
		int hash = 1;
		hash = hash * 17 * left.hashCode();
		hash = hash * 17 * right.hashCode();
		return hash;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!getClass().isInstance(obj)) {
			return false;
		}
		Pair other = (Pair) obj;
		return left.equals(other.left) && right.equals(other.right);
	}

}
