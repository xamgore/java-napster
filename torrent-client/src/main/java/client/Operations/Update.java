package client.Operations;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class Update {
  private static final byte REQUEST_ID = 4;

  public static class Response {
    public final boolean isOk;

    public Response(boolean isOk) { this.isOk = isOk; }
  }

  public static Response makeRequest(String serverIp, short serverPort, short localPort, int[] fileIds)
      throws IOException {
    try (Socket socket = new Socket(serverIp, serverPort);
         DataInputStream in = new DataInputStream(socket.getInputStream());
         DataOutputStream out = new DataOutputStream(socket.getOutputStream());
    ) {
      out.writeByte(REQUEST_ID);
      out.writeShort(localPort);
      out.writeInt(fileIds.length);
      for (int id : fileIds) out.writeInt(id);
      out.flush();
      return new Response(in.readBoolean());
    }
  }
}
