package graphs.graphs;

import lombok.Getter;
import lombok.Setter;

public class ArrayMatrixGraph extends MatrixGraph {
  @Setter
  @Getter
  private int[][] matrix;

  public ArrayMatrixGraph(NodeIndexer indexer) {
    super(indexer);
    matrix = new int[indexer.size()][indexer.size()];
  }

  @Override
  public void set(int i, int j) {
    matrix[i][j] = 1;
  }

  @Override
  public int get(int i, int j) {
    return matrix[i][j];
  }
}
