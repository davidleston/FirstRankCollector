package com.davidleston.stream;

import org.junit.Before;
import org.junit.Test;

import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

import static org.assertj.core.api.Assertions.*;

public class FirstRankCollectorConcurrentTest {

  private Helper<?> collector;

  @Before
  public void beforeEachTest() {
    collector = Helper.create(FirstRankCollector.create());
  }

  @Test
  public void leftCollectedNothing_rightCollectedNothing() {
    assertThat(collector.combineAndFinish()).isEmpty();
  }

  @Test
  public void leftCollectedSomething_rightCollectedNothing() {
    collector.left("a");
    List<String> elements = collector.combineAndFinish();
    assertThat(elements).containsExactly("a");
  }

  @Test
  public void leftCollectedNothing_rightCollectedSomething() {
    collector.right("a");
    List<String> elements = collector.combineAndFinish();
    assertThat(elements).containsExactly("a");
  }

  @Test
  public void leftCollectedA_rightCollectedA() {
    collector.left("a");
    collector.right("a");
    List<String> elements = collector.combineAndFinish();
    assertThat(elements).containsExactly("a", "a");
  }

  @Test
  public void leftCollectedA_rightCollectedB() {
    collector.left("a");
    collector.right("b");
    List<String> elements = collector.combineAndFinish();
    assertThat(elements).containsExactly("a");
  }

  @Test
  public void leftCollectedB_rightCollectedA() {
    collector.left("b");
    collector.right("a");
    List<String> elements = collector.combineAndFinish();
    assertThat(elements).containsExactly("a");
  }

  @Test
  public void maintainsOrder() {
    collector = Helper.create(FirstRankCollector.create(Comparator.comparing(element -> true)));
    collector.left("d");
    collector.left("c");
    collector.right("b");
    collector.right("a");
    List<String> elements = collector.combineAndFinish();
    assertThat(elements).containsExactly("d", "c", "b", "a");
  }

  private static final class Helper<A> {
    private final BiConsumer<A, String> accumulator;
    private final A left;
    private final A right;
    private final BinaryOperator<A> combiner;
    private final Function<A, List<String>> finisher;

    private Helper(Collector<String, A, List<String>> collector) {
      Supplier<A> supplier = collector.supplier();
      accumulator = collector.accumulator();
      left = supplier.get();
      right = supplier.get();
      combiner = collector.combiner();
      finisher = collector.finisher();
    }

    private static <A> Helper<A> create(Collector<String, A, List<String>> collector) {
      return new Helper<>(collector);
    }

    private void left(String element) {
      accumulator.accept(left, element);
    }

    private void right(String element) {
      accumulator.accept(right, element);
    }

    private List<String> combineAndFinish() {
      A container = combiner.apply(left, right);
      return finisher.apply(container);
    }
  }
}
