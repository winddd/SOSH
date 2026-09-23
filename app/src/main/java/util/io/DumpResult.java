package util.io;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class DumpResult {
  public static void append(String line, String filePath) throws IOException {
    File file = new File(filePath);
    FileWriter fr = new FileWriter(file, true);
    line = line.charAt(line.length() - 1) != '\n' ? line + "\n" : line;
    fr.write(line);
    fr.close();
  }

  public static void writeToFile(String str, String filePath, boolean append) {
    File file = new File(filePath);
    FileWriter fr = null;
    try {
      fr = new FileWriter(file, append);
      fr.write(str);
      fr.close();
    }
    catch (IOException e) {
      e.printStackTrace();
    }
  }

  public static void writeToFile(List<String> strs, String filePath, boolean append) {
    File file = new File(filePath);
    FileWriter fr = null;
    try {
      fr = new FileWriter(file, append);
      for (var str : strs) {
        fr.write(str);
      }
      fr.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
