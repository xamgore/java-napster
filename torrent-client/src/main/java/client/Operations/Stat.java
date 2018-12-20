package client.Operations;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;

public class Stat {
  private static final byte REQUEST_ID = 1;

  public static class Response {
    public final java.util.List<Integer> blocksIndexes;

    public Response(java.util.List<Integer> blocksIndexes) {
      this.blocksIndexes = blocksIndexes;
    }
  }

  public static Response makeRequest(String serverIp, short serverPort, int fileId)
      throws IOException {
    java.util.List<Integer> blocks = new ArrayList<>();

    try (Socket socket = new Socket(serverIp, serverPort);
         DataInputStream in = new DataInputStream(socket.getInputStream());
         DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
      out.writeByte(REQUEST_ID);
      out.writeInt(fileId);
      out.flush();

      int numOfBlocks = in.readInt();
      while (numOfBlocks-- > 0)
        blocks.add(in.readInt());
    }

    return new Response(blocks);
  }
}
