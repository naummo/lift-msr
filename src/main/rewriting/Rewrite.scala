package rewriting

import lift.arithmetic.ArithExpr
import rewriting.utils.{NumberExpression, Utils}
import ir._
import ir.ast._
import rewriting.rules.Rule
import rewriting.macrorules.MacroRules

object Rewrite {

  def getExprForId(expr: Expr, id: Int, idMap: collection.Map[Expr, Int]): Expr =
    idMap.find(pair => pair._2 == id).get._1

  def applyRuleAtId(lambda: Lambda, id: Int, rule: Rule): Lambda = {
    val replacement = applyRuleAtId(lambda.body, id, rule)
    Lambda(lambda.params, replacement)
  }

  def applyRuleAt(lambda: Lambda, expr: Expr, rule: Rule): Lambda = {
    val replacement = applyRuleAt(lambda.body, rule, expr)
    Lambda(lambda.params, replacement)
  }

  def applyRuleAtId(expr: Expr, id: Int, rule: Rule): Expr = {
    val numbering = NumberExpression.breadthFirst(expr)
    applyRuleAtId(expr, id, rule, numbering)
  }

  def depthFirstApplyRuleAtId(expr:Expr, id: Int, rule: Rule): Expr = {
    val numbering = NumberExpression.depthFirst(expr)
    applyRuleAtId(expr, id, rule, numbering)
  }

  def applyRuleAtId(expr: Expr, id: Int, rule: Rule, numbering: collection.Map[Expr, Int]): Expr = {
    val toBeReplaced = getExprForId(expr, id, numbering)
    applyRuleAt(expr, rule, toBeReplaced)
  }

  def applyRuleAt(expr: Expr, rule: Rule, toBeReplaced: Expr): Expr = {
    TypeChecker.check(expr)
    UpdateContext(expr)

    val replacement = rule.rewrite(toBeReplaced)
    var replacedInExpr = Expr.replace(expr, toBeReplaced, replacement)

    if (rule == MacroRules.splitJoinId)
      replacedInExpr = patchUpAfterSplitJoin(toBeReplaced, replacement, replacedInExpr)

    replacedInExpr
  }

  def applyRuleUntilCannot(lambda: Lambda, rule: Rule): Lambda =
    applyRulesUntilCannot(lambda, Seq(rule))

  /**
    * Apply rules one by one until no rules apply anymore
    *
    * @param lambda The lambda where to apply rules
    * @param rules The rules to apply
    * @return
    */
  def applyRulesUntilCannot(lambda: Lambda, rules: Seq[Rule]): Lambda = {
    val newBody = applyRulesUntilCannot(lambda.body, rules)

    if (newBody eq lambda.body)
      lambda
    else
      Lambda(lambda.params, newBody)
  }

  /**
    * Apply rules one by one until no rules apply anymore
    *
    * @param expr The expression where to apply rules
    * @param rules The rules to apply
    * @return
    */
  @scala.annotation.tailrec
  def applyRulesUntilCannot(expr: Expr, rules: Seq[Rule]): Expr = {
    val allRulesAt = listAllPossibleRewritesForRules(expr, rules)

    if (allRulesAt.isEmpty) {
      expr
    } else {
      val ruleAt = allRulesAt.head
      applyRulesUntilCannot(Rewrite.applyRuleAt(expr, ruleAt._1, ruleAt._2), rules)
    }
  }

  def patchUpAfterSplitJoin(toBeReplaced: Expr, replacement: Expr, replaced: Expr): Expr = {
    try {
      TypeChecker(replaced)
      replaced
    } catch {
      case _: ZipTypeException =>

        val newExpr = Utils.getLengthOfSecondDim(replacement.t)
        val oldExpr = Utils.getLengthOfSecondDim(toBeReplaced.t)

        if (oldExpr != newExpr) {
          val st = collection.immutable.Map[ArithExpr, ArithExpr]((oldExpr, newExpr))
          val tunableNodes = Utils.findTunableNodes(replaced)
          Utils.quickAndDirtySubstitution(st, tunableNodes, replaced)
        } else {
          replaced
        }
    }
  }

  def listAllPossibleRewritesForRules(lambda: Lambda, rules: Seq[Rule]): Seq[(Rule, Expr)] = {
    listAllPossibleRewritesForRules(lambda.body, rules)
  }

  def listAllPossibleRewritesForRules(expr: Expr, rules: Seq[Rule]): Seq[(Rule, Expr)] = {
    UpdateContext(expr)
    TypeChecker(expr)
    rules.flatMap(rule => listAllPossibleRewrites(expr, rule))
  }

  def listAllPossibleRewrites(lambda: Lambda,rule: Rule): Seq[(Rule, Expr)] =
    listAllPossibleRewrites(lambda.body, rule)


  def listAllPossibleRewrites(expr: Expr, rule: Rule): Seq[(Rule, Expr)] = {
    Expr.visitWithState(Seq[(Rule, Expr)]())( expr, (e, s) => {
      if (rule.rewrite.isDefinedAt(e)) {
        s :+ (rule, e)
      } else s
    })
  }

  def rewrite(lambda: Lambda, rules: Seq[Rule], levels: Int): Seq[Lambda] = {

    val allRulesAt = listAllPossibleRewritesForRules(lambda, rules)
    val rewritten = allRulesAt.map(ruleAt => applyRuleAt(lambda, ruleAt._2, ruleAt._1))

    if (levels == 1)
      rewritten
    else
      rewritten.flatMap( l => rewrite(l, rules, levels-1))
  }
}


