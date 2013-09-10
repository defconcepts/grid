package controllers

import scala.concurrent.Future

import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.Logger

import lib.imaging.ImageMetadata
import lib.elasticsearch.ElasticSearch
import lib.storage.{NullStorage, StorageBackend}


object MediaApi extends MediaApiController {
  val storage = NullStorage
}

abstract class MediaApiController extends Controller {

  def storage: StorageBackend

  def index = Action {
    Ok("This is the Media API.\r\n")
  }

  def getImage(id: String) = Action {
    Async {
      ElasticSearch.getImageById(id) map {
        case Some(source) => Ok(source)
        case None         => NotFound
      }
    }
  }

  def putImage(id: String) = Action(parse.temporaryFile) { request =>
    val tempFile = request.body

    val response = for {
      meta <- Future { ImageMetadata.iptc(tempFile.file) }
      uri  <- storage.store(tempFile.file)
      image = model.Image(id, uri, meta)
      _    <- ElasticSearch.indexImage(image)
    } yield NoContent

    response.onComplete(_ => tempFile.clean())
    Async(response)
  }

}