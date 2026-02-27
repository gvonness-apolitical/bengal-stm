/*
 * Copyright 2023 Greg von Nessi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ai.entrolution
package runtime

import scala.concurrent.duration._

import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import org.scalatest.Tag
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers

import bengal.stm.STM
import bengal.stm.model._
import bengal.stm.syntax.all._

class TxnLogDirtySpec extends AsyncFreeSpec with AsyncIOSpec with Matchers {

  "dirty detection" - {
    "transaction retries and sees updated value when variable is modified externally" taggedAs Tag("Flaky") ignore {
      STM
        .runtime[IO]
        .flatMap { implicit stm =>
          for {
            tVar   <- TxnVar.of(0)
            signal <- TxnVar.of(false)
            // Reader waits for the signal to be set, then reads tVar
            readerFiber <- (for {
                             s <- signal.get
                             _ <- STM[IO].waitFor(s)
                             v <- tVar.get
                           } yield v).commit.start
            // Writer sets the var then signals
            _      <- tVar.set(42).commit
            _      <- signal.set(true).commit
            result <- readerFiber.joinWithNever
          } yield result
        }
        .timeout(30.seconds)
        .asserting(_ shouldBe 42)
    }

    "transaction that only reads commits successfully when no external modification occurs" in {
      STM
        .runtime[IO]
        .flatMap { implicit stm =>
          for {
            tVar   <- TxnVar.of(100)
            result <- tVar.get.commit
          } yield result
        }
        .asserting(_ shouldBe 100)
    }

    "write-only transaction always commits successfully" in {
      STM
        .runtime[IO]
        .flatMap { implicit stm =>
          for {
            tVar   <- TxnVar.of(0)
            _      <- tVar.set(999).commit
            result <- tVar.get.commit
          } yield result
        }
        .asserting(_ shouldBe 999)
    }

    "concurrent modification forces retry and transaction sees final value" taggedAs Tag("Flaky") ignore {
      STM
        .runtime[IO]
        .flatMap { implicit stm =>
          for {
            counter <- TxnVar.of(0)
            gate    <- TxnVar.of(false)
            // This transaction waits for the gate, then reads the counter
            readerFiber <- (for {
                             g <- gate.get
                             _ <- STM[IO].waitFor(g)
                             v <- counter.get
                           } yield v).commit.start
            // Writer increments counter multiple times, then opens the gate
            _      <- counter.set(1).commit
            _      <- counter.set(2).commit
            _      <- counter.set(3).commit
            _      <- gate.set(true).commit
            result <- readerFiber.joinWithNever
          } yield result
        }
        .timeout(30.seconds)
        .asserting(_ shouldBe 3)
    }
  }
}
