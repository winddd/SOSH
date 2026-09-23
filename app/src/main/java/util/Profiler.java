package util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Iterables;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import util.io.DumpResult;

public class Profiler {
  // global vars
  private static Map<Long, Profiler> profilers = new ConcurrentHashMap<>();

  // local vars
  private Map<String, Long> start_time = new HashMap<>();
  private Map<String, Long> total_time = new HashMap<>();
  private Map<String, Integer> counter = new HashMap<>();
  @Getter
  private Map<String, Double> TAG2RUNTIME = new HashMap<>();

  // efficient thread-safe multi-singleton
  public static Profiler getInstance() {
    // each thread has an unique profiler
    long tid = Thread.currentThread().getId();
    if (!profilers.containsKey(tid)) {
      synchronized (Profiler.class) {
        if (!profilers.containsKey(tid)) {
          profilers.put(tid, new Profiler());
        }
      }
    }
    return profilers.get(tid);
  }

  public static long getAggTime(String tag) {
    long time = 0;
    for (Profiler p : profilers.values()) {
      time += p.getTime(tag);
    }
    return time;
  }

  public static int getAggCount(String tag) {
    int count = 0;
    for (Profiler p : profilers.values()) {
      count += p.getCounter(tag);
    }
    return count;
  }

  public String[] getTags() {
//    Set<String> tags = new HashSet<String>();
//    for (Profiler p : profilers.values()) {
//      tags.addAll(p.counter.keySet());
//    }
    String[] res = Iterables.toArray(this.counter.keySet(), String.class);
    return res;
  }

  public long startTick(String tag) {
    if (!counter.containsKey(tag)) {
      counter.put(tag, 0);
      total_time.put(tag, 0L);
    }

    // if we haven't stop this tick, stop it!!!
    if (start_time.containsKey(tag)) {
      endTick(tag);
    }

    // start the tick!
    long cur_time = System.nanoTime();
    start_time.put(tag, cur_time);
    return cur_time;
  }

  public long endTick(String tag) {
    long cur_time = System.nanoTime();
    if (start_time.containsKey(tag)) {
      long duration = cur_time - start_time.get(tag);

      // update the counter and total_time
      total_time.put(tag, (total_time.get(tag) + duration));
      counter.put(tag, (counter.get(tag) + 1));

      // rm the tick
      start_time.remove(tag);
      return cur_time;
    } else {
      System.out.println("profiler error: Trying to end a nonexiting tag");
      Thread.dumpStack();
      System.exit(-1);
      return -1;
    }
  }

  public void tryEndTick(String tag) {
    long cur_time = System.nanoTime();
    if (start_time.containsKey(tag)) {
      long duration = cur_time - start_time.get(tag);

      // update the counter and total_time
      total_time.put(tag, (total_time.get(tag) + duration));
      counter.put(tag, (counter.get(tag) + 1));

      // rm the tick
      start_time.remove(tag);
    }
  }

  public long getTime(String tag) {
    if (total_time.containsKey(tag)) {
      return total_time.get(tag);
    } else {
      return 0;
    }
  }

  public int getCounter(String tag) {
    if (counter.containsKey(tag)) {
      return counter.get(tag);
    } else {
      return 0;
    }
  }

  public void start() {
    startTick("e2e");
  }

  public void endAll() {
    String[] tags = getTags();
    for (String tag : tags) {
      if (!tag.equals("e2e")) {
        tryEndTick(tag);
      }
    }

    tryEndTick("e2e");
  }

  /**
   * this should be called after calling `endAll`.
   */
  public void printProfilingResults(String perfFile, String expName, boolean sat) {
    if (perfFile == null || expName == null)
      return;
    StringBuilder builder = new StringBuilder();
    builder.append("Profiling results: ");

    String json = getRuntimeStatistics().replace("\n", "");
    json = json.replaceFirst("\\{", String.format("\\{\"sat\": %b, ", sat));
    json = String.format("{\"%s\": %s}", expName, json);
    builder.append(json);
    System.out.println(builder);
    System.out.println("Writing profiling results to " + perfFile);
    json = json.replace("\n", "") + "\n";
    DumpResult.writeToFile(json, perfFile, true);
  }


  public void recordResults() {
    String[] tags = getTags();

    for (String tag : tags) {
      double time =
          Double.parseDouble(new DecimalFormat("##.##").format(getTime(tag) / 1000000000.0));
      putTagRuntime(tag, time);
    }
  }

  public void putTagRuntime(String tag, double time) {
    synchronized (TAG2RUNTIME) {
      TAG2RUNTIME.put(tag, time);
    }
  }

  /**
   * Merges profiling data from all thread-local profilers into this profiler.
   *
   * <p>This is useful when solver threads timeout and don't return their profiling data
   * via SearchResult. By merging all thread profilers, we capture data from the main thread
   * (parsing, ASG, compilation, optimization) even if solver threads are still running.
   *
   * <p>This method aggregates timing data from all threads by:
   * <ul>
   *   <li>Iterating over all thread-local profiler instances</li>
   *   <li>Calling endAll() and recordResults() on each</li>
   *   <li>Merging their TAG2RUNTIME maps into this profiler's map</li>
   * </ul>
   *
   * <p><b>Thread Safety:</b> This method is synchronized on the profilers map.
   */
  public void mergeAllThreadProfilers() {
    synchronized (profilers) {
      for (Profiler p : profilers.values()) {
        // Finalize profiling for each thread
        p.endAll();
        p.recordResults();

        // Merge timing data into current profiler
        synchronized (p.TAG2RUNTIME) {
          for (Map.Entry<String, Double> entry : p.TAG2RUNTIME.entrySet()) {
            putTagRuntime(entry.getKey(), entry.getValue());
          }
        }
      }
    }
  }

  public String getRuntimeStatistics() {
//    var cfg = Config.get();
    synchronized (this.TAG2RUNTIME) {
      ObjectMapper om = new ObjectMapper();
      String jacksonData = null;
      try {
        jacksonData = om.writeValueAsString(this.TAG2RUNTIME);
      } catch (JsonProcessingException e) {
        throw new RuntimeException(e);
      }

      return jacksonData;
    }
  }

//    public static void main(String[] args) {
//        Profiler.getInstance().startTick("outer");
//        int a = 0;
//        for (int i = 0; i < 10000000; i++) {
//            Profiler.getInstance().startTick("inner");
//            a++;
//            Profiler.getInstance().endTick("inner");
//        }
//        Profiler.getInstance().endTick("outer");
//        System.out.println(a);
//        System.out.println("outer: " + Profiler.getAggTime("outer"));
//        System.out.println("inner: " + Profiler.getAggTime("inner"));
//    }
}
