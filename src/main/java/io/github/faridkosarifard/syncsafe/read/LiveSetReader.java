package io.github.faridkosarifard.syncsafe.read;

import io.github.faridkosarifard.syncsafe.model.LiveRowSet;
import io.github.faridkosarifard.syncsafe.model.TableRef;
import java.io.IOException;
import java.util.List;

/** Reads the live (post-delete) row set of one table snapshot or version. */
public interface LiveSetReader {
  LiveRowSet read(TableRef table, List<String> keyColumns) throws IOException;
}
