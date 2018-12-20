package client.Operations;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;

public class Get {
  private static final byte REQUEST_ID = 2;

  public static class Response {
    public final byte[] content;

    public Response(byte[] content) {
      this.content = content;
    }
  }

  public static Response makeRequest(String serverIp, short serverPort, int fileId, int blockId, int resSize)
      throws IOException {
    try (Socket socket = new Socket(serverIp, serverPort);
         DataInputStream in = new DataInputStream(socket.getInputStream());
         DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
      out.writeByte(REQUEST_ID);
      out.writeInt(fileId);
      out.writeInt(blockId);
      out.flush();

      byte[] content = new byte[resSize];
      in.readFully(content);
      return new Response(content);
    }
  }
}
