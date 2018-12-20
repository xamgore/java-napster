package tracker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;

import static java.util.concurrent.Executors.newCachedThreadPool;


public class Server implements Closeable {
  private static final int PORT = 8081;
  private static final Path JOURNAL_PATH = Paths.get("journal.txt");

  private final ExecutorService pool = newCachedThreadPool();
  private final Controller controller;
  private final ServerSocket socket;


  public Server(Controller controller, int port) throws IOException {
    this.socket = new ServerSocket(port);
    this.controller = controller;
  }

  @Override public void close() throws IOException {
    log.info("Server shutdown");
    try {
      socket.close();
    } finally {
      pool.shutdown();
    }
  }

  private void listen() {
    log.info("Start listening clients on {}:{}", socket.getInetAddress(), socket.getLocalPort());

    while (true) {
      try {
        pool.submit(handle(socket.accept()));
      } catch (IOException err) {
        if (!socket.isClosed()) {
          log.error("Error while accepting socket", err);
        }
      }
    }
  }

  private Runnable handle(Socket client) {
    return () -> {
      try (Socket socket = client;
           DataInputStream in = new DataInputStream(socket.getInputStream());
           DataOutputStream out = new DataOutputStream(socket.getOutputStream());
      ) {
        byte requestMethodIdx = in.readByte();
        controller.route(requestMethodIdx).handle(socket.getInetAddress(), in, out);
      } catch (IOException | InvocationTargetException | IllegalAccessException err) {
        log.error("Error while handling client {}", client, err);
      }
    };
  }


  public static void main(String[] args) throws IOException {
    Controller controller = new Controller(new Journal(JOURNAL_PATH));

    log.info("Initialize server");
    try (Server server = new Server(controller, PORT)) {
      server.listen();
    }
  }

  public static final Logger log = LoggerFactory.getLogger("Server");
}
