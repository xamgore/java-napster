package client.Operations;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;

public class List {
  private static final byte REQUEST_ID = 1;

  public static class Response {
    public final Collection<File> files;

    public Response(Collection<File> files) {
      this.files = files;
    }

    public static class File {
      public final int id;
      public final String name;
      public final long size;

      public File(int fileId, String fileName, long fileSize) {
        this.id = fileId;
        this.name = fileName;
        this.size = fileSize;
      }
    }
  }

  public static Response makeRequest(String serverIp, short serverPort) throws IOException {
    ArrayList<Response.File> files = new ArrayList<>();

    try (Socket socket = new Socket(serverIp, serverPort);
         DataInputStream in = new DataInputStream(socket.getInputStream());
         DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
      out.writeByte(REQUEST_ID);
      out.flush();

      int numOfFiles = in.readInt();
      while (numOfFiles-- > 0) {
        files.add(new Response.File(in.readInt(), in.readUTF(), in.readLong()));
      }
    }

    return new Response(files);
  }
}
