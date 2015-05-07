package cat.pseudocodi.week4.observablex

import rx.lang.scala.Observable

import scala.concurrent.{ExecutionContext, Future}

object ObservableEx {

  /** Returns an observable stream of values produced by the given future.
    * If the future fails, the observable will fail as well.
    *
    * @param f future whose values end up in the resulting observable
    * @return an observable completed after producing the value of the future, or with an exception
    */
  def apply[T](f: Future[T])(implicit execContext: ExecutionContext): Observable[T] = {
    Observable.from(f)
  }

}