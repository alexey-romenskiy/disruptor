package codes.writeonce.utils;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebSocketConnectOptions;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class TestMain {

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        final var vertx = Vertx.vertx();
        final var httpClient = vertx.createHttpClient();
        final var future = new CompletableFuture<WebSocket>();
        httpClient.webSocket(
                new WebSocketConnectOptions()
                        .setHost("uat3.trade-mate.io")
                        .setPort(443)
                        .setSsl(true)
                        .setURI("/test"),
                new Handler<AsyncResult<WebSocket>>() {
                    @Override
                    public void handle(AsyncResult<WebSocket> event) {
                        if (event.succeeded()) {
                            final var webSocket = event.result();
                            webSocket
                                    .endHandler(new Handler<Void>() {
                                        @Override
                                        public void handle(Void event) {
                                            System.out.println("endHandler");
                                        }
                                    })
                                    .exceptionHandler(new Handler<Throwable>() {
                                        @Override
                                        public void handle(Throwable event) {
                                            System.out.println("exceptionHandler");
                                            event.printStackTrace();
                                        }
                                    })
                                    .binaryMessageHandler(new Handler<Buffer>() {
                                        @Override
                                        public void handle(Buffer event) {
                                            System.out.println("binaryMessageHandler");
                                        }
                                    })
                                    .textMessageHandler(new Handler<String>() {
                                        @Override
                                        public void handle(String event) {
                                            System.out.println("textMessageHandler: " + event);
                                        }
                                    })
                                    .pongHandler(new Handler<Buffer>() {
                                        @Override
                                        public void handle(Buffer event) {
                                            System.out.println("pong");
                                        }
                                    });
                            future.complete(webSocket);
                        } else {
                            System.out.println("Error");
                            event.cause().printStackTrace();
                        }
                    }
                });
        final var webSocket = future.get();
        for (int i = 0; i < 1; i++) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace(); // TODO:
            }
            webSocket.writeTextMessage("Hello");
//            webSocket.writePing(Buffer.buffer(new byte[]{-1}));
        }
        webSocket.close();
    }
}
