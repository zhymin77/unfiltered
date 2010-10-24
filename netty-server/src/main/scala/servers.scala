package unfiltered.netty

import unfiltered.util.RunnableServer
import java.util.concurrent.Executors
import org.jboss.netty.bootstrap.ServerBootstrap
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory
import java.net.InetSocketAddress
import org.jboss.netty.handler.codec.http.{HttpRequestDecoder, HttpResponseEncoder}
import org.jboss.netty.channel._
import group.{ChannelGroup, DefaultChannelGroup}

trait Server {
  val port: Int
  val host: String
  protected def pipelineFactory: ChannelPipelineFactory

  val DEFAULT_IO_THREADS = Runtime.getRuntime().availableProcessors() + 1;
  val DEFAULT_EVENT_THREADS = DEFAULT_IO_THREADS * 4;
  
  private var bootstrap: ServerBootstrap = _
  
  /** any channels added to this will receive broadcasted events */
  protected val channels = new DefaultChannelGroup("Netty Unfiltered Server Channel Group")
  
  def start(): this.type = {
    bootstrap = new ServerBootstrap(
      new NioServerSocketChannelFactory(
        Executors.newFixedThreadPool(DEFAULT_IO_THREADS),
        Executors.newFixedThreadPool(DEFAULT_EVENT_THREADS)))

    bootstrap.setPipelineFactory(pipelineFactory)

    bootstrap.setOption("child.tcpNoDelay", true)
    bootstrap.setOption("child.keepAlive", true)
    bootstrap.setOption("receiveBufferSize", 128 * 1024)
    bootstrap.setOption("sendBufferSize", 128 * 1024)
    bootstrap.setOption("reuseAddress", true)
    bootstrap.setOption("backlog", 16384)
    channels.add(bootstrap.bind(new InetSocketAddress(host, port)))
    this
  }

  def closeConnections(): this.type = {
    // Close any pending connections / channels (including server)
    channels.close.awaitUninterruptibly
    this
  }
  def destroy(): this.type = {
    // Release NIO resources to the OS
    bootstrap.releaseExternalResources
    this
  }
  def join(): this.type = {
    def doWait() {
      try { Thread.sleep(1000) } catch { case _: InterruptedException => () }
    }
    doWait()
    this
  }
}

case class Http(port: Int, host: String, lastHandler: ChannelHandler, 
                beforeStopBlock: () => Unit) extends Server with RunnableServer {
  def stop() = {
    beforeStopBlock()
    closeConnections()
    destroy()
  }
  def beforeStop(block: => Unit) =
    Http(port, host, lastHandler, { () => beforeStopBlock(); block })

  protected def pipelineFactory: ChannelPipelineFactory = 
    new ServerPipelineFactory(channels, lastHandler)
}

object Http {
  def apply(port: Int, host: String, lastHandler: ChannelHandler): Http = 
    Http(port, host, lastHandler, () => ())
  def apply(port: Int, lastHandler: ChannelHandler): Http = 
    Http(port, "0.0.0.0", lastHandler)
}

class ServerPipelineFactory(channels: ChannelGroup, lastHandler: ChannelHandler) extends ChannelPipelineFactory {
  def getPipeline(): ChannelPipeline = {
    val line = Channels.pipeline

    line.addLast("housekeeping", new HouseKeepingChannelHandler(channels))
    line.addLast("decoder", new HttpRequestDecoder)
    line.addLast("encoder", new HttpResponseEncoder)
    line.addLast("handler", lastHandler)

    line
  }
}

/**
 * Channel handler that keeps track of channels in a ChannelGroup for controlled
 * shutdown.
 */
class HouseKeepingChannelHandler(channels: ChannelGroup) extends SimpleChannelUpstreamHandler {
  override def channelOpen(ctx: ChannelHandlerContext, e: ChannelStateEvent) = {
    // Channels are automatically removed from the group on close
    channels.add(e.getChannel)
  }
}
