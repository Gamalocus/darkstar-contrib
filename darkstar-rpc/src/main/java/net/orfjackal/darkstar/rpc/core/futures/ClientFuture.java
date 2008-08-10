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

import net.orfjackal.darkstar.rpc.core.Request;
import net.orfjackal.darkstar.rpc.core.Response;

import java.util.concurrent.FutureTask;

/**
 * @author Esko Luontola
 * @since 10.6.2008
 */
public final class ClientFuture<V> extends FutureTask<V> {

    private final Request request;
    private final ClientFutureManager manager;

    public ClientFuture(Request request, ClientFutureManager manager) {
        super(new NullRunnable(), null);
        this.request = request;
        this.manager = manager;
    }

    void markDone(Response response) {
        assert response.requestId == request.requestId;
        if (response.exception != null) {
            setException(response.exception);
        } else {
            set((V) response.value);
        }
    }

    public boolean cancel(boolean mayInterruptIfRunning) {
        manager.doNotWaitForResponse(request);
        return super.cancel(mayInterruptIfRunning);
    }

    private static class NullRunnable implements Runnable {
        public void run() {
            throw new UnsupportedOperationException();
        }
    }
}
