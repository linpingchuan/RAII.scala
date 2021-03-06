package com.thoughtworks.raii

import java.io.StringWriter

import com.thoughtworks.future._
import com.thoughtworks.raii.asynchronous._

import scalaz.syntax.all._
import org.scalatest.{Assertion, AsyncFreeSpec, FreeSpec, Matchers}

import scala.concurrent.Promise
import com.thoughtworks.raii.scalatest.ThoughtWorksFutureToScalaFuture

import scalaz.Trampoline

/**
  * @author 杨博 (Yang Bo) &lt;pop.atry@gmail.com&gt;
  */
final class asynchronousSpec extends AsyncFreeSpec with Matchers with ThoughtWorksFutureToScalaFuture {

  "Given a scoped resource" - {
    var isSourceClosed = false
    val source = Do.autoCloseable(new AutoCloseable {
      isSourceClosed should be(false)
      override def close(): Unit = {
        isSourceClosed should be(false)
        isSourceClosed = true
      }
    })
    "And flatMap the resource to an new autoReleaseDependencies resource" - {
      var isResultClosed = false
      val result = source.intransitiveFlatMap { sourceCloseable =>
        Do.autoCloseable(new AutoCloseable {
          isResultClosed should be(false)
          override def close(): Unit = {
            isResultClosed should be(false)
            isResultClosed = true
          }
        })
      }
      "When map the new resource" - {
        "Then dependency resource should have been released" in {
          val p = Promise[Assertion]
          ThoughtworksFutureOps(result.map { r =>
            isSourceClosed should be(true)
            isResultClosed should be(false)
          }.run)
            .onComplete { either =>
              isSourceClosed should be(true)
              isResultClosed should be(true)
              val _ = p.complete(either)
            }
          p.future
        }
      }
    }
  }

  "Nested flatMaps should be stack-safe" in {
    def loop(acc: Int, i: Int = 0): Do[Int] = {
      if (i < 30000) {
        Do.now(i).flatMap { i =>
          loop(acc + i, i + 1)
        }
      } else {
        Do.now(acc)
      }
    }

    loop(0, 0).run.map { i =>
      i should be((1 until 30000).sum)
    }
  }

}
