package client;

import client.Operations.Get;
import client.Operations.Sources;
import client.Operations.Sources.Response.Seed;
import client.Operations.Stat;
import client.Operations.Stat.Response;
import org.apache.commons.io.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static client.LocalFiles.BLOCK_SIZE;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.util.concurrent.Executors.newFixedThreadPool;
import static java.util.concurrent.Executors.newScheduledThreadPool;

public class DownloadManager implements Closeable {
  private final ScheduledExecutorService timer = newScheduledThreadPool(1);
  private final ExecutorService pool = newFixedThreadPool(4);
  private final Set<Task> inProgress = concurrentHashSet();
  private final LocalFiles localFiles;
  private final String serverIp;
  private final short serverPort;

  public DownloadManager(LocalFiles localFiles, String serverIp, short serverPort) {
    this.localFiles = localFiles;
    this.serverPort = serverPort;
    this.serverIp = serverIp;
    timer.scheduleAtFixedRate(this::onTimer, 0, 500, TimeUnit.MILLISECONDS);
  }

  @Override
  public void close() {
    pool.shutdown();
    timer.shutdown();
  }

  class Task {
    int fileId;
    int blockId;

    public Task(int fileId, int blockId) {
      this.fileId = fileId;
      this.blockId = blockId;
    }
  }

  private void onTimer() {
    try {
      Set<Task> tasks = new HashSet<>();
      Set<Integer> ids = new HashSet<>();

      // load in 4 threads, don't put too much
      // tasks they may become "outdated"
      int tasksToAdd = 4 - inProgress.size();

      // take more tasks, if some threads are not busy
      outer:
      for (FileStats stats : localFiles.getStats()) {
        for (int blockIdx : stats.notLoadedBlocks()) {
          if (tasksToAdd <= 0) break outer;
          Task t = new Task(stats.fileId, blockIdx);
          if (inProgress.contains(t)) continue;
          tasksToAdd -= 1;
          tasks.add(t);
          ids.add(t.fileId);
        }
      }

      // Collect list of fresh seeds for each fileId
      Map<Integer, List<Seed>> seeds = new HashMap<>();
      for (Integer fileId : ids) {
        seeds.put(fileId, Sources.makeRequest(serverIp, serverPort, fileId).seeds);
      }

      tasks.removeIf(task -> seeds.get(task.fileId).isEmpty());
      tasks.forEach(task -> pool.submit(new BlockLoader(task, seeds.get(task.fileId))));
    } catch (IOException e) {
    }
  }

  private class BlockLoader implements Runnable {
    private final List<Seed> seeds;
    private final Task task;

    private BlockLoader(Task task, List<Seed> seeds) {
      this.task = task;
      this.seeds = seeds;
      inProgress.add(task);
    }

    @Override
    public void run() {
      try {
        Seed seed = null;

        // search the first seed with needed block
        for (Seed candidate : seeds) {
          try {
            Response response = Stat.makeRequest(candidate.ip.getHostAddress(), candidate.port, task.fileId);
            if (response.blocksIndexes.contains(task.blockId))
              seed = candidate;
          } catch (IOException ignored) {}
        }

        if (seed == null) return;

        try {
          // get data, write it to our block
          Get.Response res = Get.makeRequest(seed.ip.getHostAddress(), seed.port, task.fileId, task.blockId, BLOCK_SIZE);
          Path path = FileStats.load(localFiles.blocksDir, task.fileId).absolutePathToOriginal;
          FileChannel channel = FileChannel.open(path, WRITE).position(task.blockId * BLOCK_SIZE);
          try (OutputStream out = Channels.newOutputStream(channel);
               ByteArrayInputStream in = new ByteArrayInputStream(res.content)) {
            IOUtils.copy(in, out);
          } catch (IOException ignored) {
            return;
          }
        } catch (IOException ignored) {
          return;
        }

        localFiles.markAsReady(task.fileId, task.blockId);
      } finally {
        inProgress.remove(task);
      }
    }
  }

  private static Set<Task> concurrentHashSet() {
    return Collections.newSetFromMap(new ConcurrentHashMap<>());
  }
}
