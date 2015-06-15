FirstRankCollector
==================

[![Build Status](https://travis-ci.org/davidleston/FirstRankCollector.svg?branch=master)](https://travis-ci.org/davidleston/FirstRankCollector)
[![Coverage Status](https://coveralls.io/repos/davidleston/FirstRankCollector/badge.svg?branch=master)](https://coveralls.io/r/davidleston/FirstRankCollector?branch=master)
![license: MIT](https://img.shields.io/badge/license-MIT-blue.svg)

An implementation of Java 8's java.util.stream.Collector that collects only elements which sort first.
The stream being collected does not need to already be sorted.
For example, in an array of {"b", "a", "a"}, both "a" strings will be collected, but not "b".

Supports null values. Maintains stream encounter order.

For instructions on how to include this library using Maven or Gradle
visit https://jitpack.io/#davidleston/FirstRankCollector/v1.1.0

## Example Usage

### Default to collecting into a list and using natural ordering 
    List<T> firstRankedElements = stream.collect(FirstRankCollector.create())

### Specify downstream collector and comparator
    List<T> firstRankedElements
      = stream.collect(FirstRankCollector.create(downstreamCollector, comparator))

### Convert elements to keys, rank based on key
    Map<K, List<T>> firstRankedKeyAndElements
      = stream.collect(FirstRankCollector.create(classifier));

## Example Use Case

Given a collection of payments, find the payments that are for the highest amount.
The collection of payments are sorted by payment number.
Have the payments of the highest amount maintain sort order.

A relatively fast method to do this would be:

    maxPaymentAmount = Collections
      .max(payments, Comparator.comparing(payment -> payment.amount))
      .amount;
    paymentsOfTheHighestAmount = payments.stream()
      .filter(payment -> payment.amount == maxPaymentAmount)
      .collect(Collectors.toList());

This method requires two iterations through the collection.
Using a FirstRankCollector will often be faster as it only requires one iteration through the collection:

    paymentsOfTheHighestAmount = payments.stream()
      .collect(FirstRankCollector.create(
        Comparator.reverseOrder(Comparator.comparing(payment -> payment.amount))));  

## Performance

Given a stream of n elements with m first-ranked elements:
<table>
  <tr>
    <th></th>
    <th>comparisons</th>
    <th>downstream accumulations</th>
    <th>downstream accumulators instantiated</th>
    <th>iterations</th>
  </tr>
 <tr>
   <th>best case</th>
   <td>n</td>
   <td>m</td>
   <td>1</td>
   <td>1</td>
 </tr>
 <tr>
   <th>worst case</th>
   <td>n</td>
   <td>n</td>
   <td>n - m + 1</td>
   <td>1</td>
 </tr>
</table>

Best case is when all first-ranked elements are at the beginning of the stream.
Worst case is when the stream is in reverse order and none of the elements that are not first-ranked are equal.

FirstRankCollector will often be faster than these alternatives.

### Slower Alternative: Group by value, sort keys, select group by first-ranked key
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

### Slower Alternative: Find min value, filter other values
    T min = Collections.min(collection);
    collection.stream()
      .filter(minValue::equals)
      .collect(Collectors.toList());
