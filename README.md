FirstRankCollector
==================

[![Build Status](https://travis-ci.org/davidleston/FirstRankCollector.svg?branch=master)](https://travis-ci.org/davidleston/FirstRankCollector)

An implementation of Java 8's java.util.stream.Collector that collects only elements which sort first.
The stream being collected does not need to already be sorted.
For example, in an array of {"b", "a", "a"}, both "a" strings will be collected, but not "b".

Given a stream of n items with m first-ranked items, n comparisons
and m to n invocations of the downstream collector are made.

Supports null values.

## Example Usage

### Default to collecting into a list and using natural ordering 
    stream.collect(FirstRankCollector.create())

### Specify downstream collector and comparator
    stream.collect(FirstRankCollector.create(downstreamCollector, comparator))

## Performance

FirstRankCollector will often be faster than these alternatives.

### Slower Alternative: Grouping by key, sorting keys, selecting group by first-ranked key
    stream.collect(Collectors.collectingAndThen(
      Collectors.groupingBy(Function.<String>identity()),
      map -> map.get(
        map.keySet()
          .stream()
          .sorted()
          .findFirst()
          .get()
        )
      )
    )

### Slower Alternative: Finding min value, filter other values
    T min = Collections.min(collection);
    collection.stream()
      .filter(minValue::equals)
      .collect(Collectors.toList());
