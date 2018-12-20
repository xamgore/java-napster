package client.Operations;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;

public class Sources {
  private static final byte REQUEST_ID = 3;

  public static class Response {
    public final java.util.List<Seed> seeds;

    public Response(java.util.List<Seed> seeds) {
      this.seeds = seeds;
    }

    public static class Seed {
      public final InetAddress ip;
      public final short port;

      public Seed(InetAddress ip, short port) {
        this.ip = ip;
        this.port = port;
      }
    }
  }

  public static Response makeRequest(String serverIp, short serverPort, int fileId)
      throws IOException {
    java.util.List<Response.Seed> seeds = new ArrayList<>();

    try (Socket socket = new Socket(serverIp, serverPort);
         DataInputStream in = new DataInputStream(socket.getInputStream());
         DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
      out.writeByte(REQUEST_ID);
      out.writeInt(fileId);
      out.flush();

      int numOfSeeds = in.readInt();
      while (numOfSeeds-- > 0) {
        byte[] ipBytes = new byte[4];
        in.readFully(ipBytes, 0, ipBytes.length);
        seeds.add(new Response.Seed(InetAddress.getByAddress(ipBytes), in.readShort()));
      }
    }

    return new Response(seeds);
  }
}
