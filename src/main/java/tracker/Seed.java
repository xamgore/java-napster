package tracker;

import org.jetbrains.annotations.NotNull;

import java.net.InetAddress;
import java.time.Instant;
import java.util.Objects;

public class Seed {
  final InetAddress ip;
  final short port;
  private long lastActive;  // unixtime

  public Seed(@NotNull InetAddress ip, short port) {
    this.ip = ip;
    this.port = port;
    this.lastActive = now();
  }

  public boolean wasActiveLast(int numOfMinutes) {
    final int secondsPerMinute = 60;
    return (lastActive - now()) / secondsPerMinute < numOfMinutes;
  }

  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Seed seed = (Seed) o;
    return port == seed.port &&
        ip.equals(seed.ip);
  }

  @Override public int hashCode() {
    return Objects.hash(ip, port);
  }

  private static long now() {
    return Instant.now().getEpochSecond();
  }
}
