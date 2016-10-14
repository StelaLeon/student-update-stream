package persistency

import scala.concurrent.{Awaitable, ExecutionContext, Future}
import io.getquill._

case class Student(id: String, `type` :String, state: String, resource_info: String)


trait EntityDal {
  def insert(supplierToInsert: Student)(implicit ec: ExecutionContext): Future[Long]
  def findById(supId: String)(implicit ec: ExecutionContext) : Future[Option[Student]]
  def findByType(typeName: String)(implicit ec: ExecutionContext) : Future[Option[Student]]
  def findByState(state: String)(implicit ec: ExecutionContext):Future[Option[Student]]
  def delete(supId: String)(implicit ec: ExecutionContext): Future[Long]
}

class EntityDalImpl(context: PostgresAsyncContext[SnakeCase]) extends EntityDal {

  import context._

  override def insert(supplierToInsert: Student)(implicit ec: ExecutionContext): Future[Long] = {
    println("inserting into db")
    context.run(query[Student].insert)(supplierToInsert :: Nil).map(_.head)
  }

  override def findById(supId: String)(implicit ec: ExecutionContext) : Future[Option[Student]] = {
    val q = quote {
      query[Student].filter(s => s.id == lift(supId))
    }
    context.run(q).map(_.headOption)
  }

  override def delete(supId: String)(implicit ec: ExecutionContext): Future[Long] = {
    val q = quote {
      query[Student].filter(s => s.id == lift(supId)).delete
    }
    context.run(q)
  }

  override def findByType(typeName: String)(implicit ec: ExecutionContext): Future[Option[Student]] = {
    val q = quote {
      query[Student].filter(s => s.`type` == lift(typeName))
    }
    context.run(q).map(_.headOption)
  }

  override def findByState(state: String)(implicit ec: ExecutionContext):Future[Option[Student]] = {
    val q = quote {
      query[Student].filter(s => s.state == lift(state))
    }
    context.run(q).map(_.headOption)
  }

}
