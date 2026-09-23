package parse;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

public class LogsLoader {
  public static String loadSingleJson(Path file) {
    String text = null;
    try {
      text = Files.readString(file);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    return text;
  }

  public static Map<String, Boolean> loadGroundTruthFile(Path file) {
    String text = loadSingleJson(file);
    Type type = new TypeToken<Map<String, Boolean>>() {
    }.getType();
    Gson gson = new Gson();
    Map<String, Boolean> gt = gson.fromJson(text, type);
    return gt;
  }

  public static String preProcess(String text) {
    text = text.replace("\n", ",");
    text = StringUtils.stripEnd(text, ",");
    text = "[" + text + "]";
    return text;
  }

  public static List<Pair<Integer, String>> loadJsonLogsFromFolder(String historyFolder) {
    File dir = new File(historyFolder);
    File[] logFiles = dir.listFiles();

    List<Pair<Integer, String>> tid2txns = new ArrayList<>();
//        List<ClientHistoryPOJO> clientHistoryPOJOS = new ArrayList<>();
    if (logFiles != null) {
      for (File logFile : logFiles) {
        if (!logFile.getName().startsWith("J") || !logFile.getName().endsWith(".log")) {
          continue;
        }
        String text = null;
        try {
          text = Files.readString(Paths.get(logFile.getPath()));
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
        text = preProcess(text);

        String fileName = logFile.getName();
        int threadId = Integer.parseInt(fileName.substring(1, fileName.length() - 4));

        tid2txns.add(new ImmutablePair<Integer, String>(threadId, text));
      }
    } else {
      // Handle the case where dir is not really a directory.
      // Checking dir.isDirectory() above would not be sufficient
      // to avoid race conditions with another process that deletes
      // directories.
      System.err.println("Cannot find logs");
      System.exit(-1);
    }

    // TODO: filter :ok
    return tid2txns;
  }

  public static String loadLogsFromFile(String filePath) {
    String text = null;
    try {
      text = Files.readString(Paths.get(filePath));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    text = preProcess(text);
    return text;
  }

  public static String loadLogsFromFileWithoutPreProcessing(String filePath) {
    String text = null;
    try {
      text = Files.readString(Paths.get(filePath));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return text;
  }

//    public static String loadEDNLogsFromFile(String filePath) throws IOException {
//        String text = Files.readString(Paths.get(filePath));
//        text = text.replace("\n", ",");
//        text = StringUtils.stripEnd(text, ",");
//        text = "[" + text + "]";
//
//        return text;
//    }
}
