package com.davidleston.stream;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;

/**
 * Demonstrates FirstRankedCollector outperforms alternative solutions.
 */
public class FirstRankedCollectorPerformanceTest {
  private static final int populationSize = 1_000;
  private static final int runs = 100_000; // Helps ensure the JIT kicks in
  private static final int approximateNumberOfFirstRanked = 10;
  private static final int uniqueValues = populationSize / (populationSize / approximateNumberOfFirstRanked);
  private static final int dotsPerMethod = 10;
  private static final int showDotForEvery = runs / dotsPerMethod;
  private static List<String> population;
  private static long fastTime;
  private static int expectedCount;

  @Rule
  public TestName name = new TestName();

  @BeforeClass
  public static void timeFastMethod() {
    Random random = new Random(populationSize);
    String[] strings = new String[populationSize];
    Arrays.setAll(strings, index -> String.valueOf(random.nextInt(uniqueValues)));
    population = Arrays.asList(strings);
    String min = Collections.min(population);
    expectedCount = Collections.frequency(population, min);

    System.out.println();
    System.out.print("fast method: ");
    fastTime = testMethod((population) -> population.stream()
        .collect(FirstRankCollector.create()));

    System.out.format("%nFinding %s first-ranked of %s strings. Executing %s runs of each method. ",
        expectedCount, populationSize, runs);
    System.out.println("The following are the durations (nanoseconds):");
    System.out.print("Best time for FirstRankedCollector: ");
    System.out.println(fastTime);
  }

  @Test
  public void grouping() {
    compareMethods((population) -> population.stream()
            .collect(Collectors.collectingAndThen(
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
    );
  }

  @Test
  public void minThenFiltering() {
    compareMethods(population -> {
      String minValue = Collections.min(population);
      return population.stream()
          .filter(minValue::equals)
          .collect(Collectors.toList());
    });
  }

  private static long testMethod(Function<List<String>, List<String>> method) {
    return IntStream.range(0, runs)
        .parallel()
        .mapToLong(index -> {
          if (index % showDotForEvery == 0) {
            System.out.print('.');
          }
          long start = System.nanoTime();
          List<String> results = method.apply(population);
          long end = System.nanoTime();
          int elementsFound = results.size();
          assertThat(elementsFound).isEqualTo(expectedCount)
              .as("Expected both methods to find the same number of first-ranked elements.");
          return end - start;
        })
        .min()
        .getAsLong();
  }

  private void compareMethods(Function<List<String>, List<String>> slowMethod) {
    System.out.println();
    System.out.format("%s: ", name.getMethodName());
    long slowTime = testMethod(slowMethod);

    System.out.println();
    System.out.format("Best time for %s: %s%n", name.getMethodName(), slowTime);
    System.out.format("FirstRankedCollector %s%% faster!%n", (int) ((((double) slowTime / (double) fastTime) - 1) * 100));
    assertThat(slowTime).isGreaterThan(fastTime)
        .as("Expect slow method to take longer than fast method.");
  }
}
