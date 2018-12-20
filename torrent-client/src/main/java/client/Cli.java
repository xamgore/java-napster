package client;

import client.Operations.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Objects;
import java.util.Scanner;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.concurrent.Executors.newScheduledThreadPool;


public class Cli implements Closeable {
  private static final String SERVER_IP = "127.0.0.1";
  private static final short SERVER_PORT = 8081;
  private static short PORT_TO_BIND = 8080;

  private final LocalFiles localFiles;
  private final LocalServer localServer;
  private final ScheduledExecutorService timer = newScheduledThreadPool(1);
  // todo: После скачивания отдельных блоков некоторого файла клиент становится сидом.


  public Cli(LocalFiles localFiles, LocalServer localServer, DownloadManager manager) {
    this.localFiles = localFiles;
    this.localServer = localServer;
    timer.scheduleAtFixedRate(onTimerWakeUp(), 0, 30, TimeUnit.SECONDS);
  }

  @Override public void close() throws IOException {
    log.info("Cli shutdown");
    timer.shutdown();
    localServer.close();
  }


  public static void main(String... args) throws InvocationTargetException, IllegalAccessException, IOException {
    if (args.length == 0) {
      System.out.println("Port argument required:  client.sh <port>");
      System.out.println("  default value " + PORT_TO_BIND + " will be used");
    } else {
      PORT_TO_BIND = Short.parseShort(args[0]);
    }

    LocalFiles localFiles = new LocalFiles(Paths.get("blocks"), Paths.get("downloads"));
    LocalServer localServer = new LocalServer(localFiles, PORT_TO_BIND);
    DownloadManager manager = new DownloadManager(localFiles, SERVER_IP, SERVER_PORT);
    new Cli(localFiles, localServer, manager).repl();
  }

  private void repl() throws InvocationTargetException, IllegalAccessException {
    Scanner scanner = new Scanner(System.in);
    help();

    REPL:
    while (scanner.hasNextLine()) {
      String[] args = scanner.nextLine().split("\\s+", 2);
      String verb = args[0];
      log.debug("/{}", verb);

      for (Method m : getClass().getDeclaredMethods()) {
        Verb annotation = m.getAnnotation(Verb.class);
        if (annotation == null || !m.getName().equals(verb)) continue;

        try {
          m.invoke(this, (Object) args);
        } catch (Exception e) {
          log.error("REPL handler error: ", e);
          System.err.println("Error raised: " + e.getMessage());
        }

        continue REPL;
      }

      log.debug("/help");
      help();
    }
  }


  @Verb(doc = "             - display this help message")
  private void help(String... args) {
    Method[] methods = getClass().getDeclaredMethods();

    System.out.println("\nUsage:");
    System.out.println("" +
        Stream.of(methods)
            .filter(m -> m.getAnnotation(Verb.class) != null)
            .map(m -> {
              Verb verb = m.getAnnotation(Verb.class);
              if (verb == null) return null;
              if (verb.doc().isEmpty()) return m.getName();
              System.out.printf("  %s %s\n", m.getName(), verb.doc());
              return null;
            })
            .filter(Objects::nonNull)
            .collect(Collectors.joining(", ", "\n  ", "\n")));
  }


  @Verb(doc = "             - list of all files on the server")
  private void list(String[] args) throws IOException {
    List.Response response = List.makeRequest(SERVER_IP, SERVER_PORT);

    for (List.Response.File file : response.files) {
      System.out.printf("%-3s %14s  %-6s\n", file.id + ":", file.name, humanReadable(file.size));
    }

    System.out.println();
  }


  @Verb(doc = "<filePath> - publish file on the server")
  private void upload(String[] args) throws IOException {
    if (args.length != 2) {
      System.out.println("Usage: upload <filePath>");
      return;
    }

    Path path = Paths.get(args[1]).toAbsolutePath();
    if (!path.toFile().exists()) {
      System.out.println("File not exists: " + path);
      return;
    }

    String fileName = path.getFileName().toString();
    long fileSize = path.toFile().length();
    Upload.Response response = Upload.makeRequest(SERVER_IP, SERVER_PORT, fileName, fileSize);

    localFiles.addAsExisting(response.fileId, path);
    System.out.println("Published, fileId is " + response.fileId + "\n");
  }


  @Verb(doc = "<fileId> - add file to the download queue")
  private void download(String[] args) throws IOException {
    if (args.length != 2) {
      System.out.println("Usage: download <fileId>");
      return;
    }

    int fileId = Integer.parseInt(args[1]);
    if (localFiles.exists(fileId)) {
      System.out.println("ok");
      return;
    }

    List.Response listResponse = List.makeRequest(SERVER_IP, SERVER_PORT);
    for (List.Response.File remoteFile : listResponse.files) {
      if (remoteFile.id == fileId) {
        localFiles.addEmpty(fileId, remoteFile.size, remoteFile.name);
        System.out.println("ok");
        return;
      }
    }

    System.out.printf("There is no file with %d id on the tracker", fileId);
    System.out.println();
  }


  @Verb(doc = "[ip port fileId]   - list available blocks to share")
  private void stats(String[] args) throws IOException {
    if (args.length != 2) {
      Collection<FileStats> stats = localFiles.getStats();
      System.out.println("  Total files: " + stats.size());
      stats.forEach(s -> System.out.printf("%d: %s; %d blocks, %d ready",
          s.fileId, s.absolutePathToOriginal.getName(0), s.loadedBlocks.totalNumber, s.loadedBlocks.indexes.size()));
      return;
    }

    args = args[1].split("\\s+");
    if (args.length != 3) {
      System.out.println("Usage:  stats ip port fileId");
      return;
    }

    String ip = args[0];
    short port = Short.parseShort(args[1]);
    int fileId = Integer.parseInt(args[2]);
    Stat.Response response = Stat.makeRequest(ip, port, fileId);

    System.out.println("Blocks available: " + response.blocksIndexes.size());
    System.out.print("  ");
    for (Integer idx : response.blocksIndexes) {
      System.out.print(idx + " ");
    }
  }


  @Verb(doc = "<fileId>  - list of active <fileId> seeds")
  private void sources(String[] args) throws IOException {
    if (args.length != 2) {
      System.out.println("Usage: source <fileId>");
      return;
    }

    int fileId = Integer.parseInt(args[1]);
    Sources.Response response = Sources.makeRequest(SERVER_IP, SERVER_PORT, fileId);

    if (response.seeds.size() == 0) {
      System.out.println("There is no seeds");
    } else {
      for (Sources.Response.Seed seed : response.seeds)
        System.out.printf("%s %d\n", seed.ip, seed.port);
    }

    System.out.println();
  }


  @Verb(doc = "[<fileId>] - list of <fileId> to share")
  private void update(String[] args) throws IOException {
    if (args.length != 2) {
      System.out.println("Usage: update <fileId1> <fileId2> ...");
      return;
    }

    args = args[1].split("\\s+");
    int[] fileIds = Stream.of(args).mapToInt(Integer::parseInt).toArray();
    Update.Response response = Update.makeRequest(SERVER_IP, SERVER_PORT, PORT_TO_BIND, fileIds);
    System.out.println(response.isOk ? "ok" : "failed");
    System.out.println();
  }


  @Verb(doc = "             - terminate client")
  private void exit(String[] args) {
    // todo: shutdown
  }


  // call update
  @NotNull private Runnable onTimerWakeUp() {
    return () -> {
      try {
        int[] existingFileIds = this.localFiles.getStats().stream()
            .filter(s -> s.loadedBlocks.isNotEmpty())
            .mapToInt(s -> s.fileId).toArray();

        boolean isOk = Update.makeRequest(SERVER_IP, SERVER_PORT, PORT_TO_BIND, existingFileIds).isOk;
        log.debug("Updated, {}", isOk);
      } catch (Exception e) {}
    };
  }

  private static String humanReadable(long bytes) {
    int unit = 1024;
    if (bytes < unit) return bytes + " B";
    int exp = (int) (Math.log(bytes) / Math.log(unit));
    String pre = ("KMGTPE").charAt(exp - 1) + ("i");
    return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
  }

  public static final Logger log = LoggerFactory.getLogger("Server");
}
