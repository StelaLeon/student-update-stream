package persistency

import com.github.mauricio.async.db.Configuration

/**
 * Created by sleon on 10/5/16.
 */

object ErrorState extends Enumeration{
  val ERROR, PARSED = Value
}

import io.getquill.{PostgresAsyncContext, SnakeCase}

trait DbContext {
  val postgresContext : PostgresAsyncContext[SnakeCase]
}

trait PersistenceModule {
  val suppliersDal : EntityDal
}


trait PersistenceModuleImpl extends PersistenceModule with DbContext{
  //this: Configuration  =>

  override lazy val postgresContext = new PostgresAsyncContext[SnakeCase]("quill")
  override val suppliersDal = new EntityDalImpl(postgresContext)

}