package client;

import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class BlocksSet implements Iterable<Integer> {
  public Set<Integer> indexes;
  public int totalNumber;

  public BlocksSet(Set<Integer> indexes, int totalNumber) {
    this.indexes = indexes;
    this.totalNumber = totalNumber;
  }


  public BlocksSet invert() {
    Set<Integer> set = IntStream.range(0, totalNumber).filter(indexes::contains).boxed().collect(Collectors.toSet());
    return new BlocksSet(set, totalNumber);
  }

  public static BlocksSet range(int totalNumber) {
    return new BlocksSet(IntStream.range(0, totalNumber).boxed().collect(Collectors.toSet()), totalNumber);
  }

  public static BlocksSet empty(int totalNumber) {
    return new BlocksSet(new HashSet<>(), totalNumber);
  }


  @NotNull @Override public Iterator<Integer> iterator() {
    return indexes.iterator();
  }

  @Override public void forEach(Consumer<? super Integer> action) {
    indexes.forEach(action);
  }

  @Override public Spliterator<Integer> spliterator() {
    return indexes.spliterator();
  }

  public boolean isNotEmpty() {
    return !indexes.isEmpty();
  }
}
