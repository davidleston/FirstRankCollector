package com.davidleston.stream;

import org.junit.Test;

import java.util.*;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

public class FirstRankCollectorTest {
  @Test
  public void emptyStreamResultsInEmptyCollection() {
    List<String> elements = Stream.<String>of().collect(FirstRankCollector.create());
    assertThat(elements).isEmpty();
  }

  @Test
  public void streamOfOneResultsInCollectionOfOne() {
    List<String> elements = Stream.of("a").collect(FirstRankCollector.create());
    assertThat(elements).containsExactly("a");
  }

  @Test
  public void streamOfTwoNonEqualElementsInRankOrderResultsInCollectionOfFirstRankedElement() {
    List<String> elements = Stream.of("a", "b").collect(FirstRankCollector.create());
    assertThat(elements).containsExactly("a");
  }

  @Test
  public void streamOfTwoNonEqualElementsInReverseRankOrderResultsInCollectionOfFirstRankedElement() {
    List<String> elements = Stream.of("b", "a").collect(FirstRankCollector.create());
    assertThat(elements).containsExactly("a");
  }

  @Test(expected = NullPointerException.class)
  public void defaultComparatorForStringsDoesNotSupportNull() {
    List<String> elements = Stream.of(null, "a").collect(FirstRankCollector.create());
    assertThat(elements).containsExactly("a");
  }

  @Test
  public void nullFirstComparatorAndNullFriendlyCollection() {
    List<String> elements = Stream.of(null, "a").collect(FirstRankCollector
        .create(Comparator.nullsFirst(Comparator.<String>naturalOrder())));
    assertThat(elements).containsExactly(new String[]{null});
  }

  @Test
  public void nullLastComparatorAndNullFriendlyCollection() {
    List<String> elements = Stream.of(null, "a").collect(FirstRankCollector
        .create(Comparator.nullsLast(Comparator.<String>naturalOrder())));
    assertThat(elements).containsExactly("a");
  }

  @Test
  public void duplicatesAreKeptByDefault() {
    List<String> elements = Stream.of("a", "a").collect(FirstRankCollector.create());
    assertThat(elements).hasSize(2);
  }

  @Test
  public void setCollectorResultsInDeduping() {
    Set<String> elements = Stream.of("a", "a")
        .collect(FirstRankCollector.create(Collectors.<String>toSet()));
    assertThat(elements).hasSize(1);
  }

  @Test
  public void customCollectorAndCustomComparator() {
    Set<String> elements = Stream.of("a", "b")
        .collect(FirstRankCollector.create(Collectors.toSet(), Collections.reverseOrder()));
    assertThat(elements).containsExactly("b");
  }

  @Test
  public void countingCollectorThrowsAwayContainer() {
    long count = Stream.of(3, 2, 2) // 3 being first causes a collector to be thrown away
        .collect(FirstRankCollector.create(Collectors.<Integer>counting()));
    assertThat(count).isEqualTo(2);
  }

  @Test
  public void orderedDownstreamCollectorCharacteristics() {
    Collector<String, ?, List<String>> collector = FirstRankCollector.create();
    assertThat(collector.characteristics()).isEmpty();
  }

  @Test
  public void unorderedDownstreamCollectorCharacteristics() {
    Collector<String, ?, Set<String>> collector = FirstRankCollector.create(Collectors.<String>toSet());
    assertThat(collector.characteristics()).containsOnly(Collector.Characteristics.UNORDERED);
  }

  @Test
  public void maintainsOrder() {
    List<Character> elements = Stream.of('A', 'b', 'B', 'a')
        .collect(FirstRankCollector.create(Comparator.comparing(Character::isUpperCase)));
    assertThat(elements).containsExactly('b', 'a');
  }

  @Test public void keyed() {
    Map.Entry<Integer, List<String>> map = Stream.of("a", "aa", "b", "bb", "a")
        .collect(FirstRankCollector.create(String::length));
    assertThat(map.getKey()).isEqualTo(1);
    assertThat(map.getValue()).containsExactly("a", "b", "a");
  }

  @Test public void keyComparator() {
    Map.Entry<Integer, List<String>> map = Stream.of("a", "aa", "b", "bb")
        .collect(FirstRankCollector.create(String::length, Comparator.reverseOrder()));
    assertThat(map.getKey()).isEqualTo(2);
    assertThat(map.getValue()).containsExactly("aa", "bb");
  }

  @Test public void keyedWithDownstreamCollector() {
    Map.Entry<Integer, Set<String>> map = Stream.of("a", "aa", "a")
        .collect(FirstRankCollector.create(String::length, Collectors.<String>toSet()));
    assertThat(map.getKey()).isEqualTo(1);
    assertThat(map.getValue()).containsExactly("a");
  }

  @Test public void keyedComparatorWithDownstreamCollector() {
    Map.Entry<Integer, Set<String>> map = Stream.of("aa", "a", "aa")
        .collect(FirstRankCollector.create(String::length, Collectors.<String>toSet(), Comparator.reverseOrder()));
    assertThat(map.getKey()).isEqualTo(2);
    assertThat(map.getValue()).containsExactly("aa");
  }
}