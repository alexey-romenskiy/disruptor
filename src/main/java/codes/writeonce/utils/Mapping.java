package codes.writeonce.utils;

import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.cookie.Cookie;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

public interface Mapping {

    @Nullable
    Resource handle(
            @Nonnull HttpRequest request,
            @Nonnull QueryStringDecoder queryStringDecoder,
            @Nonnull Map<String, List<Cookie>> cookies
    ) throws NettyRequestException;

    @Nonnull
    ResponseFilter getResponseFilter();

    class Resource {

        @Nullable
        RequestHandlerFactory get;

        @Nullable
        RequestHandlerFactory delete;

        @Nullable
        RequestHandlerFactory post;

        @Nullable
        RequestHandlerFactory patch;

        @Nullable
        RequestHandlerFactory options;

        public void setGet(@Nonnull RequestHandlerFactory get) {
            if (this.get != null) {
                throw new IllegalArgumentException();
            }
            this.get = get;
        }

        public void setDelete(@Nonnull RequestHandlerFactory delete) {
            if (this.delete != null) {
                throw new IllegalArgumentException();
            }
            this.delete = delete;
        }

        public void setPost(@Nonnull RequestHandlerFactory post) {
            if (this.post != null) {
                throw new IllegalArgumentException();
            }
            this.post = post;
        }

        public void setPatch(@Nonnull RequestHandlerFactory patch) {
            if (this.patch != null) {
                throw new IllegalArgumentException();
            }
            this.patch = patch;
        }

        public void setOptions(@Nonnull RequestHandlerFactory options) {
            if (this.options != null) {
                throw new IllegalArgumentException();
            }
            this.options = options;
        }
    }
}
