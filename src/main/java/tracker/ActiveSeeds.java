package tracker;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Collections.synchronizedSet;
import static tracker.Server.log;

@SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
class ActiveSeeds {
  private final Map<Integer, Set<Seed>> seedsOfFile = new ConcurrentHashMap<>();
  private final int minutes;

  public ActiveSeeds(int nLastMinutes, Collection<Integer> fileIds) {
    fileIds.forEach(id -> seedsOfFile.put(id, emptySet()));
    this.minutes = nLastMinutes;
  }

  public void prepare(int fileId) {
    seedsOfFile.computeIfAbsent(fileId, id -> emptySet());
  }

  /**
   * Add seed to a number of active one.
   * If the <tt>fileId</tt> was not "prepared" before, nothing will be done.
   * Call <tt>prepare</tt> method.
   */
  public void addIfPrepared(int fileId, Seed seed) {
    seedsOfFile.computeIfPresent(fileId, (id, set) -> {
      set.add(seed);
      return set;
    });
  }

  /**
   * Returns seeds sharing file.
   * If a seed was not active too long (5 minutes), skip it
   */
  public List<Seed> of(int fileId) {
    Set<Seed> seeds = seedsOfFile.getOrDefault(fileId, emptySet());
    List<Seed> result = new ArrayList<>();

    // do sync on reads of synchronized sets
    synchronized (seeds) {
      Iterator<Seed> iterator = seeds.iterator();

      while (iterator.hasNext()) {
        Seed candidate = iterator.next();

        if (candidate.wasActiveLast(minutes)) {
          result.add(candidate);
        } else {
          iterator.remove();
        }
      }
    }

    return result;
  }

  private static Set<Seed> emptySet() {
    return synchronizedSet(new HashSet<>());
  }
}
