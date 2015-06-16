package com.davidleston.stream;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * Collects only elements which tie for the minimum or maximum element.
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
 */
public enum FirstRankCollector {
  /**
   * Collects elements that tie for the minimum element.
   */
  multiMin() {
    protected final <T> Comparator<T> internalCompareBy(Comparator<T> comparator) {
      return comparator;
    }
  },
  /**
   * Collects elements that tie for the maximum element.
   */
  multiMax() {
    protected final <T> Comparator<T> internalCompareBy(Comparator<T> comparator) {
      return comparator.reversed();
    }
  };

  protected abstract <T> Comparator<T> internalCompareBy(Comparator<T> comparator);

  /**
   * @param <T> the type of input elements to the reduction operation
   * @return Collector orders by natural order, reduces into a List
   */
  public final <T extends Comparable<T>> Collector<T, ?, List<T>> byNaturalOrder() {
    return compareByThenCollectInto(Comparator.<T>naturalOrder(), Collectors.<T>toList());
  }

  /**
   * @param downstream a {@code Collector} implementing the downstream reduction
   * @param <T>        the type of input elements
   * @param <R>        result type of the downstream collector
   * @return Collector orders by natural order, reduces into a List
   */
  public final <T extends Comparable<T>, R> Collector<T, ?, R> collectInto(Collector<T, ?, R> downstream) {
    return compareByThenCollectInto(Comparator.<T>naturalOrder(), downstream);
  }

  /**
   * @param comparator order by this {@link Comparator}
   * @param <T>        the type of input elements
   * @return Collector reduces into a List
   */
  public final <T> Collector<T, ?, List<T>> compareBy(Comparator<T> comparator) {
    return compareByThenCollectInto(comparator, Collectors.<T>toList());
  }

  /**
   * @param comparator order by this {@link Comparator}
   * @param downstream a {@code Collector} implementing the downstream reduction
   * @param <T>        the type of input elements
   * @param <A>        the intermediate accumulation type of the downstream collector
   * @param <R>        result type of the downstream collector
   * @return Collector collects into downstream collector and reduces into what the downstream collector reduces into
   */
  public final <T, A, R> Collector<T, ?, R> compareByThenCollectInto(Comparator<T> comparator, Collector<T, A, R> downstream) {
    BinaryOperator<A> combiner = downstream.combiner();
    Function<A, R> finisher = downstream.finisher();
    return Collector.<T, Accumulator<T, A, R>, R>of(
        () -> new Accumulator<>(downstream, internalCompareBy(comparator)),
        Accumulator::collect,
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
        container -> finisher.apply(container.resultContainer),
        downstream.characteristics().contains(Collector.Characteristics.UNORDERED)
            ? new Collector.Characteristics[]{Collector.Characteristics.UNORDERED}
            : new Collector.Characteristics[]{}
    );
  }

  private static final class Accumulator<T, A, R> implements Comparable<Accumulator<T, A, R>> {
    private final Comparator<T> comparator;
    private final Supplier<A> downstreamSupplier;
    private final BiConsumer<A, T> downstreamAccumulator;
    private A resultContainer;
    private boolean collectionHasOccurred = false;
    private T containedElement;

    private Accumulator(Collector<T, A, R> downstream, Comparator<T> comparator) {
      this.comparator = comparator;
      downstreamSupplier = downstream.supplier();
      downstreamAccumulator = downstream.accumulator();
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
    public int compareTo(Accumulator<T, A, R> other) {
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
