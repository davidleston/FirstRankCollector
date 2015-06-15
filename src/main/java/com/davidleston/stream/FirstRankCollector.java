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
 * Maintains stream encounter order if the downstream collector does not have the
 * {@link java.util.stream.Collector.Characteristics#UNORDERED} characteristic.
 * Supports concurrent collection.
 * <p>
 * Will often be faster than alternatives such as:
 * <ul>
 *   <li>{@link Collectors#groupingBy(Function)}, sort keys, select group by first-ranked key</li>
 *   <li>Find min value, filter other values</li>
 * </ul>
 * <p>
 * Given a stream of n elements with m first-ranked elements:
 * <table>
 *   <tr>
 *     <th></th>
 *     <th>comparisons</th>
 *     <th>downstream accumulations</th>
 *     <th>downstream accumulators instantiated</th>
 *     <th>iterations</th>
 *   </tr>
 *   <tr>
 *     <th>best case</th>
 *     <td>n</td>
 *     <td>m</td>
 *     <td>1</td>
 *     <td>1</td>
 *   </tr>
 *   <tr>
 *     <th>worst case</th>
 *     <td>n</td>
 *     <td>n</td>
 *     <td>n - m + 1</td>
 *     <td>1</td>
 *   </tr>
 * </table>
 * Best case is when all first-ranked elements are at the beginning of the stream.
 * Worst case is when the stream is in reverse order and none of the elements that are not first-ranked are equal.
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
 * @param <T> the type of input elements
 * @param <K> the type of the keys
 * @param <A> the intermediate accumulation type of the downstream collector
 * @param <R> result type of the downstream collector
 */
public final class FirstRankCollector<T, K, A, R> implements Comparable<FirstRankCollector<T, K, A, R>> {
  private final Function<? super T, ? extends K> classifier;
  private final Comparator<K> comparator;
  private final Supplier<A> downstreamSupplier;
  private final BiConsumer<A, T> downstreamAccumulator;
  private A resultContainer;
  private boolean collectionHasOccurred = false;
  private K key;

  private FirstRankCollector(Function<? super T, ? extends K> classifier, Collector<T, A, R> downstream, Comparator<K> comparator) {
    this.classifier = classifier;
    this.comparator = comparator;
    downstreamSupplier = downstream.supplier();
    downstreamAccumulator = downstream.accumulator();
    resultContainer = downstreamSupplier.get();
  }

  /**
   * @param <T> the type of input elements to the reduction operation
   * @return FirstRankCollector orders by natural order, reduces into a List
   */
  public static <T extends Comparable<T>> Collector<T, ?, List<T>> create() {
    return create(Collectors.<T>toList(), Comparator.<T>naturalOrder());
  }

  /**
   * @param downstream a {@code Collector} implementing the downstream reduction
   * @param <T>        the type of input elements
   * @param <R>        result type of the downstream collector
   * @return FirstRankCollector orders by natural order
   */
  public static <T extends Comparable<T>, R> Collector<T, ?, R> create(Collector<T, ?, R> downstream) {
    return create(downstream, Comparator.<T>naturalOrder());
  }

  /**
   * @param comparator order by this {@link Comparator}
   * @param <T>        the type of input elements
   * @return FirstRankCollector reduces into a List
   */
  public static <T> Collector<T, ?, List<T>> create(Comparator<T> comparator) {
    return create(Collectors.<T>toList(), comparator);
  }

  /**
   * @param downstream a {@code Collector} implementing the downstream reduction
   * @param comparator order by this {@link Comparator}
   * @param <T>        the type of input elements
   * @param <A>        the intermediate accumulation type of the downstream collector
   * @param <R>        result type of the downstream collector
   * @return FirstRankCollector
   */
  public static <T, A, R> Collector<T, ?, R> create(Collector<T, A, R> downstream, Comparator<T> comparator) {
    Function<A, R> finisher = downstream.finisher();
    return create(Function.<T>identity(), downstream, comparator,
        (FirstRankCollector<T, T, A, R> container) -> finisher.apply(container.resultContainer));
  }

  /**
   * @param classifier    a classifier function mapping input elements to keys
   * @param <T>           the type of input elements
   * @param <K>           the type of the keys, which are used for ranking
   * @return an immutable map entry containing the first-ranked key
   */
  public static <T, K extends Comparable<K>> Collector<T, ?, Map.Entry<K, List<T>>> create(
      Function<? super T, ? extends K> classifier) {
    return create(classifier, Collectors.toList(), Comparator.naturalOrder());
  }

  /**
   * @param classifier    a classifier function mapping input elements to keys
   * @param keyComparator a {@code Comparator} for comparing keys
   * @param <T>           the type of input elements
   * @param <K>           the type of the keys, which are used for ranking
   * @return an immutable map entry containing the first-ranked key
   */
  public static <T, K> Collector<T, ?, Map.Entry<K, List<T>>> create(
      Function<? super T, ? extends K> classifier, Comparator<K> keyComparator) {
    return create(classifier, Collectors.toList(), keyComparator);
  }

  /**
   * @param classifier a classifier function mapping input elements to keys
   * @param downstream a {@code Collector} implementing the downstream reduction
   * @param <T>        the type of input elements
   * @param <K>        the type of the keys, which are used for ranking
   * @param <R>        result type of the downstream collector
   * @return an immutable map entry containing the first-ranked key
   */
  public static <T, K extends Comparable<K>, R> Collector<T, ?, Map.Entry<K, R>> create(
      Function<? super T, ? extends K> classifier, Collector<T, ?, R> downstream) {
    return create(classifier, downstream, Comparator.naturalOrder());
  }

  /**
   * @param classifier    a classifier function mapping input elements to keys
   * @param downstream    a {@code Collector} implementing the downstream reduction
   * @param keyComparator a {@code Comparator} for comparing keys
   * @param <T>           the type of input elements
   * @param <K>           the type of the keys, which are used for ranking
   * @param <A>           the intermediate accumulation type of the downstream collector
   * @param <R>           result type of the downstream collector
   * @return an immutable map entry containing the first-ranked key
   */
  public static <T, K, A, R> Collector<T, ?, Map.Entry<K, R>> create(
      Function<? super T, ? extends K> classifier, Collector<T, A, R> downstream, Comparator<K> keyComparator) {
    Function<A, R> finisher = downstream.finisher();
    return create(classifier, downstream, keyComparator,
        (FirstRankCollector<T, K, A, R> container) ->
            new AbstractMap.SimpleImmutableEntry<>(container.key, finisher.apply(container.resultContainer)));
  }

  private static <T, K, A, R, RR> Collector<T, ?, RR> create(
      Function<? super T, ? extends K> classifier, Collector<T, A, R> downstream,
      Comparator<K> keyComparator,
      Function<FirstRankCollector<T, K, A, R>, RR> finisher) {
    BinaryOperator<A> combiner = downstream.combiner();
    return Collector.of(
        () -> new FirstRankCollector<>(classifier, downstream, keyComparator),
        FirstRankCollector::collect,
        (left, right) -> {
          int comparisonResult = left.compareTo(right);
          if (comparisonResult > 0) {
            return right;
          }
          if (comparisonResult == 0) {
            left.resultContainer = combiner.apply(left.resultContainer, right.resultContainer);
          }
          return left;
        },
        finisher,
        downstream.characteristics().contains(Collector.Characteristics.UNORDERED)
            ? new Collector.Characteristics[]{Collector.Characteristics.UNORDERED}
            : new Collector.Characteristics[]{}
    );
  }

  private void collect(T element) {
    K potentialKey = classifier.apply(element);
    if (!collectionHasOccurred) {
      downstreamAccumulator.accept(resultContainer, element);
      collectionHasOccurred = true;
      key = potentialKey;
    } else {
      int comparisonResult = comparator.compare(key, potentialKey);
      if (comparisonResult == 0) {
        downstreamAccumulator.accept(resultContainer, element);
      } else if (comparisonResult > 0) {
        resultContainer = downstreamSupplier.get();
        downstreamAccumulator.accept(resultContainer, element);
        key = potentialKey;
      }
    }
  }

  @Override
  public int compareTo(FirstRankCollector<T, K, A, R> other) {
    if (!collectionHasOccurred) {
      return 1;
    }
    if (!other.collectionHasOccurred) {
      return -1;
    }
    return comparator.compare(key, other.key);
  }
}
