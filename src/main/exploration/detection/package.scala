package exploration

import ir.{TypeChecker, UnallocatedMemory, UpdateContext}
import ir.ast.{Expr, FunCall, Lambda}
import ir.view.{NoView, View}
import lift.arithmetic.?
import opencl.generator.{NDRange, RangesAndCounts}
import opencl.ir.{InferOpenCLAddressSpace, OpenCLMemoryAllocator}
import opencl.ir.pattern.{MapGlb, MapLcl}

package object detection {

  def getNumDimensions(lambda: Lambda): Int = {

    val dims = Expr.visitWithState(Set[Int]())(lambda.body, {
      case (FunCall(MapLcl(dim, _), _), set) => set + dim
      case (FunCall(MapGlb(dim, _), _), set) => set + dim
      case (_, set) => set
    })

    dims.size
  }

  def prepareLambda(f: Lambda): Unit = {
    if (f.body.context == null || f.body.view == NoView || f.body.mem == UnallocatedMemory) {
      TypeChecker(f)
      InferOpenCLAddressSpace(f)
      RangesAndCounts(f, NDRange(?, ?, ?), NDRange(?, ?, ?), collection.Map())
      OpenCLMemoryAllocator(f)
      View(f)
      UpdateContext(f)
    }
  }
}
