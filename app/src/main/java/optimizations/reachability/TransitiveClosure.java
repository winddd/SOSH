package optimizations.reachability;

import lombok.extern.slf4j.Slf4j;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.BooleanIndexing;
import org.nd4j.linalg.indexing.INDArrayIndex;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.nd4j.linalg.indexing.conditions.Conditions;
import util.Config;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class TransitiveClosure {
  private Config cfg;
  private DataType DATATYPE = DataType.FLOAT16;

  public TransitiveClosure(Config cfg) {
    this.cfg = cfg;
  }

  private long getN(INDArray m) {
    long[] shape = m.shape();
    assert shape.length == 2 && shape[0] == shape[1];
    long n = shape[0];
    return n;
  }

  private void setNdArrayFromSubIntegerArray(int[][] m, int rowStart, int rowEnd, int colStart, int colEnd, INDArray arr) {
    for (int i = rowStart; i < rowEnd; i ++){
      for (int j = colStart; j < colEnd; j++){
        m[i][j] = arr.getInt(i-rowStart, j-colStart);
      }
    }
  }


  /**
   * or in place
   * @param m1
   * @param m2
   */
  private int[][] or(int[][] m1, int[][] m2) {
    assert m1.length == m2.length && m1[0].length == m2[0].length;
    int l = m1.length;
    int n = m1[0].length;
    int[][] r = new int[l][n];

    for (int i = 0; i < l; i++) {
      for (int j = 0; j < n; j++) {
        r[i][j] = m1[i][j] & m2[i][j];
        assert r[i][j] == 0 || r[i][j] == 1;
      }
    }
    return r;
  }

  private int[][] matrixSquared(final int[][] m, final int blkSize) {
    // FIXME: potential overflow
    log.debug("Computing m*m for m=" + m);
    int n = m.length;
    assert blkSize <= n;
    // how many blocks along any dimension
    int numBlks = (n % blkSize) == 0 ? n / blkSize : (n/blkSize + 1);

    int[][] result = new int[n][n];
    for (int rowStart = 0; rowStart < n; rowStart += blkSize) {
      for (int colStart = 0; colStart < n; colStart += blkSize) {
        // Determine the end indices for slicing
        int rowEnd = Math.min(rowStart + blkSize, n);
        int colEnd = Math.min(colStart + blkSize, n);
        int rowSize = rowEnd - rowStart;
        int colSize = colEnd - colStart;
        log.debug(String.format("Computing [%d:%d,%d:%d]", rowStart, rowEnd, colStart, colEnd));
        // to compute the sub matrix: [rowStart:rowEnd, colStart:colEnd],
        // we need to prepare [rowStart:rowEnd, :] and [:, colStart:colEnd]
        // do sub matrix multiplication for `numBlks` times and sum them up
        // Here we assume that blkSize*blkSize must fit into GPU
        INDArray sum = Nd4j.zeros(DATATYPE, rowSize, colSize);
        for (int i = 0; i < numBlks; i ++) {
          log.debug(String.format("Computing %d/%d subblock for [%d:%d,%d:%d]", i, numBlks, rowStart, rowEnd, colStart, colEnd));
          int blkStart = i * blkSize;
          int blkEnd = Math.min(blkStart + blkSize, n);

          // dynamic create sub matrix
          INDArray leftSubBlk = createNdArrayFromSubIntegerArray(m, rowStart, rowEnd, blkStart, blkEnd);
          INDArray rightSubBlk = createNdArrayFromSubIntegerArray(m, blkStart, blkEnd, colStart, colEnd);
//          log.debug(String.format("leftSubBlk: [%d:%d, %d:%d]", rowStart, rowEnd, blkStart, blkEnd) + twoDimArray2String(leftSubBlk));
//          log.debug(String.format("rightSubBlk: [%d:%d, %d:%d]", blkStart, blkEnd, colStart, colEnd) + twoDimArray2String(rightSubBlk));
//          log.debug(String.format("shape_dim=%d: (%d, %d)", leftSubBlk.shape().length, leftSubBlk.shape()[0], leftSubBlk.shape()[1]));
//          log.debug(String.format("shape_dim=%d: (%d, %d)", rightSubBlk.shape().length, rightSubBlk.shape()[0], rightSubBlk.shape()[1]));
          INDArray subBlkProduct = null;
          if (rowSize == 1 && colSize == 1) {
            var leftSubVec = leftSubBlk.get(NDArrayIndex.point(0), NDArrayIndex.all());
            var rightSubVec = rightSubBlk.get(NDArrayIndex.all(), NDArrayIndex.point(0));
            subBlkProduct = Nd4j.zeros(DATATYPE, 1,1);
            subBlkProduct.put(new INDArrayIndex[] {
                    NDArrayIndex.interval(0, 1), // Rows 1 to 2
                    NDArrayIndex.interval(0, 1)},
                leftSubVec.mulRowVector(rightSubVec).sum());
          } else {
            subBlkProduct = leftSubBlk.mmul(rightSubBlk);
          }

          sum.addi(subBlkProduct);
        }

        BooleanIndexing.replaceWhere(sum, 1, Conditions.greaterThan(0));
        log.debug(String.format("Set result[%d:%d, %d:%d] to %s", rowStart, rowEnd, colStart, colEnd, sum));
        setNdArrayFromSubIntegerArray(result, rowStart, rowEnd, colStart, colEnd, sum);
      }
    }

    log.debug("End matrix squared");
//    log.debug(String.valueOf(result));
    return result;
  }

  private INDArray createNdArrayFromSubIntegerArray(int[][] m, int rowStart, int rowEnd, int colStart, int colEnd) {
    int rowSz = rowEnd - rowStart;
    int colSz = colEnd - colStart;
    var subBlk = Nd4j.zeros(DATATYPE, rowSz, colSz);
    for (int i = rowStart; i < rowEnd; i ++){
      for (int j = colStart; j < colEnd; j++){
        subBlk.putScalar(new int[]{i-rowStart, j-colStart}, m[i][j]);
      }
    }

    return subBlk;
  }


  public INDArray transitiveClosure(INDArray adjMatrix) {
//    var adjMatrix = javaAarray2NdArray(adjM);
//    profiler.startTick("transitiveClousre");
    log.debug(String.format("adjMatrix shape: [%d * %d]", adjMatrix.shape()[0], adjMatrix.shape()[1]));
    log.debug(adjMatrix + "");
    setSelfLoop(adjMatrix);
    log.info("Starting computing transitive closure");
//    INDArray lastRm = adjMatrix;
    INDArray currRm = adjMatrix;
    var n = getN(adjMatrix);
    int numIterations = (int) Math.ceil(Math.log(n) / Math.log(2));
    for(int k = 0; k < numIterations; k ++) {
      // A^(i+1) = A^i * A^i, where means i-th iteration
//      log.info(String.format("%dth:", k));
//      if (k % 100 == 0) {
//        log.info(String.format("%d/%d iteration of transitive closure:", k, n));
//      }
//      log.debug("lastRm" + lastRm.dataType() + "\n" + lastRm);
//      log.debug("currRm" + currRm.dataType() + "\n" + currRm);
//      var rowK = lastRm.get(NDArrayIndex.interval(k, k+1), NDArrayIndex.all());
//      var colK = lastRm.get(NDArrayIndex.all(), NDArrayIndex.interval(k, k+1));
//      var reachabilityViaK = colK.mmul(rowK);
      // A^n A is adj matrix, A^N = A^(n/2) * A^()
      // R = A^1 | A^2 | A^3 | ... | A^n
//      var reachabilityViaK = lastRm.mmul(lastRm);
//      profiler.startTick("mmul");
//      var reachabilityViaK = ;
//      profiler.endTick("mmul");
//      log.debug("reachabilityViaK: " + reachabilityViaK.dataType() + "\n" + reachabilityViaK);
//      currRm = lastRm.add(reachabilityViaK);
//      profiler.startTick("addi");
      currRm.addi(currRm.mmul(currRm));
      log.debug("After addi and mmul:");
      log.debug(String.valueOf(currRm));
//      profiler.endTick("addi");
//      currRm = Transforms.or(lastRm, reachabilityViaK).castTo(DATATYPE);
      //      BooleanIndexing.replaceWhere(currRm, from, Conditions.equals(0));
      BooleanIndexing.replaceWhere(currRm, 1, Conditions.greaterThan(0));
      log.debug("After quantization:");
      log.debug(String.valueOf(currRm));
//      if (lastRm.equals(currRm)) {
//        log.debug(String.format("early stop at %d-th iteration", k));
//        break;
//      } else {
//        lastRm = currRm;
//      }
//      lastRm = currRm;
    }
    log.info("Ending computing transitive closure");
    log.debug("currRm" + currRm.dataType() + " " + (currRm));

    rmSelfLoop(currRm);
//    profiler.endTick("transitiveClousre");
    return currRm;
  }

  /**
   * should only be enabled when the GPU memory cannot accommodate a matrix of n*n,
   * where n is the number of nodes.
   * 3060Ti should be able to handle n <= 80k~90k.
   * @param adjMatrix
   * @return
   */
  public int[][] transitiveClosure(int[][] adjMatrix) {
//    profiler.startTick("transitiveClousre");
    log.debug(adjMatrix + "");
    setSelfLoop(adjMatrix);
    log.info("Starting computing transitive closure");
//    var lastRm = adjMatrix;
    int[][] currRm = adjMatrix;
    var n = adjMatrix.length;
    int blkSize = cfg.SUB_MATRIX_SIZE;
    int numIterations = (int) Math.ceil(Math.log(n)/Math.log(2));

    for(int k = 0; k < numIterations; k ++) {
      log.debug(String.format("%d/%d iteration of transitive closure:", k, numIterations));
      log.debug("currRm" + currRm);
//      log.debug("lastRm:\n" + lastRm);
//      var rowK = currRm.get(NDArrayIndex.interval(k, k+1), NDArrayIndex.all());
//      var colK = currRm.get(NDArrayIndex.all(), NDArrayIndex.interval(k, k+1));
//      var reachabilityViaK = vecMul(colK, rowK);
      var reachabilityViaK = matrixSquared(currRm, blkSize);
//      log.debug("reachabilityViaK: \n" + reachabilityViaK);
//      currRm = Transforms.or(currRm, reachabilityViaK);
      currRm = or(currRm, reachabilityViaK);
//      if (Arrays.deepEquals(lastRm, currRm)) {
//        log.debug(String.format("early stop at %d-th iteration", k));
//        break;
//      } else {
//        lastRm = currRm;
//      }
    }

    log.debug("Transitive closure:");
    log.debug(String.valueOf(currRm));
    log.info("Ending computing transitive closure");

    rmSelfLoop(currRm);
//    profiler.endTick("transitiveClousre");
    return currRm;
  }


  private String twoDimArray2String(INDArray arr) {
    var shape = arr.shape();
    int m = (int) shape[0];
    int n = (int) shape[1];
    StringBuilder builder = new StringBuilder("[");

    for (int i = 0; i < m; i ++) {
      List<String> nums = new ArrayList<>();
      for(int j = 0; j<n;j++){
        nums.add(String.valueOf(arr.getInt(i,j)));
      }
      builder.append("[");
      builder.append(String.join(",", nums));
      builder.append("]");
    }
    builder.append("]");
    return builder.toString();
  }

  private INDArray setSelfLoop(INDArray rm) {
    return selfLoop(rm, 1);
  }

  private INDArray rmSelfLoop(INDArray rm) {
    return selfLoop(rm, 0);
  }

  private INDArray selfLoop(INDArray rm, int scalar) {
    long[] shape = rm.shape();
    assert shape.length == 2 && shape[0] == shape[1];
    long n = shape[0];
    for (var i = 0; i < n; i ++) {
      rm.putScalar(new int[]{i, i}, scalar);
    }

    return rm;
  }

  private int[][] setSelfLoop(int[][] rm) {
    return selfLoop(rm, 1);
  }

  private int[][] rmSelfLoop(int[][] rm) {
    return selfLoop(rm, 0);
  }

  private int[][] selfLoop(int[][] rm, int scalar) {
    assert rm[0].length == rm.length;
    long n = rm.length;
    for (var i = 0; i < n; i ++) {
      rm[i][i] = scalar;
    }

    return rm;
  }

  //  private int[][] ndArray2javaMatrix(INDArray matrix) {
//    profiler.startTick("ndArray2javaMatrix");
//    int n = (int) getN(matrix);
//    int[][] javaArray = new int[n][n];
//    for (int i = 0; i < n; i ++) {
//      for (int j = 0; j < n; j ++) {
//        javaArray[i][j] = matrix.getInt(i,j);
//      }
//    }
//
//    profiler.startTick("ndArray2javaMatrix");
//    return javaArray;
//  }
//
//  private INDArray javaMatrix2NdArray(int[][] matrix) {
//    profiler.startTick("javaMatrix2NdArray");
//    int n = matrix.length;
//    INDArray ndArray = Nd4j.zeros(DATATYPE, n, n);
//    for (int i = 0; i < n; i ++) {
//      for (int j = 0; j < n; j ++) {
//        ndArray.putScalar(new int[]{i,j}, matrix[i][j]);
//      }
//    }
//    profiler.endTick("javaMatrix2NdArray");
//    return ndArray;
//  }

//  private int[][] deepCopy2DArray(int[][] array) {
//    if (array == null) {
//      return null;
//    }
//
//    int[][] copy = new int[array.length][];
//    for (int i = 0; i < array.length; i++) {
//      // Allocate a new array for each row and copy the elements
//      copy[i] = new int[array[i].length];
//      System.arraycopy(array[i], 0, copy[i], 0, array[i].length);
//    }
//    return copy;
//  }
}
