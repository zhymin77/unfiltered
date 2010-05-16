package unfiltered.request

import javax.servlet.ServletRequest
import javax.servlet.http.HttpServletRequest

object HTTPS {
  def unapply(req: HttpServletRequest) = 
    if (req.getProtocol.equalsIgnoreCase("HTTPS")) Some(req)
    else None
}

class Method(method: String) {
  def unapply(req: HttpServletRequest) = 
    if (req.getMethod.equalsIgnoreCase(method)) Some(req)
    else None
}

class GET(request: HttpServletRequest)
object GET extends Method("GET")

class POST(request: HttpServletRequest)
object POST extends Method("POST")


class Path(path: String, req: HttpServletRequest)
object Path {
  def unapply(req: HttpServletRequest) = Some((req.getPathTranslated, req))
}
