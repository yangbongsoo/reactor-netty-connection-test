package targetserver;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServer;
import reactor.netty.tcp.TcpServer;

public class MischievousServer {
    private static final Logger log = LoggerFactory.getLogger(MischievousServer.class);

    public static void main(String[] args) {
        new MischievousServer().start("localhost", 8765);
    }

    public void start(String host, int port) {
        TcpServer tcpServer = TcpServer.create()
                .host(host)
                .port(port)
                .handle((nettyInbound, nettyOutbound) -> {
                    nettyOutbound.sendString(Mono.just("response content"))
                            .subscribe(new BaseSubscriber<Void>() {
                                @Override
                                protected void hookOnComplete() {
                                    log.info("response complete " + nettyOutbound);
                                    super.hookOnComplete();
                                }
                            });
                    return nettyOutbound.withConnection(c -> {
                        log.info("appending to " + nettyOutbound);
                        c.channel().close().channel().writeAndFlush("appending");
                    });
                });

        HttpServer.from(tcpServer)
                .bindUntilJavaShutdown(Duration.ofSeconds(5),
                        server -> log.info("Mischievous server started on {}:{}", server.host(), server.port()));
    }

}
