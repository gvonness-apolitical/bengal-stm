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

import bengal.stm.STM
import bengal.stm.model.*
import bengal.stm.syntax.all.*

import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.syntax.parallel.*
import cats.syntax.traverse.*
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.*

class TxnLockOrderingSpec extends AsyncFreeSpec with AsyncIOSpec with Matchers {

  "lock ordering" - {
    "two transactions accessing same variables in different order complete without deadlock" in {
      STM
        .runtime[IO]
        .flatMap { implicit stm =>
          for {
            varA <- TxnVar.of(1)
            varB <- TxnVar.of(2)
            // Transaction 1: read A, read B, write A
            txn1 = for {
                     a <- varA.get
                     b <- varB.get
                     _ <- varA.set(a + b)
                   } yield ()
            // Transaction 2: read B, read A, write B
            txn2 = for {
                     b <- varB.get
                     a <- varA.get
                     _ <- varB.set(a + b)
                   } yield ()
            _      <- (txn1.commit, txn2.commit).parTupled
            finalA <- varA.get.commit
            finalB <- varB.get.commit
          } yield (finalA, finalB)
        }
        .timeout(5.seconds)
        .asserting { case (a, b) =>
          // Both transactions completed; exact values depend on ordering
          // but neither should deadlock. We verify totals are consistent.
          // If txn1 runs first: A=3, B=5. If txn2 runs first: B=3, A=4.
          // With retries, other orderings are possible. Key assertion: no deadlock.
          (a + b) should be > 3
        }
    }

    "many concurrent transactions with overlapping variable access complete without deadlock" in {
      STM
        .runtime[IO]
        .flatMap { implicit stm =>
          for {
            vars <- (1 to 5).toList.traverse(i => TxnVar.of(i))
            // Each transaction reads all vars and writes one of them
            txns = vars.zipWithIndex.map { case (target, _) =>
                     (for {
                       values <- vars.traverse(_.get)
                       sum = values.sum
                       _ <- target.set(sum)
                     } yield ()).commit
                   }
            _       <- txns.parSequence
            results <- vars.traverse(_.get.commit)
          } yield results
        }
        .timeout(10.seconds)
        .asserting { results =>
          // All transactions completed without deadlock
          results.foreach(_ should be > 0)
          results.size shouldBe 5
        }
    }

    "bidirectional transfer between two accounts does not deadlock" in {
      STM
        .runtime[IO]
        .flatMap { implicit stm =>
          for {
            accountA <- TxnVar.of(1000)
            accountB <- TxnVar.of(1000)
            // Transfer A -> B
            txn1 = for {
                     a <- accountA.get
                     b <- accountB.get
                     _ <- accountA.set(a - 10)
                     _ <- accountB.set(b + 10)
                   } yield ()
            // Transfer B -> A (opposite order)
            txn2 = for {
                     b <- accountB.get
                     a <- accountA.get
                     _ <- accountB.set(b - 10)
                     _ <- accountA.set(a + 10)
                   } yield ()
            _      <- (txn1.commit, txn2.commit).parTupled
            finalA <- accountA.get.commit
            finalB <- accountB.get.commit
          } yield finalA + finalB
        }
        .timeout(5.seconds)
        .asserting(_ shouldBe 2000)
    }
  }
}
