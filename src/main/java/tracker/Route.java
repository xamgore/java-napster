package tracker;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;

@FunctionalInterface
public interface Route {
  void handle(InetAddress clientIp, DataInputStream in, DataOutputStream out) throws IOException, InvocationTargetException, IllegalAccessException;
}
