package io.github.faridkosarifard.syncsafe.read;

import java.nio.file.Path;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.LocalFileSystem;

/** Shared Hadoop configuration for local-file Iceberg and Delta Kernel access. */
public final class HadoopSupport {
  private HadoopSupport() {}

  public static Configuration configuration() {
    Configuration conf = new Configuration();
    conf.set("fs.file.impl", LocalFileSystem.class.getName());
    conf.setBoolean("fs.file.impl.disable.cache", true);
    conf.set("fs.defaultFS", "file:///");
    return conf;
  }

  public static String uri(Path path) {
    return path.toAbsolutePath().normalize().toUri().toString();
  }
}
