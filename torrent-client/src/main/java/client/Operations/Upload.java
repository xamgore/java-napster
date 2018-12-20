package client.Operations;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class Upload {
  private static final byte REQUEST_ID = 2;

  public static class Response {
    public final int fileId;

    public Response(int fileId) {this.fileId = fileId;}
  }

  public static Response makeRequest(String serverIp, short serverPort, String fileName, long fileSize)
      throws IOException {
    try (Socket socket = new Socket(serverIp, serverPort);
         DataInputStream in = new DataInputStream(socket.getInputStream());
         DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
      out.writeByte(REQUEST_ID);
      out.writeUTF(fileName);
      out.writeLong(fileSize);
      out.flush();
      return new Response(in.readInt());
    }
  }
}
