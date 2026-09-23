package parse;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Objects;

public class ParserUtils {
  /**
   * Find the files with a suffix in a given folder.
   * @return
   */
  public static ArrayList<File> findLogs(String folder, String prefix, String suffix) {
    Path path = Paths.get(folder);
    File logDir = path.toFile();
    if (!logDir.isDirectory()) {
      throw new Error("path is not a directory");
    }

    ArrayList<File> logs = new ArrayList<>();
    for (File f : Objects.requireNonNull(logDir.listFiles())) {
      if (f.isFile() && f.getName().endsWith(suffix) && f.getName().startsWith(prefix)) {
        logs.add(f);
      }
    }

    return logs;
  }
}
