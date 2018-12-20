package tracker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static java.lang.Integer.parseUnsignedInt;
import static java.lang.Long.parseUnsignedLong;
import static java.lang.Math.max;
import static org.apache.commons.io.FileUtils.touch;
import static org.apache.commons.io.FileUtils.writeStringToFile;
import static tracker.Server.log;


public class Journal {
  private final Path path;
  private final AtomicInteger lastFileId;
  private final ConcurrentLinkedQueue<Record> journal;


  public Journal(Path path) throws IOException {
    log.debug("Touch a journal file");
    touch(path.toFile());

    this.path = path;
    this.journal = new ConcurrentLinkedQueue<>(loadRecordsFromFile(path));
    this.lastFileId = new AtomicInteger(journal.parallelStream().mapToInt(f -> f.id).max().orElse(-1));
  }


  public List<Record> getRecords() {
    return new ArrayList<>(journal);
  }

  public List<Integer> getIds() {
    return journal.stream().mapToInt(f -> f.id).boxed().collect(Collectors.toList());
  }

  public int add(long size, String name) throws IOException {
    Record info = new Record(lastFileId.incrementAndGet(), max(0, size), name);

    // simultaneous file-writes are prevented
    synchronized (path) { writeStringToFile(path.toFile(), info.serialize() + "\n", true); }

    journal.add(info);
    return info.id;
  }

  static private Collection<Record> loadRecordsFromFile(Path path) throws IOException {
    log.debug("Load journal records");
    return Files.readAllLines(path).stream().map(Record::deserialize).collect(Collectors.toList());
  }


  public static class Record {
    final int id;
    final long size;
    final String name;

    Record(int id, long size, String name) {
      this.id = id;
      this.size = size;
      this.name = name;
    }

    static Record deserialize(String raw) {
      String[] values = raw.split("/", 3);
      return new Record(parseUnsignedInt(values[0]), parseUnsignedLong(values[1]), values[2]);
    }

    static String serialize(int id, long size, String name) {
      return String.format("%d/%d/%s", id, size, name);
    }

    String serialize() {
      return serialize(id, size, name);
    }
  }
}
