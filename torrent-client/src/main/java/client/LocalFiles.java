package client;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static client.FileStats.STATS;

public class LocalFiles {
  public static final int BLOCK_SIZE = 100;

  public final Path blocksDir;
  public final Path downloadsDir;
  private final Map<Integer, FileStats> stats = new ConcurrentHashMap<>();

  public LocalFiles(Path blocksDir, Path downloadsDir) throws IOException {
    this.blocksDir = blocksDir.toAbsolutePath();
    this.blocksDir.toFile().mkdirs();

    this.downloadsDir = blocksDir.toAbsolutePath();
    this.downloadsDir.toFile().mkdirs();

    File[] indexes = this.blocksDir.toFile().listFiles((dir, name) -> name.endsWith(STATS));
    if (indexes != null) {
      for (File s : indexes) {
        FileStats st = FileStats.load(blocksDir, s);
        stats.put(st.fileId, st);
      }
    }
  }

  // Check a file is allocated
  public boolean exists(int fileId) {
    return stats.containsKey(fileId);
  }

  public Collection<FileStats> getStats() {
    synchronized (stats) {
      return new ArrayList<>(stats.values());
    }
  }

  public void markAsReady(int fileId, int blockIdx) {
    stats.computeIfPresent(fileId, (key, stat) -> {
      try {
        stat.dump(blocksDir);
        stat.loadedBlocks.indexes.add(blockIdx);
      } catch (IOException e) {
        e.printStackTrace();
      }
      return stat;
    });
  }

  // The file will be downloaded in some time
  public void addEmpty(int fileId, long size, String name) throws IOException {
    Path absolutePath = downloadsDir.resolve(name).toAbsolutePath();
    createFileWithSizeAs(size, absolutePath);
    BlocksSet emptySetOfBlocks = BlocksSet.empty(countBlocks(absolutePath));
    addAndDump(new FileStats(fileId, emptySetOfBlocks, absolutePath));
  }

  // this file is already existing on disk
  public void addAsExisting(int fileId, Path absolutePath) throws IOException {
    BlocksSet setOfAllBlocks = BlocksSet.range(countBlocks(absolutePath));
    addAndDump(new FileStats(fileId, setOfAllBlocks, absolutePath));
  }

  private void addAndDump(FileStats fileStats) throws IOException {
    fileStats.dump(blocksDir);
    stats.put(fileStats.fileId, fileStats);
  }

  private static int countBlocks(Path absolutePath) throws IOException {
    return (int) Math.ceil(Files.size(absolutePath) / (1.0 * BLOCK_SIZE));
  }

  private static void createFileWithSizeAs(long size, Path absolutePath) throws IOException {
    try (RandomAccessFile file = new RandomAccessFile(absolutePath.toFile(), "rw")) {
      file.setLength(size);
    }
  }
}
