

object SshDsl {
  import fr.janalyse.ssh._

  case class EndPoint(host:String, port:Int)
  implicit class EndPointHelper(val sc:StringContext) extends AnyVal {
    def ep(args: Any*):EndPoint = {
      val strings = sc.parts.iterator
      val expressions = args.iterator
      var buf = new StringBuffer(strings.next)
      while(strings.hasNext) {
        buf append expressions.next
        buf append strings.next
      }
      val parts = buf.toString.split(":",2)
      EndPoint(parts.head, parts.drop(1).headOption.map(_.toInt).getOrElse(22))
    }
  }
  implicit class EndPointToAccessPath(from:EndPoint) {
    def <~>(to:EndPoint):AccessPath = AccessPath(from::to::Nil)
  }

  case class AccessPath(endpoints:List[EndPoint]) {
    def <~>(to:EndPoint):AccessPath = AccessPath(endpoints:+to)
  }

  class ServerContext(name:String) {
    var path = AccessPath(EndPoint("127.0.0.1",22)::Nil)
    def path_(newPath:AccessPath):Unit = {path=newPath}
    def pshell[T] (proc : SSHShell => T):T = {
      def intricate(endpoints:Iterable[EndPoint], lport:Option[Int]=None):T = {
        endpoints.headOption match {
          case Some(endpoint) if lport.isDefined => // intricate tunnel
            SSH.once("127.0.0.1", port=lport.get) { ssh =>
              val newPort = ssh.remote2Local(endpoint.host,endpoint.port)
              intricate(endpoints.tail, Some(newPort))
           }
          case Some(endpoint)  => // first tunnel
            SSH.once(endpoint.host, port=endpoint.port) { ssh =>
              val newPort = ssh.remote2Local("127.0.0.1",22)
              intricate(endpoints.tail, Some(newPort))
           }
          case None if lport.isDefined => SSH.shell("localhost",port=lport.get) { proc }
          case None =>
            throw new RuntimeException("Empty ssh path")
        }
      }
      intricate(path.endpoints)
    }
    def shell[T] (withSh : implicit SSHShell => T):T = {
      pshell { sh =>
        implicit val ish=sh
        withSh
      }
    }
  }
  var servers = Map.empty[String,ServerContext]

  def server(name:String)(specify: implicit ServerContext => Unit) = {
    implicit val context = new ServerContext(name)
    servers+=name->context
    specify
  }
  def access(path:AccessPath)(implicit context:ServerContext) = {
    context.path=path
  }
  def shell[T](serverName:String)(withSh : implicit SSHShell => T):T = {
    servers(serverName).pshell { sh =>
      implicit val ish = sh
      withSh
    }
  }

  def ls(implicit sh:SSHShell) = {sh.ls()}
  def cd(dir:String)(implicit sh:SSHShell) = {sh.cd(dir)}
  def pwd(implicit sh:SSHShell) = {sh.pwd()}
  def mkdir(name:String)(implicit sh:SSHShell) = {sh.mkdir(name)}
  def exec(cmd:String)(implicit sh:SSHShell) = {sh.execute(cmd)}
}



object test {
  def main(args: Array[String]): Unit = {
    import SshDsl._
    server("srv1") {
      access(ep"127.0.0.1:22" <~> ep"127.0.0.2:22" <~> ep"127.0.0.3:22")
      //service("web1") on 80
    }

    shell("srv1") {
      ls.take(10).map{println}
      println(exec("netstat -anp | grep LISTEN | grep 127.0.0"))
    }
/*
    http("srv1" / "web1") {
      get("/server-status?auto")
    }
*/
  }
}

package object __root__ {
  val x=1
}
