import cats.data.EitherT
import uk.gov.hmrc.helptosavestridefrontend.models.PayePersonalDetails
import uk.gov.hmrc.helptosavestridefrontend.models.eligibility.EligibilityCheckResult

import scala.concurrent.Future

def f(i: EligibilityCheckResult): PayePersonalDetails =

val e1: EitherT[Future,String,Int] = ???
def e2(i: Int): EitherT[Future,String,Option[Long]] = ???

val r: EitherT[Future,String,(Int,Option[Long])] = for {
  x1 <- e1
  x2 <- e2(x1)
} yield x1 -> x2