package persistency

import java.sql.{DriverManager, Connection}

import com.typesafe.scalalogging.LazyLogging
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor

import scala.util.{Failure, Success, Try}
/**
 * Created by sleon on 10/4/16.
 */
class SchemaGeneration(url: String, user: String, password: String) extends LazyLogging {

  def run(): Unit = {
    val changeLogFile = "db/db.changelog-schema.xml"
    Try({
      val driver = "org.postgresql.Driver"
      Class.forName(driver).newInstance()
      DriverManager.getConnection(url,user,password)
    }) match {
      case Success(connection) =>
        logger.info(s"Liquibase is now running ...")
        createDBSchema(connection, changeLogFile)
      case Failure(ex) =>
        throw new RuntimeException("Could not connect to db", ex)
    }
  }


  private def createDBSchema(dbConnection: Connection, diffFilePath: String): Unit = {
    val liquibase = new Liquibase(diffFilePath,
      new ClassLoaderResourceAccessor(classOf[SchemaGeneration].getClassLoader),
      DatabaseFactory.getInstance.findCorrectDatabaseImplementation(new JdbcConnection(dbConnection)))
    try {
      liquibase.update("")
    } catch {
      case e: Throwable => throw e
    } finally {
      liquibase.forceReleaseLocks()
      dbConnection.rollback()
      dbConnection.close()
    }
  }

}
