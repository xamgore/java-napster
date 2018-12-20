package client;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import static java.nio.file.Files.newOutputStream;

public class FileStats {
  public static final String STATS = ".stats";

  public final int fileId;
  public final BlocksSet loadedBlocks;
  public final Path absolutePathToOriginal;

  public FileStats(int fileId, BlocksSet loadedBlocks, Path absolutePath) {
    this.fileId = fileId;
    this.loadedBlocks = loadedBlocks;
    this.absolutePathToOriginal = absolutePath;
  }

  public BlocksSet notLoadedBlocks() {
    return loadedBlocks.invert();
  }


  public static FileStats load(Path blocksDirs, int fileId) throws IOException {
    Path index = getStatsIndexFile(blocksDirs, fileId);
    try (DataInputStream in = new DataInputStream(Files.newInputStream(index))) {
      Path absolutePath = Paths.get(in.readUTF());
      int totalNumberOfBlocks = in.readInt();
      int size = in.readInt();

      Set<Integer> blockIndexes = new HashSet<>();
      for (int i = 0; i < size; i++) {
        int part = in.readInt();
        blockIndexes.add(part);
      }

      return new FileStats(fileId, new BlocksSet(blockIndexes, totalNumberOfBlocks), absolutePath);
    }
  }

  public static FileStats load(Path blocksDir, File index) throws IOException {
    String beforeDot = index.getName().split("\\.")[0];
    int fileId = Integer.parseInt(beforeDot);
    return load(blocksDir, fileId);
  }

  public synchronized void dump(Path blocksDir) throws IOException {
    Path file = getStatsIndexFile(blocksDir, fileId);
    try (DataOutputStream out = new DataOutputStream(newOutputStream(file))) {
      out.writeUTF(absolutePathToOriginal.getFileName().toString());
      out.writeInt(loadedBlocks.totalNumber);
      out.writeInt(loadedBlocks.indexes.size());
      for (int idx : loadedBlocks.indexes) out.writeInt(idx);
    }
  }

  private static Path getStatsIndexFile(Path blocksDir, int fileId) {
    return blocksDir.resolve(fileId + STATS);
  }
}
