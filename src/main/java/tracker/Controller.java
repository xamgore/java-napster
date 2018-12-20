package tracker;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.util.List;

import static java.lang.Math.max;
import static tracker.Server.log;

public class Controller {
  private static final int LAST_FIVE_MINUTES = 5;

  private final Journal filesJournal;
  private final ActiveSeeds activeSeeds;


  public Controller(Journal filesJournal) {
    this.filesJournal = filesJournal;
    this.activeSeeds = new ActiveSeeds(LAST_FIVE_MINUTES, filesJournal.getIds());
  }

  public Route route(byte requestIdx) {
    Method[] methods = Controller.class.getDeclaredMethods();

    for (Method m : methods) {
      RouteId annotation = m.getAnnotation(RouteId.class);
      if (annotation == null || annotation.value() != requestIdx) continue;
      log.info("Called /{} route", m.getName());
      return (ip, in, out) -> m.invoke(this, ip, in, out);
    }

    // default route
    return this::none;
  }


  // список раздаваемых файлов
  @RouteId(1)
  private void list(InetAddress clientIp, DataInputStream in, DataOutputStream out) throws IOException {
    List<Journal.Record> records = filesJournal.getRecords();

    out.writeInt(records.size());
    for (Journal.Record record : records) {
      out.writeInt(record.id);
      out.writeUTF(record.name);
      out.writeLong(record.size);
    }
  }

  // публикация нового файла
  @RouteId(2)
  private void upload(InetAddress clientIp, DataInputStream in, DataOutputStream out) throws IOException {
    String fileName = in.readUTF();
    long fileSize = in.readLong();

    int fileId = filesJournal.add(fileSize, fileName);
    activeSeeds.prepare(fileId);
    out.writeInt(fileId);
  }

  @RouteId(3)
  // список клиентов, владеющих определенным файлом целиком или некоторыми его частями
  private void sources(InetAddress clientIp, DataInputStream in, DataOutputStream out) throws IOException {
    int fileId = in.readInt();

    List<Seed> seeds = activeSeeds.of(fileId);
    out.writeInt(seeds.size());

    for (Seed seed : seeds) {
      out.write(seed.ip.getAddress());
      out.writeShort(seed.port);
    }
  }

  @RouteId(4)
  // загрузка клиентом данных о раздаваемых файлах
  private void update(InetAddress clientIp, DataInputStream in, DataOutputStream out) throws IOException {
    short clientPort = in.readShort();
    Seed seed = new Seed(clientIp, clientPort);

    int numOfFilesShared = max(0, in.readInt());
    while (numOfFilesShared-- > 0) {
      int fileId = in.readInt();
      activeSeeds.addIfPrepared(fileId, seed);
    }
  }

  // default route
  private void none(InetAddress clientIp, DataInputStream in, DataOutputStream out) {
    log.info("Called /none route");
  }
}
