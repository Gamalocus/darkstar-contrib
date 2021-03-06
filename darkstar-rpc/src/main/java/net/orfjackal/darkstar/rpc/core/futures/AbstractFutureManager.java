/*
 * Copyright (c) 2008, Esko Luontola. All Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright notice,
 *       this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright notice,
 *       this list of conditions and the following disclaimer in the documentation
 *       and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package net.orfjackal.darkstar.rpc.core.futures;

import net.orfjackal.darkstar.rpc.core.protocol.Request;
import net.orfjackal.darkstar.rpc.core.protocol.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.Future;

/**
 * @author Esko Luontola
 * @since 11.8.2008
 */
public abstract class AbstractFutureManager implements FutureManager {
    private static final Logger logger = LoggerFactory.getLogger(AbstractFutureManager.class);

    public <V> Future<V> waitForResponseTo(Request request) {
        RpcFuture<V> f = newFuture(request);
        assert !requestMap().containsKey(request.requestId);
        requestMap().put(request.requestId, f);
        return returnFuture(f);
    }

    public void cancelWaitingForResponseTo(Request request) {
        requestMap().remove(request.requestId);
    }

    public void recievedResponse(Response response) {
        RpcFuture<?> f = requestMap().remove(response.requestId);
        if (f != null) {
            f.markDone(response);
        } else {
            logger.warn("Unexpected response: {}", response);
        }
    }

    public int waitingForResponse() {
        return requestMap().size();
    }

    protected abstract Map<Long, RpcFuture<?>> requestMap();

    protected abstract <V> RpcFuture<V> newFuture(Request request);

    protected abstract <V> Future<V> returnFuture(RpcFuture<V> future);
}
