package codes.writeonce.utils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public interface RequestHandler {

    @Nullable
    BodyHandler getBodyHandler() throws NettyRequestException;

    void end(@Nonnull NettyRequestContext requestContext) throws NettyRequestException;

    void cleanup();
}
