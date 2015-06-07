package com.davidleston.stream;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * Collects only elements which sort first.
 * The {@link java.util.stream.Stream} being collected does not need to already be sorted.
 * For example, in an array of {"b", "a", "a"}, both "a" strings will be collected, but not "b".
 * <p>
 * Will often be faster than alternatives such as:
 * <ul>
 *   <li>{@link Collectors#groupingBy(Function)}, sorting keys, selecting group by first-ranked key</li>
 *   <li>Finding min value, filter other values</li>
 * </ul>
 * <p>
 * Given a stream of n items with m first-ranked items, n comparisons
 * and m to n invocations of the downstream collector are made.
 * <p>
 * Assumes the downstream {@link Collector} supports having containers created that are never
 * combined via a {@link Collector#combiner()} nor finished via a {@link Collector#finisher()}.
 * <p>
 * Supports null elements if the downstream {@link Collector} and {@link Comparator} support null elements.
 * The default downstream {@link Collector} is {@link Collectors#toList()}, which supports null elements.
 * The default {@link Comparator} is {@link Comparator#naturalOrder()} which does not support null elements.
 * For a {@link Comparator} that supports null elements, use {@link Comparator#nullsFirst(Comparator)},
 * {@link Comparator#nullsLast(Comparator)}, or other null-safe {@link Comparator}.
 *
 * @param <T> the type of input elements to the reduction operation
 * @param <A> the mutable accumulation type of the reduction operation of the downstream {@link Collector}
 * @param <R> the result type of the reduction operation
 */
public final class FirstRankCollector<T, A, R> implements Collector<T, FirstRankCollector.Container<T, A, R>, R> {
  private final Collector<T, A, R> downstream;
  private final Comparator<T> comparator;

  private FirstRankCollector(Collector<T, A, R> downstream, Comparator<T> comparator) {
    this.downstream = downstream;
    this.comparator = comparator;
  }

  /**
   * @param <T> the type of input elements to the reduction operation
   * @return FirstRankCollector orders by natural order, reduces into a List
   */
  public static <T extends Comparable<T>> Collector<T, ?, List<T>> create() {
    return create(Collectors.<T>toList(), Comparator.<T>naturalOrder());
  }

  /**
   * @param downstream reduce into this {@link Collector}
   * @param <T>        the type of input elements to the reduction operation
   * @param <R>        the result type of the reduction operation
   * @return FirstRankCollector orders by natural order
   */
  public static <T extends Comparable<T>, R> Collector<T, ?, R> create(Collector<T, ?, R> downstream) {
    return create(downstream, Comparator.<T>naturalOrder());
  }

  /**
   * @param comparator order by this {@link Comparator}
   * @param <T>        the type of input elements to the reduction operation
   * @return FirstRankCollector reduces into a List
   */
  public static <T> Collector<T, ?, List<T>> create(Comparator<T> comparator) {
    return create(Collectors.<T>toList(), comparator);
  }

  /**
   * @param downstream reduce into this {@link Collector}
   * @param comparator order by this {@link Comparator}
   * @param <T>        the type of input elements to the reduction operation
   * @param <R>        the result type of the reduction operation
   * @return FirstRankCollector
   */
  public static <T, R> Collector<T, ?, R> create(Collector<T, ?, R> downstream, Comparator<T> comparator) {
    return new FirstRankCollector<>(downstream, comparator);
  }

  @Override
  public Supplier<Container<T, A, R>> supplier() {
    return () -> new Container<>(downstream, comparator);
  }

  @Override
  public BiConsumer<Container<T, A, R>, T> accumulator() {
    return Container::collect;
  }

  @Override
  public BinaryOperator<Container<T, A, R>> combiner() {
    BinaryOperator<A> combiner = downstream.combiner();
    return (left, right) -> {
      int comparisonResult = left.compareTo(right);
      if (comparisonResult > 0) {
        return right;
      }
      if (comparisonResult == 0) {
        left.resultContainer = combiner.apply(left.resultContainer, right.resultContainer);
      }
      return left;
    };
  }

  @Override
  public Function<Container<T, A, R>, R> finisher() {
    Function<A, R> finisher = downstream.finisher();
    return tracker -> finisher.apply(tracker.resultContainer);
  }

  @Override
  public Set<Characteristics> characteristics() {
    if (downstream.characteristics().contains(Characteristics.UNORDERED)) {
      return Collections.unmodifiableSet(EnumSet.of(Collector.Characteristics.UNORDERED));
    }
    return Collections.emptySet();
  }

  protected final static class Container<T, A, R> implements Comparable<Container<T, A, R>> {
    private final Comparator<T> comparator;
    private final Supplier<A> downstreamSupplier;
    private final BiConsumer<A, T> downstreamAccumulator;
    private A resultContainer;
    private boolean collectionHasOccurred = false;
    private T containedElement;

    private Container(Collector<T, A, R> collector, Comparator<T> comparator) {
      this.comparator = comparator;
      downstreamSupplier = collector.supplier();
      downstreamAccumulator = collector.accumulator();
      resultContainer = downstreamSupplier.get();
    }

    private void collect(T element) {
      if (!collectionHasOccurred) {
        downstreamAccumulator.accept(resultContainer, element);
        collectionHasOccurred = true;
        containedElement = element;
      } else {
        int comparisonResult = comparator.compare(containedElement, element);
        if (comparisonResult == 0) {
          downstreamAccumulator.accept(resultContainer, element);
        } else if (comparisonResult > 0) {
          resultContainer = downstreamSupplier.get();
          downstreamAccumulator.accept(resultContainer, element);
          containedElement = element;
        }
      }
    }

    @Override
    public int compareTo(Container<T, A, R> other) {
      if (!collectionHasOccurred) {
        return 1;
      }
      if (!other.collectionHasOccurred) {
        return -1;
      }
      return comparator.compare(containedElement, other.containedElement);
    }
  }
}
