package common.binaryhistory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SessionHistory implements Serializable {
  private static final long serialVersionUID = 6529685098267757691L;
  private List<LOG_TXN> txns = new ArrayList<>();

  public static SessionHistory loadFromFile(String logFile) {
    return loadFromFile(new File(logFile));
  }

  public static SessionHistory loadFromFile(File logFile) {
    SessionHistory sessionHistory;
    ObjectInputStream ois;
    try {
      ois = new ObjectInputStream(new FileInputStream(logFile));
      sessionHistory = (SessionHistory) ois.readObject();
    } catch (IOException | ClassNotFoundException e) {
      throw new RuntimeException(e);
    }

    return sessionHistory;
  }

  public int size(){
    return txns.size();
  }

  public LOG_TXN getKthTxn(int k){
    assert k >= 0 && k < txns.size();
    return txns.get(k);
  }

  /**
   * txn: List<Mop> a txn
   * @param txn
   */
  public void commitSingleTxn(LOG_TXN txn){
    txns.add(txn);
  }

  public void write2Files(Path path) {
    ObjectOutputStream oos = null;
    try {
      oos = new ObjectOutputStream(new FileOutputStream(path.toFile()));
      oos.writeObject(this);
      oos.close();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
