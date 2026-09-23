package graphs.graphs;

import lombok.Getter;
import lombok.Setter;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

@Deprecated
public class NdArrayMatrixGraph extends MatrixGraph {
  @Setter
  @Getter
  private INDArray matrix;
  private DataType dataType;

  public NdArrayMatrixGraph(NodeIndexer indexer, DataType dataType) {
    super(indexer);
    int n = indexer.size();
    this.matrix = Nd4j.zeros(dataType, n, n);
    this.dataType = dataType;
  }

  @Override
  public void set(int i, int j) {
    matrix.putScalar(new int[]{i, j}, 1);
  }

  @Override
  public int get(int i, int j) {
    return matrix.getInt(i, j);
  }
}
