package com.davidleston.stream;

import org.junit.Test;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

public class FirstRankCollectorTest {
  @Test
  public void emptyStreamResultsInEmptyCollection() {
    List<String> elements = Stream.<String>of().collect(FirstRankCollector.<String>create());
    assertThat(elements).isEmpty();
  }

  @Test
  public void streamOfOneResultsInCollectionOfOne() {
    List<String> elements = Stream.of("a").collect(FirstRankCollector.<String>create());
    assertThat(elements).containsExactly("a");
  }

  @Test
  public void streamOfTwoNonEqualElementsInRankOrderResultsInCollectionOfFirstRankedElement() {
    List<String> elements = Stream.of("a", "b").collect(FirstRankCollector.<String>create());
    assertThat(elements).containsExactly("a");
  }

  @Test
  public void streamOfTwoNonEqualElementsInReverseRankOrderResultsInCollectionOfFirstRankedElement() {
    List<String> elements = Stream.of("b", "a").collect(FirstRankCollector.<String>create());
    assertThat(elements).containsExactly("a");
  }

  @Test(expected = NullPointerException.class)
  public void defaultComparatorForStringsDoesNotSupportNull() {
    List<String> elements = Stream.of(null, "a").collect(FirstRankCollector.<String>create());
    assertThat(elements).containsExactly("a");
  }

  @Test
  public void nullFirstComparatorAndNullFriendlyCollection() {
    List<String> elements = Stream.of(null, "a").collect(FirstRankCollector
        .<String>create(Comparator.nullsFirst(Comparator.<String>naturalOrder())));
    assertThat(elements).containsExactly(new String[]{null});
  }

  @Test
  public void nullLastComparatorAndNullFriendlyCollection() {
    List<String> elements = Stream.of(null, "a").collect(FirstRankCollector
        .<String>create(Comparator.nullsLast(Comparator.<String>naturalOrder())));
    assertThat(elements).containsExactly("a");
  }

  @Test
  public void duplicatesAreKeptByDefault() {
    List<String> elements = Stream.of("a", "a").collect(FirstRankCollector.<String>create());
    assertThat(elements).hasSize(2);
  }

  @Test
  public void setCollectorResultsInDeduping() {
    Set<String> elements = Stream.of("a", "a")
        .collect(FirstRankCollector.<String, Set<String>>create(Collectors.toSet()));
    assertThat(elements).hasSize(1);
  }

  @Test
  public void customCollectorAndCustomComparator() {
    Set<String> elements = Stream.of("a", "b")
        .collect(FirstRankCollector.<String, Set<String>>create(Collectors.toSet(), Collections.reverseOrder()));
    assertThat(elements).containsExactly("b");
  }

  @Test
  public void countingCollectorThrowsAwayContainer() {
    long count = Stream.of(3, 2, 2) // 3 being first causes a collector to be thrown away
        .collect(FirstRankCollector.<Integer, Long>create(Collectors.counting()));
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
    List<Character> characters = Stream.of('A', 'b', 'B', 'a')
        .collect(FirstRankCollector.create(Comparator.comparing(Character::isUpperCase)));
    assertThat(characters).containsExactly('b', 'a');
  }
}