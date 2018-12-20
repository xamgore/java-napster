package client;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BoundedInputStream;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static client.Cli.log;
import static java.nio.channels.Channels.newInputStream;
import static java.nio.file.StandardOpenOption.READ;

public class LocalServer implements Closeable {
  public static final int STAT_REQUEST = 1;
  public static final int GET_REQUEST = 2;
  public final LocalFiles localFiles;
  public final short portToBind;

  private final ServerSocket serverSocket;
  private final ExecutorService pool = Executors.newCachedThreadPool();


  public LocalServer(LocalFiles localFiles, short portToBind) throws IOException {
    this.localFiles = localFiles;
    this.portToBind = portToBind;
    this.serverSocket = new ServerSocket(portToBind);
    pool.submit(this::listen);
  }

  @Override public void close() throws IOException {
    log.info("Server shutdown");
    try {
      serverSocket.close();
    } finally {
      pool.shutdown();
    }
  }

  private void listen() {
    log.info("Start listening clients on {}:{}", serverSocket.getInetAddress(), serverSocket.getLocalPort());

    while (!Thread.interrupted()) {
      try {
        pool.submit(handle(serverSocket.accept()));
      } catch (IOException err) {
        log.error("Error while accepting socket", err);
      }
    }
  }

  private Runnable handle(Socket client) {
    return () -> {
      log.debug("Connected        ", client.getInetAddress(), client.getPort());

      try (Socket socket = client;
           DataInputStream in = new DataInputStream(socket.getInputStream());
           DataOutputStream out = new DataOutputStream(socket.getOutputStream());
      ) {
        byte requestMethodIdx = in.readByte();

        switch (requestMethodIdx) {
          case STAT_REQUEST: {
            log.info("/stat requested");
            int fileId = in.readInt();
            for (Integer idx : FileStats.load(localFiles.blocksDir, fileId).loadedBlocks)
              out.writeInt(idx);
            break;
          }

          case GET_REQUEST: {
            log.info("/get requested");
            int fileId = in.readInt();
            int blockId = in.readInt();
            Path path = FileStats.load(localFiles.blocksDir, fileId).absolutePathToOriginal;
            FileChannel channel = FileChannel.open(path, READ).position(blockId * LocalFiles.BLOCK_SIZE);
            try (InputStream block = new BoundedInputStream(newInputStream(channel), LocalFiles.BLOCK_SIZE)) {
              IOUtils.copy(block, out);
            }
            break;
          }
        }
      } catch (IOException err) {
        log.error("Error while handling client {}", client, err);
      }

      log.debug("Ok               {}:{}", client.getInetAddress(), client.getPort());
    };
  }

}
