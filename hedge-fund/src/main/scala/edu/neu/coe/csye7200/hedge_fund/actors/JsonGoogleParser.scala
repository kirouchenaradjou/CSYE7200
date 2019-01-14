package edu.neu.coe.csye7200.hedge_fund.actors

import akka.actor.{ActorRef, Props}
import spray.http._

import scala.util._
import edu.neu.coe.csye7200.hedge_fund.model.{GoogleModel, Model}

/**
 * TODO create a super-type for this kind of actor
 *
 * @author robinhillyard
 */
class JsonGoogleParser(blackboard: ActorRef) extends BlackboardActor(blackboard) {

  val model: Model = new GoogleModel

  override def receive = {
    case ContentMessage(entity) =>
      log.debug("JsonGoogleParser received ContentMessage")
      JsonGoogleParser.decode(entity) match {
        case Right(results) => processQuote(results)
        case Left(message) => log.warning("Decoding error: " + message)
      }
    case m => super.receive(m)
  }

  def processQuote(quotes: Seq[Map[String, Option[String]]]) = quotes foreach { q => processInstrument(q) }

  def processInstrument(quote: Map[String, Option[String]]) = model.getKey("symbol") match {
    case Some(s) =>
      quote.get(s) match {
        case Some(Some(symbol)) => updateMarket(symbol, quote)
        case _ => log.warning(s"$s is undefined in quote")
      }
    case None => log.warning("'symbol' is not defined in model")
  }

  def updateMarket(symbol: String, quote: Map[String, Option[String]]) = blackboard ! KnowledgeUpdate(model, symbol, quote flatMap { case (k, Some(v)) => Option(k -> v); case _ => None })
}

object JsonGoogleParser {
  import spray.json.DefaultJsonProtocol
  import spray.httpx.unmarshalling._
  import spray.httpx.marshalling._
  import spray.httpx.SprayJsonSupport._
  import spray.json._

  type Results = Seq[Map[String, Option[String]]]

  object MyJsonProtocol extends DefaultJsonProtocol with NullOptions {
  }

  import MyJsonProtocol._

  /**
   * This version of decode is a little more complex than usual because the Google
   * interface deliberately prefixes "//" to the start of the Json in order
   * that we should not be able to invoke the service without some effort.
   * @param entity
   * @return
   */
  def decode(entity: HttpEntity): Deserialized[Results] = {
    val mediaTypeTextHtml = MediaTypes.`text/html`
    val mediaTypeJson = MediaTypes.`application/json`
    val contentTypeJson = ContentType(mediaTypeJson, HttpCharsets.`UTF-8`)
    //    val contentTypeText = ContentType(mediaTypeTextHtml, HttpCharsets.`ISO-8859-1`)
    entity match {
      case HttpEntity.NonEmpty(`contentTypeJson`, _) =>
        entity.as[Results]
      case HttpEntity.NonEmpty(ContentType(`mediaTypeTextHtml`, x), y) =>
        HttpEntity(ContentType(mediaTypeJson, x), fix(y)).as[Results]
      case HttpEntity.NonEmpty(x, _) => Left(MalformedContent(s"logic error: contentType=$x"))
      case _ => Left(MalformedContent("logic error"))
    }
  }

  def fix(data: HttpData): Array[Byte] = fix(data.asString).getBytes

  def fix(s: String): String = s.substring(3)

}