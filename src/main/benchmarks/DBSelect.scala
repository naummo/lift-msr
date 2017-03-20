package benchmarks

import ir._
import ir.ast._
import opencl.ir._
import opencl.ir.pattern._
import lift.arithmetic.SizeVar

class DBSelect(override val f: Seq[(String, Array[Lambda])])
      extends Benchmark2[(Int, Int, Int)]("SELECT", Seq(128), f, (x, y) => x == y) {
  
  override def runScala(inputs: Seq[Any]): Array[(Int, Int, Int)] = {
    val rowA = inputs(0).asInstanceOf[Array[Int]]
    val rowB = inputs(1).asInstanceOf[Array[Int]]
    val rowC = inputs(2).asInstanceOf[Array[Int]]
    
    def is_one(n: Int): Int = if (n == 1) 1 else 0
    
    (rowC.map(is_one), rowA, rowB).zipped.toArray
  }
  
  override def generateInputs(): Seq[Any] = {
    // Input: 3 columns (A, B, C) of integers
    val n = inputSizes().head
    
    val colA: Array[Int] = Array.tabulate(n)(i => (3*i + 4) % 10)
    val colB: Array[Int] = Array.tabulate(n)(i => (3*i + 2) % 10)
    val colC: Array[Int] = Array.tabulate(n)(i => (3*i) % 10)
   
    Seq(colA, colB, colC)
  }
  
  override protected def printParams(): Unit = {
    println("Emulating query: `SELECT A, B FROM table WHERE C = 1`")
    println("where `table` has 3 integer columns (A, B, C).")
    println("The first column of the result tells wether each row has been selected:")
    println("  1 means selected")
    println("  0 means the opposite")
  }
  
  override def buildInstanceSQLValues(variant: Int,
                                      name: String,
                                      lambdas: Array[Lambda],
                                      configuration: BenchmarkConfiguration,
                                      iStats: InstanceStatistic): String = {
    // TODO: handle all the whole content of `stats`
    val (stats, correctness) = iStats(variant)
    s"($variant, $name, ${stats(0)}, $correctness)"
  }
}

object DBSelect {
  val N = SizeVar("N")

  private val tuple_id = UserFun(
    "tuple_id", "t", "return t;",
    TupleType(Int, Int, Int), TupleType(Int, Int, Int)
  )

  private val is_one = UserFun(
    "is_one", "c", "if (c == 1) { return 1; } else { return 0; }",
    Int, Int
  )
  
  val naive: Lambda = fun(
    ArrayType(Int, N), ArrayType(Int, N), ArrayType(Int, N),
    (rowA, rowB, rowC) => {
      MapGlb(toGlobal(tuple_id)) $ Zip(
        MapSeq(is_one) $ rowC,  // The WHERE clause
        rowA, rowB              // The selected columns
      )
    }
  )
  
  def apply() = new DBSelect(Seq(("Naive", Array[Lambda](naive))))
  
  def main(args: Array[String]): Unit = {
    DBSelect().run(args)
  }
}