package opencl.generator

import apart.arithmetic.{ArithExpr, SizeVar, Var}
import benchmarks.{GEMM, MatrixMultiplication}
import ir._
import ir.ast._
import opencl.executor._
import opencl.ir._
import opencl.ir.pattern._
import org.junit.Assert._
import org.junit.{AfterClass, BeforeClass, Test}

import scala.reflect.ClassTag

object TestMatrixMatrix {
  @BeforeClass def before() {
    Executor.loadLibrary()
    println("Initialize the executor")
    Executor.init()
  }

  @AfterClass def after() {
    println("Shutdown the executor")
    Executor.shutdown()
  }
}

class TestMatrixMatrix {

  @Test def MATRIX_MATRIX_SIMPLE() {

    val Msize = 256
    val Ksize = 64
    val Nsize = 512
    val matrixA = Array.tabulate(Msize, Ksize)((r, c) => (((r * 3 + c * 2) % 10) + 1) * 1.0f)
    val matrixB = Array.tabulate(Ksize, Nsize)((r, c) => (((r * 7 + c * 3) % 10) + 1) * 1.0f)

    val N = SizeVar("N")
    val M = SizeVar("M")
    val K = SizeVar("K")

    val f = fun(
      ArrayType(ArrayType(Float, K), M),
      ArrayType(ArrayType(Float, K), N),
      (A, B) => {
        MapWrg(fun( Arow =>
          Join() o  MapLcl(fun( Bcol =>
            toGlobal(MapSeq(id)) o ReduceSeq(fun((acc, y) => multAndSumUp.apply(acc, Get(y, 0), Get(y, 1))), 0.0f) $ Zip(Arow, Bcol)
          )) $ B
        )) $ A
      })

    val (output: Array[Float], runtime) = Execute(Msize * Nsize)(f, matrixA, matrixB.transpose)

    println("output.size = " + output.length)
    println("output(0) = " + output(0))
    println("runtime = " + runtime)

    val gold = Utils.matrixMatrixMultiply(matrixA, matrixB).flatten

    assertArrayEquals(gold, output, 0.0001f)
  }

  @Test def MATRIX_MATRIX_SIMPLER() {

    val Msize = 64
    val Ksize = 128
    val Nsize = 256
    val matrixA = Array.tabulate(Msize, Ksize)((r, c) => (((r * 3 + c * 2) % 10) + 1) * 1.0f)
    val matrixB = Array.tabulate(Ksize, Nsize)((r, c) => (((r * 7 + c * 3) % 10) + 1) * 1.0f)

    val N = SizeVar("N")
    val M = SizeVar("M")
    val K = SizeVar("K")

    val f = fun(
      ArrayType(ArrayType(Float, K), M),
      ArrayType(ArrayType(Float, K), N),
      (A, B) => {
        MapGlb(fun( Arow =>
          MapSeq(fun( Bcol =>
            toGlobal(MapSeq(id)) o ReduceSeq(add, 0.0f) o MapSeq(mult) $ Zip(Arow, Bcol)
          )) $ B
        )) $ A
      })

    val (output: Array[Float], runtime) = Execute(Msize * Nsize)(f, matrixA, matrixB.transpose)

    println("output.size = " + output.length)
    println("output(0) = " + output(0))
    println("runtime = " + runtime)

    val gold = Utils.matrixMatrixMultiply(matrixA, matrixB).flatten

    assertArrayEquals(gold, output, 0.0f)
  }

  @Test def MATRIX_MATRIX_2D_GLOBAL_ID() {

    val Msize = 512
    val Ksize = 512
    val Nsize = 512
    val matrixA = Array.tabulate(Msize, Ksize)((r, c) => (((r * 3 + c * 2) % 10) + 1) * 1.0f)
    val matrixB = Array.tabulate(Ksize, Nsize)((r, c) => (((r * 7 + c * 3) % 10) + 1) * 1.0f)

    val N = SizeVar("N")
    val M = SizeVar("M")
    val K = SizeVar("K")

    val f1 = fun(
      ArrayType(ArrayType(Float, K), M),
      ArrayType(ArrayType(Float, K), N), // this is already transposed
      (A, B) => {
        MapGlb(0)(fun( Arow =>
          MapGlb(1)(fun( Bcol =>
            toGlobal(MapSeq(id)) o ReduceSeq(fun((acc, y) => multAndSumUp.apply(acc, Get(y, 0), Get(y, 1))), 0.0f) $ Zip(Arow, Bcol)
          )) $ B
        )) $ A
      })

    val f2 = fun(
      ArrayType(ArrayType(Float, K), M),
      ArrayType(ArrayType(Float, K), N), // this is already transposed
      (A, B) => {
        MapGlb(0)(MapGlb(1)(toGlobal(MapSeq(id)) o ReduceSeq(fun((acc, y) => multAndSumUp.apply(acc, Get(y, 0), Get(y, 1))), 0.0f))) o
          Map(fun( Arow =>
            Map(fun( Bcol =>
              Zip(Arow, Bcol)
            )) $ B
          )) $ A
      })

    val (output: Array[Float], runtime) = Execute(Msize * Nsize)(f1, matrixA, matrixB.transpose)

    println("output.size = " + output.length)
    println("output(0) = " + output(0))
    println("runtime = " + runtime)

    val gold = Utils.matrixMatrixMultiply(matrixA, matrixB).flatten

    assertArrayEquals(gold, output, 0.001f)

    val (output2: Array[Float], runtime2) = Execute(Msize * Nsize)(f2, matrixA, matrixB.transpose)

    println("output.size = " + output2.length)
    println("output(0) = " + output2(0))
    println("runtime = " + runtime2)

    assertArrayEquals(gold, output2, 0.001f)
  }

  @Test def vectorised() {

    val Msize = 16
    val Ksize = 16
    val Nsize = 16
    val matrixA = Array.tabulate(Msize, Ksize)((r, c) => (((r * 3 + c * 2) % 10) + 1) * 1.0f)
    val matrixB = Array.tabulate(Ksize, Nsize)((r, c) => (((r * 7 + c * 3) % 10) + 1) * 1.0f)

    val N = SizeVar("N")
    val M = SizeVar("M")
    val K = SizeVar("K")

    val f1 = fun(
      ArrayType(ArrayType(Float, K), M),
      ArrayType(ArrayType(Float, K), N), // this is already transposed
      (A, B) => {
        MapGlb(0)(fun( Arow =>
          MapGlb(1)(fun( Bcol =>
            toGlobal(MapSeq(id)) o ReduceSeq(add, 0.0f) o asScalar() o
              ReduceSeq(fun((acc, next) => VectorizeUserFun(4, multAndSumUp)(acc, Get(next, 0), Get(next, 1))), Value(0.0f).vectorize(4))
              $ Zip(asVector(4) $ Arow, asVector(4) $ Bcol)
          )) $ B
        )) $ A
      })

    val f1_ = fun(
      ArrayType(ArrayType(Float, K), M),
      ArrayType(ArrayType(Float, K), N),
      (A, B) => {
        // for each row of A ...
        A :>> MapGlb(0)(\( Arow =>
          // ... and each column of B ...
          B :>> MapGlb(1)(\( Bcol =>
            // ... combine them into a single vectorized vector ...
            Zip(asVector(4)(Arow), asVector(4)(Bcol)) :>>
            // ... reduce this vector using a vectorized operator ...
            ReduceSeq(\( (acc, pair) => {
              val multAndSumUpVec = VectorizeUserFun(4, multAndSumUp)

              multAndSumUpVec(acc, pair._0, pair._1)
            }), Value(0.0f).vectorize(4)) :>>
            // ... turn the vector data back into scalars and finish the reduction ...
            asScalar() :>> ReduceSeq(add, 0.0f) :>>
            // ... copy the result back into global memory (from private)
            toGlobal(MapSeq(id))
          ))
        ))
      })

    // Derived. TODO: Actual one contains some empty MapSeqs.
    // High-level da55a60496191590d618057cadfe6d18b409f8b76cd31753123701e465a8ea4d
    // Low-level 330f47d76e559e4f466c49cde9d7bd9438b370f8cd032e22f20ee156db54bd9a
    val fd = fun(
      ArrayType(ArrayType(Float, K), M),
      ArrayType(ArrayType(Float, K), N), // this is already transposed
      (p_924477420, p_640363654) =>
      FunCall(MapGlb(0)(fun((p_317986356) =>
        FunCall(MapGlb(1)(fun((p_331510866) =>
          toGlobal(MapSeq(id)) $ FunCall(ReduceSeq(fun((p_1728790703, p_1227074340) =>
            FunCall(add, p_1728790703, p_1227074340))), Value("0.0f", Float),
            FunCall(asScalar(),
                FunCall(ReduceSeq(fun((p_929776179, p_1765250898) =>
                  FunCall(VectorizeUserFun(4,add), p_929776179, FunCall(VectorizeUserFun(4,mult), FunCall(Get(0), p_1765250898), FunCall(Get(1), p_1765250898)))
                )),
                  FunCall(VectorizeUserFun(4, id), Value("0.0f", VectorType(Float, 4))),
                  FunCall(Zip(2), FunCall(asVector(4), p_317986356), FunCall(asVector(4), p_331510866)))
            ))
        )), p_640363654)
      )), p_924477420))

    val (output1: Array[Float], _) = Execute(16, 16, Msize, Nsize, (true, true))(f1, matrixA, matrixB.transpose)
    val (output2: Array[Float], _) = Execute(16, 16, Msize, Nsize, (true, true))(fd, matrixA, matrixB.transpose)

    val gold = Utils.matrixMatrixMultiply(matrixA, matrixB).flatten

    assertArrayEquals(gold, output1, 0.001f)
    assertArrayEquals(gold, output2, 0.001f)

  }

  @Test def vectorisedReuseA() {

    val Msize = 8
    val Ksize = 32
    val Nsize = 16
    val matrixA = Array.tabulate(Msize, Ksize)((r, c) => (((r * 3 + c * 2) % 10) + 1) * 1.0f)
    val matrixB = Array.tabulate(Ksize, Nsize)((r, c) => (((r * 7 + c * 3) % 10) + 1) * 1.0f)

    val vectorLength = 4

    val N = SizeVar("N")
    val M = SizeVar("M")
    val K = SizeVar("K")

    val mult = UserFun("mult", Array("l", "r"), "{ return l * r; }", Seq(Float, Float4), Float4)

    val f = fun(
      ArrayType(ArrayType(Float, K), M),
      ArrayType(ArrayType(Float, N), K),
      (A, B) =>
        Map(Join()) o
          MapGlb(0)(fun(rowA => MapGlb(1)( fun(colsB =>
            toGlobal(MapSeq(VectorizeUserFun(4, id))) o Join() o ReduceSeq(fun((acc, elemRowPair) =>
              MapSeq(fun(partial => VectorizeUserFun(4,add)(Get(partial, 0), Get(partial, 1))))
                $ Zip(MapSeq(fun(b => mult(Get(elemRowPair, 0), b))) o asVector(vectorLength) $ Get(elemRowPair, 1), acc)
            ), toPrivate(MapSeq(VectorizeUserFun(4, id))) $ Value("0.0f", ArrayType(VectorType(Float, vectorLength), 1))) $ Zip(rowA, colsB)
          )) o Map(Transpose()) o Split(vectorLength) o Transpose() $ B
          )) $ A
    )

    val (output: Array[Float], _) = Execute(Msize, Nsize)(f, matrixA, matrixB)

    val gold = Utils.matrixMatrixMultiply(matrixA, matrixB).flatten

    assertArrayEquals(gold, output, 0.001f)

  }

  @Test def partiallyVectorisedTiled(): Unit = {
    // Basic tiled matrix multiply without local memory
    val mSize = 16
    val kSize = 16
    val nSize = 16
    val matrixA = Array.tabulate(mSize, kSize)((r, c) => (((r * 3 + c * 2) % 10) + 1) * 1.0f)
    val matrixB = Array.tabulate(kSize, nSize)((r, c) => (((r * 7 + c * 3) % 10) + 1) * 1.0f)

    val tileSize = 2
    val vectorLength = 4

    val gold = Utils.matrixMatrixMultiply(matrixA, matrixB).flatten

    // val dot = UserFun("dotp", Array("a", "b"), "{ return dot(a, b); }", Seq(Float4, Float4), Float)
    // ReduceSeq( add, 0.0f) o asScalar() o MapSeq(VectorizeUserFun(4, mult))
    // is equivalent to MapSeq(dot) if the input is of type Array(Float4, 1)

    val N = SizeVar("N")
    val M = SizeVar("M")
    val K = SizeVar("K")

    val f_ =  fun(
      ArrayType(ArrayType(Float, K), M),
      ArrayType(ArrayType(Float, K), N),
      (A, B) => {
        // Undo the tiling
        Untile() o
          MapGlb(0)(fun( aRows =>
            MapGlb(1)(fun( bCols =>

              Map(TransposeW()) o TransposeW() o

                toGlobal(MapSeq(MapSeq(MapSeq(MapSeq(id))))) o

                // Multiply all necessary combinations of tiles
                ReduceSeq(fun( (acc, pairOfTiles) =>

                  fun(partial =>
                    MapSeq(fun(pairOfRows =>
                      MapSeq(fun(a =>
                        MapSeq(add) $ Zip(Get(a, 0), Get(a, 1))
                      )) $ Zip(Get(pairOfRows, 0), Get(pairOfRows, 1))
                    )) $ Zip(acc, partial) ) o
                    MapSeq( fun(rowA =>
                      MapSeq( fun( colB =>
                        ReduceSeq(add, id $ Value(0.0f)) o asScalar() o
                          MapSeq(VectorizeUserFun(4, mult))
                          $ Zip(asVector(vectorLength) $ rowA, asVector(vectorLength) $ colB)
                      )) $ Get(pairOfTiles, 1)
                    )) $ Get(pairOfTiles, 0)
                )
                  , MapSeq(MapSeq(MapSeq(id)))
                    $ Value(0.0f, ArrayType(ArrayType(ArrayType(Float, 1), tileSize), tileSize))
                  // ArrayType(ArrayType(ArrayType(Float, 1), 2), 2))
                  // maliBNT stores it in float4 + vectorised final add + 2 vector stores instead 4 scalar ones
                ) $ Zip(aRows, bCols)

            )) o Tile(tileSize, vectorLength) $ B
            // Tile the matrices
          )) o Tile(tileSize, vectorLength) $ A
      })

    // same as above just in top to bottom notation
    val f__ = fun(
      ArrayType(ArrayType(Float, K), M),
      ArrayType(ArrayType(Float, K), N),
      (A, B) => {
        A :>> Tile(tileSize, vectorLength) :>>
          MapGlb(0)(fun( aRows =>
            B :>> Tile(tileSize, vectorLength) :>>
              MapGlb(1)(fun( bCols =>
                Zip(aRows, bCols) :>>
                  ReduceSeq(fun( (acc, pairOfTiles) => {
                    pairOfTiles._0 :>> MapSeq(fun( rowA =>
                      pairOfTiles._1 :>> MapSeq(fun( colB =>
                        Zip(asVector(vectorLength)(rowA), asVector(vectorLength)(colB)) :>>
                          MapSeq(VectorizeUserFun(4, mult)) :>>
                          asScalar() :>>
                          ReduceSeq(add, Value(0.0f) :>> id)
                      ))
                    )) :>> fun(partial =>
                      Zip(acc, partial) :>>
                      MapSeq(fun(pairOfRows =>
                        Zip(pairOfRows._0, pairOfRows._1) :>>
                        MapSeq(fun(x => Zip(x._0, x._1) :>> MapSeq(add)))
                      ))
                    )
                  }),
                    MapSeq(MapSeq(MapSeq(id)))(Value(0.0f, ArrayType(ArrayType(ArrayType(Float, 1), tileSize), tileSize)))
                  ) :>>
                  toGlobal(MapSeq(MapSeq(MapSeq(MapSeq(id))))) :>> TransposeW() :>> Map(TransposeW())
              ))
          )) :>> Untile()
      }
    )

    // load the tile in local memory once (to avoid reloading the same value twice) and vectorized the summation in the reduction loop
    val f = fun(
      ArrayType(ArrayType(Float, K), M),
      ArrayType(ArrayType(Float, K), N),
      (A, B) => {
        // tile A
        A :>> Tile(tileSize, vectorLength) :>>
        MapGlb(0)(fun( aRows =>
          // tile B
          B :>> Tile(tileSize, vectorLength) :>>
          MapGlb(1)(fun( bCols =>
            Zip(aRows, bCols) :>>
            ReduceSeq(fun( (acc, pairOfTiles) => {
              // copy both tiles into private memory
              Zip(
                pairOfTiles._0 :>> MapSeq(fun(rowA =>
                  asVector(vectorLength)(rowA) :>> toPrivate(MapSeq(VectorizeUserFun(4, id))))),
                pairOfTiles._1 :>> MapSeq(fun(colB =>
                  asVector(vectorLength)(colB) :>> toPrivate(MapSeq(VectorizeUserFun(4, id)))))
              ) :>> fun(zippedPairOfTiles =>
                // for each row of the tile of A ...
                zippedPairOfTiles :>> MapSeq(fun( rowA =>
                  // ... and each column of the tile of B ...
                  zippedPairOfTiles :>> MapSeq(fun( colB =>
                    // ... perform vectorized multiplication ...
                    Zip(rowA._0, colB._1) :>>
                    MapSeq(VectorizeUserFun(4, mult)) :>>
                    // .. and scalar summation
                    asScalar() :>>
                    ReduceSeq(add, Value(0.0f) :>> id)
                  ))
                ))
              ) :>> fun(partial =>
                // reshape the data to be vectorized for performing the summation
                partial :>> Join() :>> Join() :>> asVector(4) :>>
                fun(xs =>
                  // perform the vectorized summation
                  Zip(acc, xs) :>> MapSeq(VectorizeUserFun(4, add))
                ))
              }), MapSeq(VectorizeUserFun(4, id))(Value(0.0f, ArrayType(VectorType(Float, 4), 1)))
            ) :>>
            // reshape the data and perform the copy back to global memory using a vector width of 2
            Map(asScalar() >>> asVector(2) >>> Split(1)) :>>
            toGlobal(MapSeq(MapSeq(MapSeq(VectorizeUserFun(2, id))))) :>>
            TransposeW() :>> Map(TransposeW() >>> Join() >>> asScalar())
          ))
        )) :>> Untile()
      }
    )

    // Derived. TODO: Actual one contains some empty MapSeqs.
    // High-level 7352181db558ca218caa8723936a115f8e30dd4e69a2686e11d5a0255bf5d8a4
    // Low-level 72ef373f103c33cf9c79741a95fdd9d15d605f757529151844690d685c173c19
    val fd = fun(
      ArrayType(ArrayType(Float, K), M),
      ArrayType(ArrayType(Float, K), N),
      (A, B) =>
      FunCall(Join(), FunCall(MapGlb(0)(fun((p_1545087375) =>
        FunCall(TransposeW(), FunCall(Join(), FunCall(MapGlb(1)(fun((p_668210649) =>
          FunCall(TransposeW(), FunCall(Map(fun((p_1434041222) =>
            FunCall(TransposeW(), p_1434041222)
          )), FunCall(TransposeW(), FunCall(MapSeq(fun((p_1308109015) =>
            FunCall(toGlobal(MapSeq(MapSeq(id))), p_1308109015))), FunCall(ReduceSeq(fun((p_2050404090, p_280265505) =>
            FunCall(MapSeq(fun((p_827084938) =>
              FunCall(Join(), FunCall(MapSeq(fun((p_306206744) =>
                FunCall(ReduceSeq(fun((p_1597655940, p_2619171) =>
                  FunCall(add, p_1597655940, p_2619171))),
                  FunCall(Get(0), p_306206744), FunCall(asScalar(), FunCall(MapSeq(fun((p_1983025922) =>
                    FunCall(VectorizeUserFun(4,mult), FunCall(Get(0), p_1983025922), FunCall(Get(1), p_1983025922))
                  )), FunCall(Zip(2), FunCall(asVector(4), FunCall(Get(1), p_827084938)), FunCall(asVector(4), FunCall(Get(1), p_306206744)))))))), FunCall(Zip(2), FunCall(Get(0), p_827084938), FunCall(Transpose(), FunCall(Get(1), p_280265505)))))
            )), FunCall(Zip(2), p_2050404090, FunCall(Transpose(), FunCall(Get(0), p_280265505))))
          )), FunCall(MapSeq(fun((p_824208363) =>
            FunCall(MapSeq(fun((p_500179317) =>
              FunCall(idfloat, p_500179317)
            )), p_824208363))), Value("0.0f", ArrayType(ArrayType(Float, tileSize), tileSize))), FunCall(Zip(2), FunCall(Split(vectorLength), FunCall(Transpose(), p_1545087375)), FunCall(Split(vectorLength), FunCall(Transpose(), p_668210649))))))))
        )), FunCall(Split(tileSize), B))))
      )), FunCall(Split(tileSize), A))))


    val (output1: Array[Float], _) = Execute(2, 2, mSize/2, nSize/2, (true, true))(f, matrixA, matrixB.transpose)
    val (output2: Array[Float], _) = Execute(2, 2, mSize/2, nSize/2, (true, true))(fd, matrixA, matrixB.transpose)

    assertArrayEquals(gold, output1, 0.0001f)
    assertArrayEquals(gold, output2, 0.0001f)
  }

  @Test def tiledMultiplicationScala(): Unit = {
    val mSize = 16
    val kSize = 16
    val nSize = 16
    val matrixA = Array.tabulate(mSize, kSize)((r, c) => (((r * 3 + c * 2) % 10) + 1) * 1.0f)
    val matrixB = Array.tabulate(kSize, nSize)((r, c) => (((r * 7 + c * 3) % 10) + 1) * 1.0f)

    val gold = Utils.matrixMatrixMultiply(matrixA, matrixB).flatten

    val transposedB = matrixB.transpose

    val tileSize = 4

    val tiledA = matrixA.grouped(tileSize).toArray.map(_.transpose.grouped(tileSize).toArray.map(_.transpose))
    val tiledB = transposedB.grouped(tileSize).toArray.map(_.transpose.grouped(tileSize).toArray.map(_.transpose))

    // Matrix-Multiplication and reduction over tiles fused
    val tiledC = tiledA.map(aRows => {
      tiledB.map(bCols => {
        (aRows, bCols).zipped.foldLeft(Array.ofDim[Float](tileSize, tileSize))((acc, tiles) => {
          val aTile = tiles._1
          val bTile = tiles._2

          val temp = aTile.map(aRow => bTile.map(bRow => (aRow, bRow).zipped.map(_ * _).sum))

          (temp, acc).zipped.map((x, y) => (x, y).zipped.map(_+_))
        })
      })
    })

    val matrixC = tiledC.flatMap(_.transpose.map(_.flatten)).flatten

    assertArrayEquals(gold, matrixC, 0.001f)

    // Matrix-Multiplication and reduction over tiles separate
    val tiledC2 = tiledA.map(aRows => {
      tiledB.map(bCols => {
        (aRows, bCols).zipped.map((aTile, bTile) => aTile.map(aRow => bTile.map(bRow => (aRow, bRow).zipped.map(_ * _).sum))).
          foldLeft(Array.ofDim[Float](tileSize, tileSize))((acc, tile) => {

          (tile, acc).zipped.map((x, y) => (x, y).zipped.map(_+_))
        })
      })
    })

    val matrixC2 = tiledC2.flatMap(_.transpose.map(_.flatten)).flatten

    assertArrayEquals(gold, matrixC2, 0.001f)

    // Matrix-Multiplication and reduction over tiles separate, reduction over last dimension, not first
    val tiledC3 = tiledA.map(aRows => {
      tiledB.map(bCols => {
        (aRows, bCols).zipped.map((aTile, bTile) => aTile.map(aRow => bTile.map(bRow => (aRow, bRow).zipped.map(_ * _).sum))).
          transpose.map(_.transpose.map(_.sum))
      })
    })

    val matrixC3 = tiledC3.flatMap(_.transpose.map(_.flatten)).flatten

    assertArrayEquals(gold, matrixC3, 0.001f)

    // Trying to reuse A
    val matrixC4 = matrixA.map(rowA => (rowA, matrixB).zipped.map((elemA, rowB) => rowB.map(_*elemA)))
      .transpose.fold(Array.ofDim[Float](16, 16))((a, b) =>  (a, b).zipped.map((a, b) => (a, b).zipped.map(_+_))).flatten

    assertArrayEquals(gold, matrixC4, 0.001f)


    val grouped = transposedB.grouped(4).toArray.map(_.transpose)

    val matrixC5 = matrixA.map(rowA => grouped.map(columnsB => (rowA, columnsB).zipped.
      foldLeft(Array.ofDim[Float](4))((acc, elemRowPair) => (elemRowPair._2.map(_*elemRowPair._1), acc).
      zipped.map(_+_)))).map(_.flatten)

    assertArrayEquals(gold, matrixC5.flatten, 0.001f)

    // Trying to reuse B
    val matrixC6 = matrixA.grouped(4).toArray.map(rowsA => transposedB.map(colB =>(rowsA.transpose, colB).zipped.
      foldLeft(Array.ofDim[Float](4))((acc, rowElemPair) => (rowElemPair._1.map(_*rowElemPair._2), acc).
      zipped.map(_+_)))).map(_.transpose).flatten

    assertArrayEquals(gold, matrixC6.flatten, 0.001f)

    def reorder[T: ClassTag](array: Array[T], f: Int => Int): Array[T] = {
      val newArray = Array.ofDim[T](array.length)

      for (i <- array.indices) {
        newArray(i) = array(f(i))
      }

      newArray
    }

    val f: (Int) => Int = i => {
      val s = 4
      val n = matrixB.length / s
      (i / n) + s * (i % n)
    }

     // Trying to reuse both

     // B as innermost
     val matrixC7 = matrixA.grouped(4).toArray.map(rowsA =>
       reorder(matrixB.transpose, f).grouped(4).toArray.map(colsB => (rowsA.transpose, colsB.transpose).zipped.
         foldLeft(Array.ofDim[Float](4, 4))((acc, rowElemPair) => (rowElemPair._1.map(elem => rowElemPair._2.map(_ * elem)), acc).
         zipped.map((a, b) => (a, b).zipped.map(_+_))))
     ).map(_.map(_.transpose).flatten.transpose).flatten.map(reorder(_, f))

    assertArrayEquals(gold, matrixC7.flatten, 0.001f)

    // A as innermost
    val matrixC8 = matrixA.grouped(4).toArray.map(rowsA =>
      reorder(matrixB.transpose, f).grouped(4).toArray.map(colsB => (rowsA.transpose, colsB.transpose).zipped.
        foldLeft(Array.ofDim[Float](4, 4))((acc, rowElemPair) => (rowElemPair._2.map(elem => rowElemPair._1.map(_ * elem)), acc).
        zipped.map((a, b) => (a, b).zipped.map(_+_))))
    ).map(_.flatten.transpose).flatten.map(reorder(_, f))

    assertArrayEquals(gold, matrixC8.flatten, 0.001f)
  }

  @Test def mmReuseBothBInnermost(): Unit = {
    val mSize = 16
    val kSize = 16
    val nSize = 16
    val matrixA = Array.tabulate(mSize, kSize)((r, c) => (((r * 3 + c * 2) % 10) + 1) * 1.0f)
    val matrixB = Array.tabulate(kSize, nSize)((r, c) => (((r * 7 + c * 3) % 10) + 1) * 1.0f)

    val workPerThreadN = 4
    val workPerThreadM = 4

    val gold = Utils.matrixMatrixMultiply(matrixA, matrixB).flatten

    val n = SizeVar("N")
    val m = SizeVar("M")
    val k = SizeVar("K")

    val f = fun(
      ArrayType(ArrayType(Float, k), m),
      ArrayType(ArrayType(Float, n), k),
      (A, B) =>
        Map(Scatter(reorderStride(workPerThreadM))) o Join() o Map(TransposeW() o Join() o Map(TransposeW())) o
          MapGlb(fun( rowsA =>
            MapSeq(fun( colsB =>
              toGlobal(MapSeq(MapSeq(id))) o Join() o ReduceSeq(fun((acc, rowElemPair) =>
                MapSeq(fun(pair => MapSeq(add) $ Zip(Get(pair, 0), Get(pair, 1)))) o fun(rowElemPair => Zip(toPrivate(MapSeq(fun(a => MapSeq(fun(b => mult.apply(a, b))) $ Get(rowElemPair, 1)))) $ Get(rowElemPair, 0), acc)) $ rowElemPair
              ), toPrivate(MapSeq(MapSeq(id))) $ Value("0.0f", ArrayType(ArrayType(Float, workPerThreadM), workPerThreadN))) $ Zip(Transpose() $ rowsA, Transpose() $ colsB)
            )) o Split(workPerThreadM) o ReorderStride(workPerThreadM) o Transpose() $ B
          )) o Split(workPerThreadN) $ A
    )

    val (output: Array[Float], _) = Execute(mSize * nSize)(f, matrixA, matrixB)

    assertArrayEquals(gold, output, 0.0001f)
  }

  @Test def rectangularTiles(): Unit = {
    val mSize = 512
    val kSize = 512
    val nSize = 512
    val matrixA = Array.tabulate(mSize, kSize)((r, c) => (((r * 3 + c * 2) % 10) + 1) * 1.0f)
    val matrixB = Array.tabulate(kSize, nSize)((r, c) => (((r * 7 + c * 3) % 10) + 1) * 1.0f)

    val gold = Utils.matrixMatrixMultiply(matrixA, matrixB).flatten

    val N = SizeVar("N")
    val M = SizeVar("M")
    val K = SizeVar("K")

    val tileSizeM = 16
    val tileSizeN = tileSizeM
    val tileSizeK = 8
    val workPerThread = 2

    val f = fun(
      ArrayType(ArrayType(Float, M), K), // Transposed
      ArrayType(ArrayType(Float, N), K),
      (A, B) => {
        // Undo the tiling
        Untile() o
          MapWrg(0)(fun( aRows =>
            MapWrg(1)(fun( bCols =>
              Join() o Map(TransposeW()) o
                Join() o
                toGlobal(MapSeq(MapLcl(1)(MapLcl(0)(MapSeq(id))))) o

                // Multiply all necessary combinations of tiles
                ReduceSeq(fun( (acc, pairOfTiles) =>

                  fun(pairOfTiles =>
                     fun(partial =>
                      MapLcl(1)(fun(pairOfRows =>
                        MapLcl(0)(fun(x => MapSeq(add) $ Zip(Get(x, 0), Get(x, 1))
                        )) $ Zip(Get(pairOfRows, 0), Get(pairOfRows, 1))
                      )) $ Zip(acc, partial)
                    ) o

                      MapLcl(1)( fun(rowsA =>
                        MapLcl(0)( fun( colB =>
                          Join() o ReduceSeq(fun((acc, rowElemPair) =>
                            MapSeq(add) o fun(rowElemPair =>
                              Zip(
                                toPrivate(MapSeq(fun(a => mult.apply(a, Get(rowElemPair, 1)))
                                )) $ Get(rowElemPair, 0),
                                acc
                              )
                            ) $ rowElemPair
                          ), toPrivate(MapSeq(id)) $ Value("0.0f", ArrayType(Float, workPerThread))
                          ) $ Zip(Transpose() $ rowsA, colB)
                        )) o Transpose()$ Get(pairOfTiles, 1)
                      )) o Split(workPerThread) o Transpose() $ Get(pairOfTiles, 0)

                  ) o

                    // Copy tiles to local memory
                    fun(pairOfTiles =>
                      Tuple(
                         toLocal(MapLcl(1)(MapLcl(0)(id)))
                          $ Get(pairOfTiles, 0),
                         toLocal(MapLcl(1)(MapLcl(0)(id)))
                           $ Get(pairOfTiles, 1)
                      )) $ pairOfTiles
                )
                  , MapLcl(1)(MapLcl(0)(MapSeq(id))) $ Value(0.0f,
                    ArrayType(ArrayType(ArrayType(Float, workPerThread), tileSizeM), tileSizeN/workPerThread))
                ) $ Zip(aRows, bCols)

            )) o Transpose() o Tile(tileSizeK, tileSizeN) $ B
            // Tile the matrices
          )) o Transpose() o Tile(tileSizeK, tileSizeM) $ A // Transposed
      })

    val (output: Array[Float], _) = Execute(tileSizeM, tileSizeN / workPerThread,
      mSize, nSize / workPerThread, (true, true))(f, matrixA.transpose, matrixB)

    assertArrayEquals(gold, output, 0.0001f)
  }

  @Test def mmTiledAndBlockedBInnermost(): Unit = {
    val mSize = 512
    val kSize = 512
    val nSize = 512
    val matrixA = Array.tabulate(mSize, kSize)((r, c) => (((r * 3 + c * 2) % 10) + 1) * 1.0f)
    val matrixB = Array.tabulate(kSize, nSize)((r, c) => (((r * 7 + c * 3) % 10) + 1) * 1.0f)

    val gold = Utils.matrixMatrixMultiply(matrixA, matrixB).flatten

    val tileSizeM = 16
    val tileSizeN = tileSizeM
    val tileSizeK = 8
    val workPerThreadN = 2
    val workPerThreadM = 4

    val f = MatrixMultiplication.tiledAndBlockedBInnermost(tileSizeN, tileSizeM, tileSizeK, workPerThreadN, workPerThreadM)

    val (output: Array[Float], _) = Execute(tileSizeM / workPerThreadM, tileSizeN / workPerThreadN,
      mSize / workPerThreadM, nSize / workPerThreadN, (true, true))(f, matrixA.transpose, matrixB)

    assertArrayEquals(gold, output, 0.0001f)
  }

  @Test def gemmTiledAndBlockedBInnermost(): Unit = {
    val mSize = 512
    val kSize = 512
    val nSize = 512
    val matrixA = Array.tabulate(mSize, kSize)((r, c) => (((r * 3 + c * 2) % 10) + 1) * 1.0f)
    val matrixB = Array.tabulate(kSize, nSize)((r, c) => (((r * 7 + c * 3) % 10) + 1) * 1.0f)
    val matrixC = Array.tabulate(kSize, nSize)((r, c) => (((r * 7 + c * 3) % 10) + 1) * 1.0f)

    val alpha = 1.5f
    val beta = 2.5f

    val gold = Utils.matrixMatrixMultiply(matrixA, matrixB, matrixC, alpha, beta).flatten

    val tileSizeM = 16
    val tileSizeN = tileSizeM
    val tileSizeK = 8
    val workPerThreadN = 2
    val workPerThreadM = 4

    val f = GEMM.tiledAndBlockedBInnermost(tileSizeN, tileSizeM, tileSizeK, workPerThreadN, workPerThreadM)

    val (output: Array[Float], _) = Execute(tileSizeM / workPerThreadM, tileSizeN / workPerThreadN,
      mSize / workPerThreadM, nSize / workPerThreadN, (true, true))(f, matrixA.transpose, matrixB,
          matrixC, alpha, beta)

    assertArrayEquals(gold, output, 0.0001f)
  }

  @Test def mmVectorLoads(): Unit = {
    val mSize = 512
    val kSize = 512
    val nSize = 512
    val matrixA = Array.tabulate(mSize, kSize)((r, c) => (((r * 3 + c * 2) % 10) + 1) * 1.0f)
    val matrixB = Array.tabulate(kSize, nSize)((r, c) => (((r * 7 + c * 3) % 10) + 1) * 1.0f)

    val gold = Utils.matrixMatrixMultiply(matrixA, matrixB).flatten

    val tileSizeM = 16
    val tileSizeN = tileSizeM
    val tileSizeK = 8
    val workPerThreadN = 4
    val workPerThreadM = 4

    val f = MatrixMultiplication.vectorLoads(tileSizeN, tileSizeM, tileSizeK,
      workPerThreadN, workPerThreadM, 8)

    val (output: Array[Float], _) = Execute(tileSizeM / workPerThreadM, tileSizeN / workPerThreadN,
      mSize / workPerThreadM, nSize / workPerThreadN, (true, true))(f, matrixA.transpose, matrixB)

    assertArrayEquals(gold, output, 0.0001f)
  }

  @Test
  def hawaiiBest(): Unit = {
    val factory = (variables: Seq[ArithExpr]) => {
      val v_M_0 = variables(0)
      val v_K_1 = variables(1)
      val v_N_2 = variables(2)
      val v__3 = variables(3)
      val v__4 = variables(4)
      val v__5 = variables(5)
      val v__6 = variables(6)
      val v__7 = variables(7)

      fun(ArrayType(ArrayType(Float, v_M_0), v_K_1), ArrayType(ArrayType(Float, v_N_2), v_K_1),(p_0, p_1) => FunCall(Join(), FunCall(MapWrg(1)(fun((p_2) => FunCall(TransposeW(), FunCall(Join(), FunCall(MapWrg(0)(fun((p_3) => FunCall(TransposeW(), FunCall(Join(), FunCall(Map(fun((p_4) => FunCall(Map(fun((p_5) => FunCall(Scatter(ReorderWithStride((v__3) / (v__4))), p_5))), FunCall(TransposeW(), FunCall(Join(), FunCall(Map(fun((p_6) => FunCall(TransposeW(), FunCall(Map(fun((p_7) => FunCall(TransposeW(), p_7))), FunCall(TransposeW(), p_6))))), FunCall(TransposeW(), p_4))))))), FunCall(TransposeW(), FunCall(toGlobal(fun((p_8) => FunCall(MapSeq(fun((p_9) => FunCall(MapLcl(1)(fun((p_10) => FunCall(MapLcl(0)(fun((p_11) => FunCall(MapSeq(fun((p_12) => FunCall(MapSeq(fun((p_13) => FunCall(id, p_13))), p_12))), p_11))), p_10))), p_9))), p_8))), FunCall(ReduceSeq(fun((p_14, p_15) => FunCall(fun((p_16) => FunCall(MapLcl(1)(fun((p_17) => FunCall(Join(), FunCall(MapLcl(0)(fun((p_18) => FunCall(MapSeq(fun((p_19) => p_19)), FunCall(ReduceSeq(fun((p_20, p_21) => FunCall(fun((p_22) => FunCall(MapSeq(fun((p_23) => FunCall(MapSeq(fun((p_24) => FunCall(add, FunCall(Get(0), p_24), FunCall(mult, FunCall(Get(1), p_23), FunCall(Get(1), p_24))))), FunCall(Zip(2), FunCall(Get(0), p_23), FunCall(Get(1), p_22))))), FunCall(Zip(2), p_20, FunCall(Get(0), p_22)))), FunCall(toLocal(fun((p_25) => FunCall(fun((p_26) => FunCall(Tuple(2), FunCall(MapSeq(fun((p_27) => FunCall(id, p_27))), FunCall(Get(0), p_26)), FunCall(MapSeq(fun((p_28) => FunCall(id, p_28))), FunCall(Get(1), p_26)))), p_25))), FunCall(toPrivate(fun((p_29) => FunCall(fun((p_30) => FunCall(Tuple(2), FunCall(Get(0), p_30), FunCall(MapSeq(fun((p_31) => FunCall(id, p_31))), FunCall(Get(1), p_30)))), p_29))), p_21))))), FunCall(Get(0), p_18), FunCall(Zip(2), FunCall(Transpose(), FunCall(Get(1), p_17)), FunCall(Transpose(), FunCall(Get(1), p_18))))))), FunCall(Zip(2), FunCall(Get(0), p_17), FunCall(Split(v__4), FunCall(Gather(ReorderWithStride((v__3) / (v__4))), FunCall(Transpose(), FunCall(Get(1), p_16))))))))), FunCall(Zip(2), p_14, FunCall(Split(v__5), FunCall(Transpose(), FunCall(Get(0), p_16)))))), p_15))), FunCall(MapLcl(1)(fun((p_32) => FunCall(MapLcl(0)(fun((p_33) => FunCall(MapSeq(fun((p_34) => FunCall(MapSeq(fun((p_35) => FunCall(id, p_35))), p_34))), p_33))), p_32))), Value(0.0f, ArrayType(ArrayType(ArrayType(ArrayType(Float, v__4), v__5), (v__3*1/^(v__4))), (v__6*1/^(v__5))))), FunCall(Zip(2), p_2, p_3))))))))), FunCall(Transpose(), FunCall(Map(fun((p_36) => FunCall(Transpose(), p_36))), FunCall(Split(v__7), FunCall(Map(fun((p_37) => FunCall(Split(v__3), p_37))), p_1))))))))), FunCall(Transpose(), FunCall(Map(fun((p_38) => FunCall(Transpose(), p_38))), FunCall(Split(v__7), FunCall(Map(fun((p_39) => FunCall(Split(v__6), p_39))), p_0)))))))
    }

    val v_M_0 = SizeVar("M")
    val v_K_1 = SizeVar("K")
    val v_N_2 = SizeVar("N")

    val f = factory(Seq[ArithExpr](v_M_0, v_K_1, v_N_2,128,4,16, 64 ,256))

    println(f)

    val code = Compile(f, 32,4,1,1024/4,1024/16,1,
      scala.collection.immutable.Map[ArithExpr,ArithExpr](v_M_0 -> 1024, v_K_1 -> 1024, v_N_2 -> 1024))
  }

  @Test
  def hawaiiBestSgemm(): Unit = {
    val factory = (variables: Seq[ArithExpr]) => {
      val v_M_0 = variables(0)
      val v_K_1 = variables(1)
      val v_N_2 = variables(2)
      val v__3 = variables(3)
      val v__4 = variables(4)
      val v__5 = variables(5)
      val v__6 = variables(6)
      val v__7 = variables(7)

      fun(
        ArrayType(ArrayType(Float, v_M_0), v_K_1),
        ArrayType(ArrayType(Float, v_N_2), v_K_1),
        ArrayType(ArrayType(Float, v_N_2), v_M_0),
        Float,
        Float,
        (p_0, p_1, C,alpha,beta) =>
          FunCall(Join(),
            FunCall(MapWrg(1)(fun((p_2) =>
              FunCall(TransposeW(),
                FunCall(Join(),
                  FunCall(MapWrg(0)(fun((p_3) =>
                    FunCall(TransposeW(),
                      FunCall(Join(),
                        FunCall(Map(fun((p_4) =>
                          FunCall(Map(fun((p_5) =>
                            FunCall(Scatter(ReorderWithStride((v__3) / (v__4))), p_5))),
                            FunCall(TransposeW(),
                              FunCall(Join(),
                                FunCall(Map(fun((p_6) =>
                                  FunCall(TransposeW(),
                                    FunCall(Map(fun((p_7) =>
                                      FunCall(TransposeW(), p_7))),
                                      FunCall(TransposeW(), p_6))))),
                                  FunCall(TransposeW(), p_4))))))),
                          FunCall(TransposeW(),

                              FunCall(MapSeq(fun(x =>
                                MapLcl(1)(fun(y =>
                                  MapLcl(0)(fun( z =>
                                    MapSeq(fun(a =>
                                      MapSeq(fun(x =>
                                        toGlobal(add)(
                                          toPrivate(mult)(Get(x, 0), alpha),
                                          toPrivate(mult)(Get(x, 1), beta)
                                        )
                                      )) $ Zip(Get(a, 0), Get(a, 1))
                                    )) $ Zip(Get(z, 0), Transpose() $ Get(z, 1))
                                  )) $ Zip(Get(y, 0), Split(v__4) o ReorderStride((v__3) / (v__4)) o Transpose() $ Get(y, 1))
                                )) $ Zip(x, Split(v__5) $ Get(p_3, 1))
                              )),
                              FunCall(ReduceSeq(fun((p_14, p_15) =>
                                FunCall(fun((p_16) =>
                                  FunCall(MapLcl(1)(fun((p_17) =>
                                    FunCall(Join(),
                                      FunCall(MapLcl(0)(fun((p_18) =>
                                        FunCall(MapSeq(fun((p_19) => p_19)),
                                          FunCall(ReduceSeq(fun((p_20, p_21) =>
                                            FunCall(fun((p_22) =>
                                              FunCall(MapSeq(fun((p_23) =>
                                                FunCall(MapSeq(fun((p_24) =>
                                                  FunCall(add,
                                                    FunCall(Get(0), p_24),
                                                    FunCall(mult,
                                                      FunCall(Get(1), p_23),
                                                      FunCall(Get(1), p_24))))),
                                                  FunCall(Zip(2),
                                                    FunCall(Get(0), p_23),
                                                    FunCall(Get(1), p_22))))),
                                                FunCall(Zip(2), p_20,
                                                  FunCall(Get(0), p_22)))),
                                              FunCall(toLocal(fun((p_25) =>
                                                FunCall(fun((p_26) =>
                                                  FunCall(Tuple(2),
                                                    FunCall(MapSeq(fun((p_27) =>
                                                      FunCall(id, p_27))),
                                                      FunCall(Get(0), p_26)),
                                                    FunCall(MapSeq(fun((p_28) =>
                                                      FunCall(id, p_28))),
                                                      FunCall(Get(1), p_26)))), p_25))),
                                                FunCall(toPrivate(fun((p_29) =>
                                                  FunCall(fun((p_30) =>
                                                    FunCall(Tuple(2),
                                                      FunCall(Get(0), p_30),
                                                      FunCall(MapSeq(fun((p_31) =>
                                                        FunCall(id, p_31))),
                                                        FunCall(Get(1), p_30)))), p_29))), p_21))))),
                                            FunCall(Get(0), p_18),
                                            FunCall(Zip(2),
                                              FunCall(Transpose(),
                                                FunCall(Get(1), p_17)),
                                              FunCall(Transpose(),
                                                FunCall(Get(1), p_18))))))),
                                        FunCall(Zip(2),
                                          FunCall(Get(0), p_17),
                                          FunCall(Split(v__4),
                                            FunCall(Gather(ReorderWithStride((v__3) / (v__4))),
                                              FunCall(Transpose(),
                                                FunCall(Get(1), p_16))))))))),
                                    FunCall(Zip(2), p_14,
                                      FunCall(Split(v__5),
                                        FunCall(Transpose(),
                                          FunCall(Get(0), p_16)))))), p_15))),
                                FunCall(MapLcl(1)(fun((p_32) =>
                                  FunCall(MapLcl(0)(fun((p_33) =>
                                    FunCall(MapSeq(fun((p_34) =>
                                      FunCall(MapSeq(fun((p_35) =>
                                        FunCall(id, p_35))), p_34))), p_33))), p_32))),
                                  Value(0.0f, ArrayType(ArrayType(ArrayType(ArrayType(Float, v__4), v__5), (v__3*1/^(v__4))), (v__6*1/^(v__5))))),
                                FunCall(Zip(2), Get(p_2, 0), Get(p_3, 0)))))))))),
                    Zip(FunCall(Transpose(),
                      FunCall(Map(fun((p_36) =>
                        FunCall(Transpose(), p_36))),
                        FunCall(Split(v__7),
                          FunCall(Map(fun((p_37) =>
                            FunCall(Split(v__3), p_37))), p_1)))), Get(p_2,1))))))),
              Zip(FunCall(Transpose(),
                FunCall(Map(fun((p_38) => FunCall(Transpose(), p_38))),
                  FunCall(Split(v__7),
                    FunCall(Map(fun((p_39) =>
                      FunCall(Split(v__6), p_39))), p_0)))), Tile(v__6, v__3) $ C))))
    }

    val v_M_0 = SizeVar("M")
    val v_K_1 = SizeVar("K")
    val v_N_2 = SizeVar("N")

    val f = factory(Seq[ArithExpr](v_M_0, v_K_1, v_N_2,128,4,16, 64 ,256))

    val code = Compile(f, 32,4,1,1024/4,1024/16,1,
      scala.collection.immutable.Map[ArithExpr,ArithExpr](v_M_0 -> 1024, v_K_1 -> 1024, v_N_2 -> 1024))
  }

  @Test
  def keplerBest(): Unit = {
    val factory = (variables: Seq[ArithExpr]) => {
      val v_M_0 = variables(0)
      val v_K_1 = variables(1)
      val v_N_2 = variables(2)
      val v__3 = variables(3)
      val v__4 = variables(4)
      val v__5 = variables(5)
      val v__6 = variables(6)
      val v__7 = variables(7)

      fun(ArrayType(ArrayType(Float, v_M_0), v_K_1), ArrayType(ArrayType(Float, v_N_2), v_K_1), (p_0, p_1) => FunCall(Join(), FunCall(MapWrg(1)(fun((p_2) => FunCall(TransposeW(), FunCall(Join(), FunCall(MapWrg(0)(fun((p_3) => FunCall(TransposeW(), FunCall(Join(), FunCall(Map(fun((p_4) => FunCall(Map(fun((p_5) => FunCall(Scatter(ReorderWithStride((v__3) / (v__4))), p_5))), FunCall(TransposeW(), FunCall(Join(), FunCall(Map(fun((p_6) => FunCall(TransposeW(), FunCall(Map(fun((p_7) => FunCall(TransposeW(), p_7))), FunCall(TransposeW(), p_6))))), FunCall(TransposeW(), p_4))))))), FunCall(TransposeW(), FunCall(toGlobal(fun((p_8) => FunCall(MapSeq(fun((p_9) => FunCall(MapLcl(1)(fun((p_10) => FunCall(MapLcl(0)(fun((p_11) => FunCall(MapSeq(fun((p_12) => FunCall(MapSeq(fun((p_13) => FunCall(id, p_13))), p_12))), p_11))), p_10))), p_9))), p_8))), FunCall(ReduceSeq(fun((p_14, p_15) => FunCall(fun((p_16) => FunCall(MapLcl(1)(fun((p_17) => FunCall(Join(), FunCall(MapLcl(0)(fun((p_18) => FunCall(MapSeq(fun((p_19) => p_19)), FunCall(ReduceSeq(fun((p_20, p_21) => FunCall(fun((p_22) => FunCall(MapSeq(fun((p_23) => FunCall(MapSeq(fun((p_24) => FunCall(add, FunCall(Get(0), p_24), FunCall(mult, FunCall(Get(1), p_23), FunCall(Get(1), p_24))))), FunCall(Zip(2), FunCall(Get(0), p_23), FunCall(Get(1), p_22))))), FunCall(Zip(2), p_20, FunCall(Get(0), p_22)))), FunCall(toPrivate(fun((p_25) => FunCall(fun((p_26) => FunCall(Tuple(2), FunCall(MapSeq(fun((p_27) => FunCall(id, p_27))), FunCall(Get(0), p_26)), FunCall(MapSeq(fun((p_28) => FunCall(id, p_28))), FunCall(Get(1), p_26)))), p_25))), p_21)))), FunCall(Get(0), p_18), FunCall(Zip(2), FunCall(Transpose(), FunCall(Get(1), p_17)), FunCall(Transpose(), FunCall(Get(1), p_18))))))), FunCall(Zip(2), FunCall(Get(0), p_17), FunCall(Split(v__4), FunCall(Gather(ReorderWithStride((v__3) / (v__4))), FunCall(Transpose(), FunCall(Get(1), p_16))))))))), FunCall(Zip(2), p_14, FunCall(Split(v__5), FunCall(Transpose(), FunCall(Get(0), p_16)))))), FunCall(toLocal(fun((p_29) => FunCall(fun((p_30) => FunCall(Unzip(), FunCall(MapLcl(1)(fun((p_31) => FunCall(Tuple(2), FunCall(MapLcl(0)(fun((p_32) => FunCall(id, p_32))), FunCall(Get(0), p_31)), FunCall(MapLcl(0)(fun((p_33) => FunCall(id, p_33))), FunCall(Get(1), p_31))))), FunCall(Zip(2), FunCall(Get(0), p_30), FunCall(Get(1), p_30))))), p_29))), p_15)))), FunCall(MapLcl(1)(fun((p_34) => FunCall(MapLcl(0)(fun((p_35) => FunCall(MapSeq(fun((p_36) => FunCall(MapSeq(fun((p_37) => FunCall(id, p_37))), p_36))), p_35))), p_34))), Value(0.0f, ArrayType(ArrayType(ArrayType(ArrayType(Float, v__4), v__5), (v__3 * 1 /^ (v__4))), (v__6 * 1 /^ (v__5))))), FunCall(Zip(2), p_2, p_3))))))))), FunCall(Transpose(), FunCall(Map(fun((p_38) => FunCall(Transpose(), p_38))), FunCall(Split(v__7), FunCall(Map(fun((p_39) => FunCall(Split(v__3), p_39))), p_1))))))))), FunCall(Transpose(), FunCall(Map(fun((p_40) => FunCall(Transpose(), p_40))), FunCall(Split(v__7), FunCall(Map(fun((p_41) => FunCall(Split(v__6), p_41))), p_0)))))))
    }

    val v_M_0 = SizeVar("M")
    val v_K_1 = SizeVar("K")
    val v_N_2 = SizeVar("N")

    val f = factory(Seq[ArithExpr](v_M_0, v_K_1, v_N_2,128,4,8, 64 ,8))

    val code = Compile(f, 32,8,1,1024/4,1024/8,1,
      scala.collection.immutable.Map[ArithExpr,ArithExpr](v_M_0 -> 1024, v_K_1 -> 1024, v_N_2 -> 1024))

  }

  @Test
  def keplerBestSgemm(): Unit = {
    val factory = (variables: Seq[ArithExpr]) => {
      val v_M_0 = variables(0)
      val v_K_1 = variables(1)
      val v_N_2 = variables(2)
      val v__3 = variables(3)
      val v__4 = variables(4)
      val v__5 = variables(5)
      val v__6 = variables(6)
      val v__7 = variables(7)

      fun(
        ArrayType(ArrayType(Float, v_M_0), v_K_1),
        ArrayType(ArrayType(Float, v_N_2), v_K_1),
        ArrayType(ArrayType(Float, v_N_2), v_M_0),
        Float,
        Float,
        (p_0, p_1, C,alpha,beta) =>
          FunCall(Join(),
            FunCall(MapWrg(1)(fun((p_2) =>
              FunCall(TransposeW(),
                FunCall(Join(),
                  FunCall(MapWrg(0)(fun((p_3) =>
                    FunCall(TransposeW(),
                      FunCall(Join(),
                        FunCall(Map(fun((p_4)
                        => FunCall(Map(fun((p_5)
                          => FunCall(Scatter(ReorderWithStride((v__3) / (v__4))), p_5))),
                            FunCall(TransposeW(),
                              FunCall(Join(),
                                FunCall(Map(fun((p_6) =>
                                  FunCall(TransposeW(),
                                    FunCall(Map(fun((p_7) =>
                                      FunCall(TransposeW(), p_7)
                                    )), FunCall(TransposeW(), p_6))
                                  ))), FunCall(TransposeW(), p_4))))
                          ))
                        ),
                            FunCall(MapSeq(fun(x =>
                              MapLcl(1)(fun(y =>
                                MapLcl(0)(fun( z =>
                                  MapSeq(fun(a =>
                                    MapSeq(fun(x =>
                                      toGlobal(add)(
                                        toPrivate(mult)(Get(x, 0), alpha),
                                        toPrivate(mult)(Get(x, 1), beta)
                                      )
                                    )) $ Zip(Get(a, 0), Get(a, 1))
                                  )) $ Zip(Get(z, 0), Transpose() $ Get(z, 1))
                                )) $ Zip(Get(y, 0), Split(v__4) o ReorderStride((v__3) / (v__4)) o Transpose() $ Get(y, 1))
                              )) $ Zip(x, Split(v__5) $ Get(p_3, 1))
                            )),
                            FunCall(ReduceSeq(fun((p_14, p_15) =>
                            FunCall(fun((p_16) =>
                              FunCall(MapLcl(1)(fun((p_17) =>
                                FunCall(Join(),
                                  FunCall(MapLcl(0)(fun((p_18) =>
                                    FunCall(MapSeq(fun((p_19) => p_19)),
                                      FunCall(ReduceSeq(fun((p_20, p_21) =>
                                        FunCall(fun((p_22) =>
                                          FunCall(MapSeq(fun((p_23) =>
                                            FunCall(MapSeq(fun((p_24) =>
                                              FunCall(add,
                                                FunCall(Get(0), p_24),
                                                FunCall(mult, FunCall(Get(1), p_23),
                                                  FunCall(Get(1), p_24))
                                              ))
                                            ), FunCall(Zip(2),
                                              FunCall(Get(0), p_23),
                                              FunCall(Get(1), p_22))))),
                                            FunCall(Zip(2), p_20,
                                              FunCall(Get(0), p_22)))),
                                          FunCall(toPrivate(fun((p_25) =>
                                            FunCall(fun((p_26) =>
                                              FunCall(Tuple(2),
                                                FunCall(MapSeq(fun((p_27) =>
                                                  FunCall(id, p_27))),
                                                  FunCall(Get(0), p_26)),
                                                FunCall(MapSeq(fun((p_28) =>
                                                  FunCall(id, p_28))),
                                                  FunCall(Get(1), p_26)))), p_25))), p_21)))),
                                        FunCall(Get(0), p_18),
                                        FunCall(Zip(2),
                                          FunCall(Transpose(),
                                            FunCall(Get(1), p_17)),
                                          FunCall(Transpose(),
                                            FunCall(Get(1), p_18))))))),
                                    FunCall(Zip(2),
                                      FunCall(Get(0), p_17),
                                      FunCall(Split(v__4),
                                        FunCall(Gather(ReorderWithStride((v__3) / (v__4))),
                                          FunCall(Transpose(),
                                            FunCall(Get(1), p_16))))))))),
                                FunCall(Zip(2), p_14,
                                  FunCall(Split(v__5),
                                    FunCall(Transpose(),
                                      FunCall(Get(0), p_16)))))),
                              FunCall(toLocal(fun((p_29) =>
                                FunCall(fun((p_30) =>
                                  FunCall(Unzip(),
                                    FunCall(MapLcl(1)(fun((p_31) =>
                                      FunCall(Tuple(2),
                                        FunCall(MapLcl(0)(fun((p_32) =>
                                          FunCall(id, p_32))),
                                          FunCall(Get(0), p_31)),
                                        FunCall(MapLcl(0)(fun((p_33) =>
                                          FunCall(id, p_33))),
                                          FunCall(Get(1), p_31))))),
                                      FunCall(Zip(2),
                                        FunCall(Get(0), p_30),
                                        FunCall(Get(1), p_30))))), p_29))), p_15)))),
                            FunCall(MapLcl(1)(fun((p_34) =>
                              FunCall(MapLcl(0)(fun((p_35) =>
                                FunCall(MapSeq(fun((p_36) =>
                                  FunCall(MapSeq(fun((p_37) =>
                                    FunCall(id, p_37))), p_36))), p_35))), p_34))),
                              Value(0.0f, ArrayType(ArrayType(ArrayType(ArrayType(Float, v__4), v__5), (v__3 * 1 /^ (v__4))), (v__6 * 1 /^ (v__5))))),
                            FunCall(Zip(2), Get(p_2, 0), Get(p_3, 0))))))))),
                    Zip(FunCall(Transpose(),
                      FunCall(Map(fun((p_38) =>
                        FunCall(Transpose(), p_38))),
                        FunCall(Split(v__7),
                          FunCall(Map(fun((p_39) =>
                            FunCall(Split(v__3), p_39))), p_1)))), Get(p_2, 1))))))),
              Zip(FunCall(Transpose(),
                FunCall(Map(fun((p_40) =>
                  FunCall(Transpose(), p_40))),
                  FunCall(Split(v__7),
                    FunCall(Map(fun((p_41) =>
                      FunCall(Split(v__6), p_41))), p_0)))), Tile(v__6, v__3) $ C))))
    }

    val v_M_0 = SizeVar("M")
    val v_K_1 = SizeVar("K")
    val v_N_2 = SizeVar("N")

    val f = factory(Seq[ArithExpr](v_M_0, v_K_1, v_N_2,128,4,8, 64 ,8))

    val code = Compile(f, 32,8,1,1024/4,1024/8,1,
      scala.collection.immutable.Map[ArithExpr,ArithExpr](v_M_0 -> 1024, v_K_1 -> 1024, v_N_2 -> 1024))

  }

  @Test def mmTiledAndBlockedAInnermost(): Unit = {
    val mSize = 16
    val kSize = 16
    val nSize = 16
    val matrixA = Array.tabulate(mSize, kSize)((r, c) => (((r * 3 + c * 2) % 10) + 1) * 1.0f)
    val matrixB = Array.tabulate(kSize, nSize)((r, c) => (((r * 7 + c * 3) % 10) + 1) * 1.0f)

    val gold = Utils.matrixMatrixMultiply(matrixA, matrixB).flatten

    val N = SizeVar("N")
    val M = SizeVar("M")
    val K = SizeVar("K")

    val tileSizeM = 8
    val tileSizeN = tileSizeM
    val tileSizeK = 4
    val workPerThreadN = 2
    val workPerThreadM = 2

    val f = fun(
      ArrayType(ArrayType(Float, M), K), // Transposed
      ArrayType(ArrayType(Float, N), K),
      (A, B) => {
        // Undo the tiling
        Untile() o
          MapWrg(0)(fun( aRows =>
            MapWrg(1)(fun( bCols =>

              Map(Scatter(reorderStride(tileSizeM/workPerThreadM))) o Join() o
                Map(TransposeW() o Join()) o

                toGlobal(MapLcl(1)(MapLcl(0)(MapSeq(MapSeq(id))))) o

                Join() o

                // Multiply all necessary combinations of tiles
                ReduceSeq(fun( (acc, pairOfTiles) =>

                  fun(pairOfTiles =>

                      MapLcl(1)( fun(rowsA =>
                        MapLcl(0)( fun( colsB =>
                          Join() o ReduceSeq(fun((acc, rowElemPair) =>
                            MapSeq(fun(pair => MapSeq(add) $ Zip(Get(pair, 0), Get(pair, 1)))) o
                              fun(rowElemPair =>
                                Zip(
                                  Join() o toPrivate(MapSeq(MapSeq(
                                    fun(aArray => MapSeq(fun(b =>
                                      mult.apply(aArray, b)
                                    )) $ Get(rowElemPair, 0))) o toPrivate(MapSeq(id))
                                  )) o Split(1) $ Get(rowElemPair, 1),
                                  acc
                                )
                              ) o fun(rowElemPair =>
                              Tuple(
                                toPrivate(MapSeq(id)) $ Get(rowElemPair, 0),
                                Get(rowElemPair, 1)
                              )) $ rowElemPair
                          ), Get(colsB, 1)
                          ) $ Zip(Transpose() $ Get(rowsA, 0), Transpose() $ Get(colsB, 0))

                        )) $ Zip(Split(workPerThreadM) o ReorderStride(tileSizeM/workPerThreadM) o Transpose() $ Get(pairOfTiles, 1), Get(rowsA, 1))
                      ))  $ Zip(Split(workPerThreadN) o Transpose() $ Get(pairOfTiles, 0), acc)

                  ) o

                    // Copy tiles to local memory
                    fun(pairOfTiles =>
                      Tuple(
                        toLocal(MapLcl(1)(MapLcl(0)(id)))
                          $ Get(pairOfTiles, 0),
                        toLocal(MapLcl(1)(MapLcl(0)(id)))
                          $ Get(pairOfTiles, 1)
                      )) $ pairOfTiles
                )
                  , MapLcl(1)(MapLcl(0)(MapSeq(MapSeq(id)))) $ Value(0.0f,
                    ArrayType(ArrayType(ArrayType(ArrayType(Float, workPerThreadM), workPerThreadN), tileSizeM/workPerThreadM), tileSizeN/workPerThreadN))
                ) $ Zip(aRows, bCols)

              // Tile the matrices
            )) o Transpose() o Tile(tileSizeK, tileSizeN) $ B
          )) o Transpose() o Tile(tileSizeK, tileSizeM) $ A
      })

    val (output: Array[Float], _) = Execute(tileSizeM / workPerThreadM, tileSizeN / workPerThreadN,
      mSize / workPerThreadM, nSize / workPerThreadN, (true, true))(f, matrixA.transpose, matrixB)

    assertArrayEquals(gold, output, 0.0001f)
  }

  @Test def mmReuseB(): Unit = {
    val mSize = 16
    val kSize = 16
    val nSize = 16
    val matrixA = Array.tabulate(mSize, kSize)((r, c) => (((r * 3 + c * 2) % 10) + 1) * 1.0f)
    val matrixB = Array.tabulate(kSize, nSize)((r, c) => (((r * 7 + c * 3) % 10) + 1) * 1.0f)

    val tileSize = 4

    val gold = Utils.matrixMatrixMultiply(matrixA, matrixB).flatten

    val n = SizeVar("N")
    val m = SizeVar("M")
    val k = SizeVar("K")

    val f = fun(
      ArrayType(ArrayType(Float, k), m),
      ArrayType(ArrayType(Float, n), k),
      (A, B) =>
        Join() o Map(TransposeW()) o
          MapGlb(fun( rowsA =>
            MapSeq(fun( colB =>
              toGlobal(MapSeq(id)) o Join() o ReduceSeq(fun((acc, rowElemPair) =>
                MapSeq(add) o fun(rowElemPair => Zip(toPrivate(MapSeq(fun(a => mult.apply(a, Get(rowElemPair, 1))))) $ Get(rowElemPair, 0), acc)) $ rowElemPair
              ), toPrivate(MapSeq(id)) $ Value("0.0f", ArrayType(Float, tileSize))) $ Zip(Transpose() $ rowsA, colB)
            )) o Transpose() $ B
          )) o Split(tileSize) $ A
    )

    val (output: Array[Float], _) = Execute(mSize * nSize)(f, matrixA, matrixB)

    assertArrayEquals(gold, output, 0.0001f)
  }

  @Test def mmReuseA(): Unit = {
    val mSize = 16
    val kSize = 16
    val nSize = 16
    val matrixA = Array.tabulate(mSize, kSize)((r, c) => (((r * 3 + c * 2) % 10) + 1) * 1.0f)
    val matrixB = Array.tabulate(kSize, nSize)((r, c) => (((r * 7 + c * 3) % 10) + 1) * 1.0f)

    val tileSize = 4

    val gold = Utils.matrixMatrixMultiply(matrixA, matrixB).flatten

    val n = SizeVar("N")
    val m = SizeVar("M")
    val k = SizeVar("K")

    val f = fun(
      ArrayType(ArrayType(Float, k), m),
      ArrayType(ArrayType(Float, n), k),
      (A, B) =>
        Map(Join()) o
          MapGlb(fun(rowA => MapSeq( fun(colsB =>
            toGlobal(MapSeq(id)) o Join() o ReduceSeq(fun((acc, elemRowPair) =>
              MapSeq(add) o fun(elemRowPair =>
                Zip(toPrivate(MapSeq(fun(a => mult.apply(a, Get(elemRowPair, 0))))) $ Get(elemRowPair, 1), acc)
              ) $ elemRowPair
            ), toPrivate(MapSeq(id)) $ Value("0.0f", ArrayType(Float, tileSize))) $ Zip(rowA, colsB)
          )) o Map(Transpose()) o Split(tileSize) o Transpose() $ B
        )) $ A
    )

    val (output: Array[Float], _) = Execute(mSize * nSize)(f, matrixA, matrixB)

    assertArrayEquals(gold, output, 0.0001f)
  }

  @Test def mmTiledReuseB(): Unit = {
    val mSize = 16
    val kSize = 16
    val nSize = 16
    val matrixA = Array.tabulate(mSize, kSize)((r, c) => (((r * 3 + c * 2) % 10) + 1) * 1.0f)
    val matrixB = Array.tabulate(kSize, nSize)((r, c) => (((r * 7 + c * 3) % 10) + 1) * 1.0f)

    val tileSize = 8
    val blockSize = 4

    val gold = Utils.matrixMatrixMultiply(matrixA, matrixB).flatten

    val f = MatrixMultiplication.moreWorkPerThread(tileSize, blockSize)

    val (output: Array[Float], _) = Execute(tileSize, tileSize / blockSize,
      mSize, nSize / blockSize, (true, true))(f, matrixA, matrixB)

    assertArrayEquals(gold, output, 0.0001f)
  }

  @Test def mmTiledReuseA(): Unit = {
    val mSize = 16
    val kSize = 16
    val nSize = 16
    val matrixA = Array.tabulate(mSize, kSize)((r, c) => (((r * 3 + c * 2) % 10) + 1) * 1.0f)
    val matrixB = Array.tabulate(kSize, nSize)((r, c) => (((r * 7 + c * 3) % 10) + 1) * 1.0f)

    val tileSize = 4
    val blockSize = 2

    val gold = Utils.matrixMatrixMultiply(matrixA, matrixB).flatten

    val N = SizeVar("N")
    val M = SizeVar("M")
    val K = SizeVar("K")

    val f = fun(
      ArrayType(ArrayType(Float, K), M),
      ArrayType(ArrayType(Float, N), K),
      (A, B) => {
        // Undo the tiling
        Untile() o
          MapWrg(1)(fun( aRows =>
            MapWrg(0)(fun( bCols =>


              toGlobal(MapLcl(1)(
                Scatter(reorderStride(blockSize)) o MapLcl(0)(id)
              )) o
                Join() o

                // Multiply all necessary combinations of tiles
                ReduceSeq(fun( (acc, pairOfTiles) =>

                  fun(pairOfTiles =>
                     fun(partial =>
                      MapLcl(1)(fun(pairOfRows =>
                        MapLcl(0)(add) $ Zip(Get(pairOfRows, 0), Get(pairOfRows, 1))
                      )) $ Zip(acc, partial)
                    ) o

                      Map(Join()) o // This reorders elements and needs the scatter at the end
                      MapLcl(1)( fun(rowA =>
                        MapLcl(0)( fun( colsB =>
                          Join() o ReduceSeq(fun((acc, elemRowPair) =>
                            MapSeq(add) o fun(elemRowPair =>
                              Zip(
                                toPrivate(MapSeq(fun(a => mult.apply(a, Get(elemRowPair, 0))))) $ Get(elemRowPair, 1),
                                acc
                              )) $ elemRowPair
                            ), toPrivate(MapSeq(id)) $ Value("0.0f", ArrayType(Float, blockSize))
                          ) $ Zip(rowA, colsB)

                          )) o Map(Transpose()) o Split(blockSize) o Transpose() $ Get(pairOfTiles, 1)
                      )) $ Get(pairOfTiles, 0)
                  ) o

                    // Copy tiles to local memory
                    fun(pairOfTiles =>
                      Tuple(
                         toLocal(MapLcl(1)(MapLcl(0)(id))) $ Get(pairOfTiles, 0),
                         toLocal(MapLcl(1)(MapLcl(0)(id))) $ Get(pairOfTiles, 1)
                      )) $ pairOfTiles
                )
                  , MapLcl(1)(MapLcl(0)(id)) $ Value(0.0f, ArrayType(ArrayType(Float, tileSize), tileSize))
                ) $ Zip(aRows, bCols)

            )) o Transpose() o Tile(tileSize) $ B
            // Tile the matrices
          )) o Tile(tileSize) $ A
      })

    val (output: Array[Float], _) = Execute(tileSize/blockSize, tileSize, mSize/blockSize, nSize, (true, false))(f, matrixA, matrixB)

    assertArrayEquals(gold, output, 0.0001f)
  }

  @Test def tiledMatrixMultiply(): Unit = {
    val mSize = 16
    val kSize = 16
    val nSize = 16
    val matrixA = Array.tabulate(mSize, kSize)((r, c) => (((r * 3 + c * 2) % 10) + 1) * 1.0f)
    val matrixB = Array.tabulate(kSize, nSize)((r, c) => (((r * 7 + c * 3) % 10) + 1) * 1.0f)

    val tileSize = 4

    val gold = Utils.matrixMatrixMultiply(matrixA, matrixB).flatten

    val n = SizeVar("N")
    val m = SizeVar("M")
    val k = SizeVar("K")

    val f = fun(
      ArrayType(ArrayType(Float, k), m),
      ArrayType(ArrayType(Float, k), n),
      (A, B) => {
        // Undo the tiling
        Untile() o
        MapWrg(0)(fun( aRows =>
          MapWrg(1)(fun( bCols =>

            // Reduce the partial results (matrices), so that the reduce is innermost
             MapLcl(0)(Join() o MapLcl(1)(toGlobal(MapSeq(id)) o ReduceSeq(add, 0.0f) o Join()) o Transpose()) o Transpose() o

            // Multiply all necessary combinations of tiles
            MapSeq(fun( tiles =>
               MapLcl(0)( fun(aTile =>
                MapLcl(1)( fun( bTile =>
                  toGlobal(MapSeq(id)) o ReduceSeq(fun((acc, y) => multAndSumUp.apply(acc, Get(y, 0), Get(y, 1))), 0.0f) $ Zip(aTile, bTile)
                )) $ Get(tiles, 1)
              )) $ Get(tiles, 0)
            )) $ Zip(aRows, bCols)

          // Tile the matrices
          )) o Tile(tileSize) $ B
        )) o Tile(tileSize) $ A
      })

    val (output: Array[Float], runtime) = Execute(4, 4, mSize, nSize, (false, false))(f, matrixA, matrixB.transpose)

    println("output.size = " + output.length)
    println("output(0) = " + output(0))
    println("runtime = " + runtime)

    assertArrayEquals(gold, output, 0.0001f)
  }

  @Test def tiledMatrixMultiply2(): Unit = {
    // Basic tiled matrix multiply without local memory
    val mSize = 16
    val kSize = 16
    val nSize = 16
    val matrixA = Array.tabulate(mSize, kSize)((r, c) => (((r * 3 + c * 2) % 10) + 1) * 1.0f)
    val matrixB = Array.tabulate(kSize, nSize)((r, c) => (((r * 7 + c * 3) % 10) + 1) * 1.0f)

    val tileSize = 4

    val gold = Utils.matrixMatrixMultiply(matrixA, matrixB).flatten

    val N = SizeVar("N")
    val M = SizeVar("M")
    val K = SizeVar("K")

    val f = fun(
      ArrayType(ArrayType(Float, K), M),
      ArrayType(ArrayType(Float, K), N),
      (A, B) => {
        // Undo the tiling
        Untile() o
          MapWrg(0)(fun( aRows =>
            MapWrg(1)(fun( bCols =>

              toGlobal(MapLcl(1)(MapLcl(0)(id))) o
                Join() o

                // Multiply all necessary combinations of tiles
                ReduceSeq(fun( (acc, pairOfTiles) =>

                  fun(pairOfTiles =>
                     fun(partial => MapLcl(1)(fun(pairOfRows => MapLcl(0)(add) $ Zip(Get(pairOfRows, 0), Get(pairOfRows, 1)))) $ Zip(acc, partial) ) o
                      Map(Join()) o
                      MapLcl(1)( fun(rowA =>
                        MapLcl(0)( fun( colB =>
                          toGlobal(MapSeq(id) o ReduceSeq(fun((acc, y) => multAndSumUp.apply(acc, Get(y, 0), Get(y, 1))), 0.0f)) $ Zip(rowA, colB)
                        )) $ Get(pairOfTiles, 1)
                      )) $ Get(pairOfTiles, 0)
                  ) $ pairOfTiles
                )
                  , toGlobal(MapLcl(1)(MapLcl(0)(id))) $ Value(0.0f, ArrayType(ArrayType(Float, tileSize), tileSize))
                ) $ Zip(aRows, bCols)

              // Tile the matrices
            )) o Tile(tileSize) $ B
          )) o Tile(tileSize) $ A
      })

    val (output: Array[Float], _) = Execute(mSize, nSize)(f, matrixA, matrixB.transpose)

    assertArrayEquals(gold, output, 0.0001f)
  }

  @Test def tiledMatrixMultiplyLocalMemory(): Unit = {
    val mSize = 16
    val kSize = 16
    val nSize = 16
    val matrixA = Array.tabulate(mSize, kSize)((r, c) => (((r * 3 + c * 2) % 10) + 1) * 1.0f)
    val matrixB = Array.tabulate(kSize, nSize)((r, c) => (((r * 7 + c * 3) % 10) + 1) * 1.0f)

    val tileSize = 4

    val gold = Utils.matrixMatrixMultiply(matrixA, matrixB).flatten

    val n = SizeVar("N")
    val m = SizeVar("M")
    val k = SizeVar("K")

    val f = fun(
      ArrayType(ArrayType(Float, k), m),
      ArrayType(ArrayType(Float, k), n),
      (A, B) => {
        // Undo the tiling
        Untile() o
          MapWrg(0)(fun( aRows =>
            MapWrg(1)(fun( bCols =>

              Map(Join()) o
              // Reduce the partial results (matrices), so that the reduce is innermost
               toGlobal(MapLcl(0)(MapLcl(1)(toGlobal(MapSeq(id)) o ReduceSeq(add, 0.0f)))) o
              Map(Map(Join()) o Transpose()) o Transpose() o

                // Multiply all necessary combinations of tiles
                toLocal(MapSeq(fun( tiles =>
                   MapLcl(0)( fun(aTile =>
                    MapLcl(1)( fun( bTile =>
                      toLocal(MapSeq(id)) o ReduceSeq(fun((acc, y) =>
                        multAndSumUp.apply(acc, Get(y, 0), Get(y, 1))), 0.0f) $ Zip(aTile, bTile)
                    )) $ Get(tiles, 1)
                  )) $ Get(tiles, 0)
                ) o

                // Copy tiles to local memory
                fun(tiles =>
                  Tuple(
                     toLocal(MapLcl(0)(MapLcl(1)(id))) $ Get(tiles, 0),
                     toLocal(MapLcl(0)(MapLcl(1)(id))) $ Get(tiles, 1)
                  ))
                )) $ Zip(aRows, bCols)

              // Tile the matrices
            )) o Tile(tileSize) $ B
          )) o Tile(tileSize) $ A
      })

    val (output: Array[Float], runtime) = Execute(4, 4, mSize, nSize, (true, true))(f, matrixA, matrixB.transpose)

    println("output.size = " + output.length)
    println("output(0) = " + output(0))
    println("runtime = " + runtime)

    assertArrayEquals(gold, output, 0.0001f)
  }

  @Test def tiledMatrixMultiplyLocalMemory2(): Unit = {
    val mSize = 16
    val kSize = 16
    val nSize = 16
    val matrixA = Array.tabulate(mSize, kSize)((r, c) => (((r * 3 + c * 2) % 10) + 1) * 1.0f)
    val matrixB = Array.tabulate(kSize, nSize)((r, c) => (((r * 7 + c * 3) % 10) + 1) * 1.0f)

    val tileSize = 4

    val gold = Utils.matrixMatrixMultiply(matrixA, matrixB).flatten

    val N = SizeVar("N")
    val M = SizeVar("M")
    val K = SizeVar("K")

    val f = fun(
      ArrayType(ArrayType(Float, K), M),
      ArrayType(ArrayType(Float, K), N),
      (A, B) => {
        // Undo the tiling
        Untile() o
          MapWrg(0)(fun( aRows =>
            MapWrg(1)(fun( bCols =>

              toGlobal(MapLcl(1)(MapLcl(0)(id))) o
                Join() o

                // Multiply all necessary combinations of tiles
                ReduceSeq(fun( (acc, pairOfTiles) =>

                  fun(pairOfTiles =>
                     fun(partial => MapLcl(1)(fun(pairOfRows => MapLcl(0)(add) $ Zip(Get(pairOfRows, 0), Get(pairOfRows, 1)))) $ Zip(acc, partial) ) o
                      Map(Join()) o
                      MapLcl(1)( fun(rowA =>
                        MapLcl(0)( fun( colB =>
                          toLocal(MapSeq(id) o ReduceSeq(fun((acc, y) => multAndSumUp.apply(acc, Get(y, 0), Get(y, 1))), 0.0f)) $ Zip(rowA, colB)
                        )) $ Get(pairOfTiles, 1)
                      )) $ Get(pairOfTiles, 0)
                  ) o

                    // Copy tiles to local memory
                    fun(pairOfTiles =>
                      Tuple(
                         toLocal(MapLcl(1)(MapLcl(0)(id))) $ Get(pairOfTiles, 0),
                         toLocal(MapLcl(1)(MapLcl(0)(id))) $ Get(pairOfTiles, 1)
                      )) $ pairOfTiles
                )
                  , toLocal(MapLcl(1)(MapLcl(0)(id))) $ Value(0.0f, ArrayType(ArrayType(Float, tileSize), tileSize))
                ) $ Zip(aRows, bCols)

              // Tile the matrices
            )) o Tile(tileSize) $ B
          )) o Tile(tileSize) $ A
      })

    val (output: Array[Float], runtime) = Execute(4, 4, mSize, nSize, (true, true))(f, matrixA, matrixB.transpose)

    println("output.size = " + output.length)
    println("output(0) = " + output(0))
    println("runtime = " + runtime)

    assertArrayEquals(gold, output, 0.0001f)
  }

  @Test def tiledMatrixMultiplyWithTranspose(): Unit = {
    val mSize = 16
    val kSize = 16
    val nSize = 16
    val matrixA = Array.tabulate(mSize, kSize)((r, c) => (((r * 3 + c * 2) % 10) + 1) * 1.0f)
    val matrixB = Array.tabulate(kSize, nSize)((r, c) => (((r * 7 + c * 3) % 10) + 1) * 1.0f)

    val tileSize = 4

    val gold = Utils.matrixMatrixMultiply(matrixA, matrixB).flatten

    val N = SizeVar("N")
    val M = SizeVar("M")
    val K = SizeVar("K")

    val f = fun(
      ArrayType(ArrayType(Float, K), M),
      ArrayType(ArrayType(Float, N), K),
      (A, B) => {
        // Undo the tiling
        Untile() o
          MapWrg(0)(fun( aRows =>
            MapWrg(1)(fun( bCols =>

              toGlobal(MapLcl(1)(MapLcl(0)(id))) o
                Join() o

                // Multiply all necessary combinations of tiles
                ReduceSeq(fun( (acc, pairOfTiles) =>

                  fun(pairOfTiles =>
                     fun(partial => MapLcl(1)(fun(pairOfRows => MapLcl(0)(add) $ Zip(Get(pairOfRows, 0), Get(pairOfRows, 1)))) $ Zip(acc, partial) ) o
                      Map(Join()) o
                      MapLcl(1)( fun(rowA =>
                        MapLcl(0)( fun( colB =>
                          toLocal(MapSeq(id) o ReduceSeq(fun((acc, y) => multAndSumUp.apply(acc, Get(y, 0), Get(y, 1))), 0.0f)) $ Zip(rowA, colB)
                        )) o Transpose() $ Get(pairOfTiles, 1)
                      )) $ Get(pairOfTiles, 0)
                  ) o

                    // Copy tiles to local memory
                    fun(pairOfTiles =>
                      Tuple(
                         toLocal(MapLcl(1)(MapLcl(0)(id))) $ Get(pairOfTiles, 0),
                         toLocal(MapLcl(1)(MapLcl(0)(id))) $ Get(pairOfTiles, 1)
                      )) $ pairOfTiles
                )
                  , toLocal(MapLcl(1)(MapLcl(0)(id))) $ Value(0.0f, ArrayType(ArrayType(Float, tileSize), tileSize))
                ) $ Zip(aRows, bCols)

            )) o Transpose() o Tile(tileSize) $ B
            // Tile the matrices
          )) o Tile(tileSize) $ A
      })

    val (output: Array[Float], runtime) = Execute(tileSize, tileSize, mSize, nSize,
      (true, true))(f, matrixA, matrixB)

    println("output.size = " + output.length)
    println("output(0) = " + output(0))
    println("runtime = " + runtime)

    assertArrayEquals(gold, output, 0.0001f)
  }

  @Test def tiledMatrixMultiplyWithTransposeAndPrivate(): Unit = {
    val mSize = 16
    val kSize = 16
    val nSize = 16
    val matrixA = Array.tabulate(mSize, kSize)((r, c) => (((r * 3 + c * 2) % 10) + 1) * 1.0f)
    val matrixB = Array.tabulate(kSize, nSize)((r, c) => (((r * 7 + c * 3) % 10) + 1) * 1.0f)

    val tileSize = 4

    val gold = Utils.matrixMatrixMultiply(matrixA, matrixB).flatten

    val f = MatrixMultiplication.tiled(tileSize)

    val (output: Array[Float], runtime) = Execute(4, 4, mSize, nSize, (true, false))(f, matrixA, matrixB)

    println("output.size = " + output.length)
    println("output(0) = " + output(0))
    println("runtime = " + runtime)

    assertArrayEquals(gold, output, 0.0001f)
  }

  @Test def addArrayOfMatrices(): Unit = {
    val mSize = 16
    val kSize = 16
    val numMatrices = 16
    val matrices = Array.fill(numMatrices, mSize, kSize)(util.Random.nextInt(5).toFloat)

    val gold = matrices.reduce((x, y) => (x, y).zipped.map((x, y) =>
                                                             (x, y).zipped.map(_+_))).flatten

    val test = matrices.transpose.flatMap(_.transpose.map(_.sum))

    assertArrayEquals(gold, test, 0.001f)

    val f = fun(
      ArrayType(ArrayType(ArrayType(Float, SizeVar("M")), SizeVar("K")), SizeVar("N")),
      input => {
        MapGlb(0)(MapGlb(1)(toGlobal(MapSeq(id)) o ReduceSeq(add, 0.0f)) o Transpose()) o
        Transpose() $ input
      }
    )

    val (output: Array[Float], _) = Execute(mSize*kSize)(f, matrices)

    assertArrayEquals(gold, output, 0.001f)
  }

  @Test def MATRIX_MATRIX_2D_GLOBAL_ID_WITH_TRANSPOSE() {

    val Msize = 512
    val Ksize = 512
    val Nsize = 512
    val matrixA = Array.tabulate(Msize, Ksize)((r, c) => (((r * 3 + c * 2) % 10) + 1) * 1.0f)
    val matrixB = Array.tabulate(Ksize, Nsize)((r, c) => (((r * 7 + c * 3) % 10) + 1) * 1.0f)

    val N = SizeVar("N")
    val M = SizeVar("M")
    val K = SizeVar("K")

    val f = fun(
      ArrayType(ArrayType(Float, K), M),
      ArrayType(ArrayType(Float, N), K),
      (A, B) => {
        MapGlb(0)(fun( Arow =>
          MapGlb(1)(fun( Bcol =>
            toGlobal(MapSeq(id)) o ReduceSeq(fun((acc, y) => multAndSumUp.apply(acc, Get(y, 0), Get(y, 1))), 0.0f) $ Zip(Arow, Bcol)
          )) o Transpose() $ B
        )) $ A
      })

    val (output: Array[Float], runtime) = Execute(Msize * Nsize)(f, matrixA, matrixB)

    println("output.size = " + output.length)
    println("output(0) = " + output(0))
    println("runtime = " + runtime)

    val gold = Utils.matrixMatrixMultiply(matrixA, matrixB).flatten

    assertArrayEquals(gold, output, 0.0f)
  }

  @Test def MATRIX_MATRIX_2D_TEST() {

    val mSize = 8
    val kSize = 8
    val nSize = 8
    val matrixA = Array.tabulate(mSize, kSize)((r, c) => (((r * 1 + c * 0) % 10) + 1) * 1.0f)
    //val matrixB = Array.tabulate(kSize, nSize)((r, c) => (((r * 2 + c * 3) % 10) + 1) * 1.0f)
    //val matrixA = Array.tabulate(mSize, kSize)((r, c) => 1.0f)
    val matrixB = Array.tabulate(kSize, nSize)((r, c) => 1.0f)

    val N = SizeVar("N")
    val M = SizeVar("M")
    val K = SizeVar("K")

    val r = 2 // number of rows a single workgroup computes
    val c = 4 // number of columns a single workgroup computes

    val f = fun(
      ArrayType(ArrayType(Float, K), M),
      ArrayType(ArrayType(Float, K), N), // this is already transposed
      (A, B) => {
        Join() o MapWrg(0)(fun( aRows =>
          TransposeW() o Join() o MapWrg(1)(fun( bCols =>
             MapLcl(0)(fun( bCol =>
               MapLcl(1)(fun( aRow =>
                toGlobal(MapSeq(id)) o ReduceSeq(fun((acc, y) => multAndSumUp.apply(acc, Get(y, 0), Get(y, 1))), 0.0f) $ Zip(aRow, bCol)
              )) $ aRows
            )) $ bCols
          )) o Split(c) $ B
        )) o Split(r) $ A
      })

    val (output: Array[Float], runtime) = Execute(8, mSize * nSize)(f, matrixA, matrixB.transpose)

    println("output.size = " + output.length)
    println("output(0) = " + output(0))
    println("runtime = " + runtime)

    val gold = Utils.matrixMatrixMultiply(matrixA, matrixB).flatten

    println("gold:")
    Utils.myPrint(gold, mSize)
    println("output:")
    Utils.myPrint(output, mSize)

    assertArrayEquals(gold,output,0.0f)

  }


}
