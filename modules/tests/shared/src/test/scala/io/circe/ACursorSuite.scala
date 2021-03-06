package io.circe

import io.circe.syntax._
import io.circe.tests.CirceSuite

class ACursorSuite extends CirceSuite {
  val j1: Json = Json.obj(
    "a" -> (1 to 5).toList.asJson,
    "b" -> Map("d" -> List(true, false, true)).asJson,
    "c" -> Map("e" -> 100.1, "f" -> 200.2).asJson
  )

  val j2: Json = Json.obj(
    "a" -> (0 to 5).toList.asJson,
    "b" -> Map("d" -> List(true, false, true)).asJson,
    "c" -> Map("e" -> 100.1, "f" -> 200.2).asJson
  )

  val j3: Json = Json.obj(
    "a" -> (1 to 5).toList.asJson,
    "b" -> 10.asJson,
    "c" -> Map("e" -> 100.1, "f" -> 200.2).asJson
  )

  val j4: Json = Json.obj(
    "a" -> (1 to 5).toList.asJson,
    "c" -> Map("e" -> 100.1, "f" -> 200.2).asJson
  )

  val cursor: ACursor = HCursor.fromJson(j1)

  "focus" should "return the JSON value in a newly created cursor" in forAll { (j: Json) =>
    assert(HCursor.fromJson(j).focus === Some(j))
  }

  "top" should "return from navigation into an object" in forAll { (j: Json) =>
    val c = HCursor.fromJson(j)

    val intoObject = for {
      fields  <- c.fields
      first   <- fields.headOption
      atFirst <- c.downField(first).success
    } yield atFirst

    assert(intoObject.forall(atFirst => atFirst.top === Some(j)))
  }

  it should "return from navigation into an array" in forAll { (j: Json) =>
    assert(HCursor.fromJson(j).downArray.success.forall(atFirst => atFirst.top === Some(j)))
  }

  "up" should "undo navigation into an object" in forAll { (j: Json) =>
    val c = HCursor.fromJson(j)

    val intoObject = for {
      fields  <- c.fields
      first   <- fields.headOption
      atFirst <- c.downField(first).success
    } yield atFirst

    assert(intoObject.forall(_.up.success.flatMap(_.focus) === Some(j)))
  }

  it should "undo navigation into an array" in forAll { (j: Json) =>
    assert(
      HCursor.fromJson(j).downArray.success.forall(atFirst =>
        atFirst.up.success.flatMap(_.focus) === Some(j)
      )
    )
  }

  "withFocus" should "have no effect when given the identity function" in forAll { (j: Json) =>
    assert(HCursor.fromJson(j).withFocus(identity).focus === Some(j))
  }

  it should "support adding an element to an array" in {
    val result = cursor.downField("a").success.map(
      _.withFocus(j =>
        j.asArray.fold(j)(a => Json.fromValues(0.asJson +: a))
      )
    )

    assert(result.flatMap(_.top) === Some(j2))
  }

  "withFocusM" should "lift a value into a List" in forAll { (j: Json) =>
    assert(HCursor.fromJson(j).withFocusM[List](List(_)).head.focus === Some(j))
  }

  "delete" should "remove a value from an object" in {
    val result = cursor.downField("b").success.flatMap(_.delete.success)

    assert(result.flatMap(_.top) === Some(j4))
  }

  it should "remove a value from an array" in forAll { (h: Json, t: List[Json]) =>
    val result = for {
      f <- HCursor.fromJson(Json.fromValues(h :: t)).downArray.success
      u <- f.delete.success
    } yield u

    assert(result.flatMap(_.focus) === Some(Json.fromValues(t)))
  }

  "set" should "replace an element" in {
    val result = cursor.downField("b").success.map(_.set(10.asJson))

    assert(result.flatMap(_.top) === Some(j3))
  }

  "lefts" should "return the expected values" in {
    val result = for {
      c <- cursor.downField("a").success
      a <- c.downN(3).success
      l <- a.lefts
    } yield l

    assert(result === Some(Vector(3.asJson, 2.asJson, 1.asJson)))
  }

  "rights" should "return the expected values" in {
    val result = for {
      c <- cursor.downField("a").success
      a <- c.downN(3).success
      l <- a.rights
    } yield l

    assert(result === Some(Vector(5.asJson)))
  }

  "fieldSet" should "return the expected values" in {
    assert(HCursor.fromJson(j1).fieldSet.map(_.toList.sorted) === Some(List("a", "b", "c")))
  }

  "fields" should "return the expected values" in {
    assert(HCursor.fromJson(j1).fields === Some(Vector("a", "b", "c")))
  }

  "left" should "successfully select an existing value" in {
    val result = for {
      c <- cursor.downField("a").success
      a <- c.downN(3).success
      l <- a.left.success
    } yield l

    assert(result.flatMap(_.focus) === Some(3.asJson))
  }

  it should "fail to select a value that doesn't exist" in {
    val result = for {
      c <- cursor.downField("b").success
      l <- c.left.success
    } yield l

    assert(result.flatMap(_.focus) === None)
  }

  "right" should "successfully select an existing value" in {
    val result = for {
      c <- cursor.downField("a").success
      a <- c.downN(3).success
      r <- a.right.success
    } yield r

    assert(result.flatMap(_.focus) === Some(5.asJson))
  }

  it should "fail to select a value that doesn't exist" in {
    val result = for {
      c <- cursor.downField("b").success
      r <- c.right.success
    } yield r

    assert(result.flatMap(_.focus) === None)
  }

  "first" should "successfully select an existing value" in {
    val result = for {
      c <- cursor.downField("a").success
      a <- c.downN(3).success
      f <- a.first.success
    } yield f

    assert(result.flatMap(_.focus) === Some(1.asJson))
  }

  it should "fail to select a value that doesn't exist" in {
    val result = for {
      c <- cursor.downField("b").success
      f <- c.first.success
    } yield f

    assert(result.flatMap(_.focus) === None)
  }

  "last" should "successfully select an existing value" in {
    val result = for {
      c <- cursor.downField("a").success
      a <- c.downN(3).success
      l <- a.last.success
    } yield l

    assert(result.flatMap(_.focus) === Some(5.asJson))
  }

  it should "fail to select a value that doesn't exist" in {
    val result = for {
      c <- cursor.downField("b").success
      l <- c.last.success
    } yield l

    assert(result.flatMap(_.focus) === None)
  }

  "leftAt" should "successfully select an existing value" in {
    val result = for {
      c <- cursor.downField("a").success
      a <- c.downN(3).success
      l <- a.leftAt(_.as[Int].right.exists(_ == 1)).success
    } yield l

    assert(result.flatMap(_.focus) === Some(1.asJson))
  }

  it should "fail to select a value that doesn't exist" in {
    val result = for {
      c <- cursor.downField("b").success
      l <- c.leftAt(_.as[Int].right.exists(_ == 1)).success
    } yield l

    assert(result.flatMap(_.focus) === None)
  }

  "rightAt" should "successfully select an existing value" in {
    val result = for {
      c <- cursor.downField("a").success
      a <- c.downN(3).success
      r <- a.rightAt(_.as[Int].right.exists(_ == 5)).success
    } yield r

    assert(result.flatMap(_.focus) === Some(5.asJson))
  }

  it should "fail to select a value that doesn't exist" in {
    val result = for {
      c <- cursor.downField("b").success
      r <- c.rightAt(_.as[Int].right.exists(_ == 5)).success
    } yield r

    assert(result.flatMap(_.focus) === None)
  }

  "field" should "successfully select an existing value" in {
    val result = for {
      c <- cursor.downField("c").success
      e <- c.downField("e").success
      f <- e.field("f").success
    } yield f

    assert(result.flatMap(_.focus) === Some(200.2.asJson))
  }

  "getOrElse" should "successfully decode an existing field" in {
    val result = for {
      b <- cursor.downField("b").success
    } yield b.getOrElse[List[Boolean]]("d")(Nil)
    assert(result === Some(Right(List(true, false, true))))
  }

  it should "use the fallback if field is missing" in {
    val result = for {
      b <- cursor.downField("b").success
    } yield b.getOrElse[List[Boolean]]("z")(Nil)
    assert(result === Some(Right(Nil)))
  }

  it should "fail if the field is the wrong type" in {
    val result = for {
      b <- cursor.downField("b").success
    } yield b.getOrElse[List[Int]]("d")(Nil)
    assert(result.fold(false)(_.isLeft))
  }

  "deleteGoLeft" should "remove the current value and move appropriately" in {
    val result = for {
      c <- cursor.downField("a").success
      a <- c.downN(3).success
      l <- a.deleteGoLeft.success
      u <- l.up.success
      lf <- l.focus
      uf <- u.focus
    } yield (lf, uf)

    assert(
      result.map(_._1) === Some(3.asJson) &&
      result.map(_._2) === Some(List(1, 2, 3, 5).asJson)
    )
  }

  "deleteGoRight" should "remove the current value and move appropriately" in {
    val result = for {
      c <- cursor.downField("a").success
      a <- c.downN(3).success
      l <- a.deleteGoRight.success
      u <- l.up.success
      lf <- l.focus
      uf <- u.focus
    } yield (lf, uf)

    assert(
      result.map(_._1) === Some(5.asJson) &&
      result.map(_._2) === Some(List(1, 2, 3, 5).asJson)
    )
  }

  "deleteGoFirst" should "remove the current value and move appropriately" in {
    val result = for {
      c <- cursor.downField("a").success
      a <- c.downN(3).success
      l <- a.deleteGoFirst.success
      u <- l.up.success
      lf <- l.focus
      uf <- u.focus
    } yield (lf, uf)

    assert(
      result.map(_._1) === Some(1.asJson) &&
      result.map(_._2) === Some(List(1, 2, 3, 5).asJson)
    )
  }

  "deleteGoLast" should "remove the current value and move appropriately" in {
    val result = for {
      c <- cursor.downField("a").success
      a <- c.downN(2).success
      l <- a.deleteGoLast.success
      u <- l.up.success
      lf <- l.focus
      uf <- u.focus
    } yield (lf, uf)

    assert(
      result.map(_._1) === Some(5.asJson) &&
      result.map(_._2) === Some(List(1, 2, 4, 5).asJson)
    )
  }

  "deleteLefts" should "remove the specified values" in {
    val result = for {
      c <- cursor.downField("a").success
      a <- c.downN(3).success
      l <- a.deleteLefts.success
      u <- l.up.success
      lf <- l.focus
      uf <- u.focus
    } yield (lf, uf)

    assert(
      result.map(_._1) === Some(4.asJson) &&
      result.map(_._2) === Some(List(4, 5).asJson)
    )
  }

  "deleteRights" should "remove the specified values" in {
    val result = for {
      c <- cursor.downField("a").success
      a <- c.downN(3).success
      l <- a.deleteRights.success
      u <- l.up.success
      lf <- l.focus
      uf <- u.focus
    } yield (lf, uf)

    assert(
      result.map(_._1) === Some(4.asJson) &&
      result.map(_._2) === Some(List(1, 2, 3, 4).asJson)
    )
  }

  "deleteGoField" should "remove the current value and move appropriately" in {
    val result = for {
      c <- cursor.downField("c").success
      a <- c.downField("e").success
      l <- a.deleteGoField("f").success
      u <- l.up.success
      lf <- l.focus
      uf <- u.focus
    } yield (lf, uf)

    assert(
      result.map(_._1) === Some(200.2.asJson) &&
      result.map(_._2) === Some(Map("f" -> 200.2).asJson)
    )
  }

  "setLefts" should "replace the specified values" in {
    val result = for {
      c <- cursor.downField("a").success
      a <- c.downN(3).success
      l <- a.setLefts(Vector(100.asJson, 101.asJson)).success
      u <- l.up.success
      lf <- l.focus
      uf <- u.focus
    } yield (lf, uf)

    assert(
      result.map(_._1) === Some(4.asJson) &&
      result.map(_._2) === Some(List(101, 100, 4, 5).asJson)
    )
  }

  "setRights" should "replace the specified values" in {
    val result = for {
      c <- cursor.downField("a").success
      a <- c.downN(3).success
      l <- a.setRights(Vector(100.asJson, 101.asJson)).success
      u <- l.up.success
      lf <- l.focus
      uf <- u.focus
    } yield (lf, uf)

    assert(
      result.map(_._1) === Some(4.asJson) &&
      result.map(_._2) === Some(List(1, 2, 3, 4, 100, 101).asJson)
    )
  }

  "replay" should "replay history" in {
    val cursor = HCursor.fromJson(j2)
    val result = cursor.downField("b").downField("d").downN(2)

    assert(
      result.focus === Some(Json.True) && cursor.replay(result.history) === result
    )
  }
}
