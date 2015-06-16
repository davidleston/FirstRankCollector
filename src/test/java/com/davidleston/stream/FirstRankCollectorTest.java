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
    List<String> elements = Stream.<String>of()
        .collect(FirstRankCollector.multiMin.byNaturalOrder());
    assertThat(elements).isEmpty();
  }

  @Test
  public void streamOfOneResultsInCollectionOfOne() {
    List<String> elements = Stream.of("a")
        .collect(FirstRankCollector.multiMin.byNaturalOrder());
    assertThat(elements).containsExactly("a");
  }

  @Test
  public void streamOfTwoNonEqualElementsInRankOrderResultsInCollectionOfFirstRankedElement() {
    List<String> elements = Stream.of("a", "b")
        .collect(FirstRankCollector.multiMin.byNaturalOrder());
    assertThat(elements).containsExactly("a");
  }

  @Test
  public void multiMax() {
    List<String> elements = Stream.of("a", "b")
        .collect(FirstRankCollector.multiMax.byNaturalOrder());
    assertThat(elements).containsExactly("b");
  }

  @Test
  public void streamOfTwoNonEqualElementsInReverseRankOrderResultsInCollectionOfFirstRankedElement() {
    List<String> elements = Stream.of("b", "a")
        .collect(FirstRankCollector.multiMin.byNaturalOrder());
    assertThat(elements).containsExactly("a");
  }

  @Test(expected = NullPointerException.class)
  public void defaultComparatorForStringsDoesNotSupportNull() {
    List<String> elements = Stream.of(null, "a")
        .collect(FirstRankCollector.multiMin.byNaturalOrder());
    assertThat(elements).containsExactly("a");
  }

  @Test
  public void nullFirstComparatorAndNullFriendlyCollection() {
    List<String> elements = Stream.of(null, "a")
        .collect(FirstRankCollector.multiMin.compareBy(Comparator.nullsFirst(Comparator.<String>naturalOrder())));
    assertThat(elements).containsExactly(new String[]{null});
  }

  @Test
  public void nullLastComparatorAndNullFriendlyCollection() {
    List<String> elements = Stream.of(null, "a")
        .collect(FirstRankCollector.multiMin.compareBy(Comparator.nullsLast(Comparator.<String>naturalOrder())));
    assertThat(elements).containsExactly("a");
  }

  @Test
  public void duplicatesAreKeptByDefault() {
    List<String> elements = Stream.of("a", "a")
        .collect(FirstRankCollector.multiMin.byNaturalOrder());
    assertThat(elements).hasSize(2);
  }

  @Test
  public void setCollectorResultsInDeduping() {
    Set<String> elements = Stream.of("a", "a")
        .collect(FirstRankCollector.multiMin.collectInto(Collectors.<String>toSet()));
    assertThat(elements).hasSize(1);
  }

  @Test
  public void customCollectorAndCustomComparator() {
    Set<String> elements = Stream.of("a", "b")
        .collect(FirstRankCollector.multiMin.compareByThenCollectInto(Collections.reverseOrder(), Collectors.toSet()));
    assertThat(elements).containsExactly("b");
  }

  @Test
  public void countingCollectorThrowsAwayContainer() {
    long count = Stream.of(3, 2, 2) // 3 being first causes a collector to be thrown away
        .collect(FirstRankCollector.multiMin.collectInto(Collectors.<Integer>counting()));
    assertThat(count).isEqualTo(2);
  }

  @Test
  public void orderedDownstreamCollectorCharacteristics() {
    Collector<String, ?, List<String>> collector = FirstRankCollector.multiMin.byNaturalOrder();
    assertThat(collector.characteristics()).isEmpty();
  }

  @Test
  public void unorderedDownstreamCollectorCharacteristics() {
    Collector<String, ?, Set<String>> collector = FirstRankCollector.multiMin.collectInto(Collectors.<String>toSet());
    assertThat(collector.characteristics()).containsOnly(Collector.Characteristics.UNORDERED);
  }

  @Test
  public void maintainsOrder() {
    List<Character> elements = Stream.of('A', 'b', 'B', 'a')
        .collect(FirstRankCollector.multiMin.compareBy(Comparator.comparing(Character::isUpperCase)));
    assertThat(elements).containsExactly('b', 'a');
  }
}